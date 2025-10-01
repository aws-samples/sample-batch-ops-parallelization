package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.model.EntityNotFoundException;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.requests.OrcaRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.bopspar.service.resources.s3.configuration.S3ConfigurationService;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.responses.OrcaResponse;
import com.amazon.bopspar.service.responses.OrcaResponseBuilder;
import com.amazon.bopspar.service.validator.OrcaRequestValidator;
import com.amazon.bopspar.service.validator.WorkflowValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;

import java.util.Map;

public class S3ConfigureBucketLambda implements RequestHandler<OrcaRequest, OrcaResponse> {
    private static final Logger LOGGER = LogManager.getLogger(S3ConfigureBucketLambda.class);
    private final WorkflowRepository workflowRepository;
    private final S3ClientFactory  s3ClientFactory;
    private final S3ConfigurationService s3ConfigurationService;
    private final S3CreateBucketService s3CreateBucketService;
    private final WorkflowStatusManager workflowStatusManager;

    public S3ConfigureBucketLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
        this.s3ConfigurationService = lambdaComponent.getS3ConfigurationService();
        this.s3CreateBucketService = lambdaComponent.getS3CreateBucketService();
        this.workflowStatusManager = lambdaComponent.getWorkflowStatusManager();
    }

    S3ConfigureBucketLambda(final WorkflowRepository workflowRepository,
                            final S3ClientFactory s3ClientFactory,
                            final S3ConfigurationService s3ConfigurationService,
                            final S3CreateBucketService s3CreateBucketService,
                            final WorkflowStatusManager workflowStatusManager) {
        this.workflowRepository = workflowRepository;
        this.s3ClientFactory = s3ClientFactory;
        this.s3ConfigurationService = s3ConfigurationService;
        this.s3CreateBucketService = s3CreateBucketService;
        this.workflowStatusManager = workflowStatusManager;
    }

    @Override
    public OrcaResponse handleRequest(final OrcaRequest orcaRequest, final Context context) {
        OrcaRequestValidator.validateOrcaRequest(orcaRequest);
        final String workflowName = orcaRequest.getWorkflowName();
        final String namespaceID = orcaRequest.getNamespaceID();
        LOGGER.info("Orca Request: {} {}", workflowName, namespaceID);

        final WorkFlowModel workflowModel = workflowRepository.getWorkflow(workflowName, namespaceID);
        WorkflowValidator.validateWorkflowArns(workflowModel);

        if (WorkflowStatus.STOPPING.name().equals(workflowModel.getStatus())) {
            return workflowStatusManager.handleStoppingStatus(workflowModel);
        }

        try (
            final S3Client s3SourceClient = s3ClientFactory.createS3Client(
                workflowModel.getSourceRoleARN(),
                workflowModel.getSourceRegion());

            final S3Client s3DestClient = s3ClientFactory.createS3Client(
                workflowModel.getDestRoleARN(),
                workflowModel.getDestRegion());

            final KmsClient kmsSourceClient = s3ClientFactory.createKmsClient(
                workflowModel.getSourceRoleARN(),
                workflowModel.getSourceRegion());

            final KmsClient kmsDestClient = s3ClientFactory.createKmsClient(
                workflowModel.getDestRoleARN(),
                workflowModel.getDestRegion())
        ) {
            final String destBucketName = Arn.fromString(workflowModel.getDestBucketARN()).resourceAsString();
            final String sourceBucketName = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();

            if (!s3CreateBucketService.checkBucketExists(s3SourceClient, sourceBucketName)
                    || !s3CreateBucketService.checkBucketExists(s3DestClient, destBucketName)) {
                throw new EntityNotFoundException("Either destination or source bucket does not exist." + " "
                        + sourceBucketName + " " + destBucketName);
            } else {
                s3ConfigurationService.configureServerAccessLogging(s3SourceClient, s3DestClient, workflowModel,
                        s3CreateBucketService);
                s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName,
                        destBucketName);
                if (workflowModel.getRuntimeConfig() == null
                        || !workflowModel.getRuntimeConfig().isSkipBucketOwnershipValidationAndCopy()) {
                    LOGGER.info("Configuring Bucket Ownership Controls");
                    s3ConfigurationService.configureBucketOwnershipControls(s3SourceClient, s3DestClient,
                            workflowModel);
                }

                final Map<String, String> sourceBucketConfigString =
                        s3ConfigurationService.getBucketConfiguration(s3SourceClient,
                                kmsSourceClient, sourceBucketName);

                workflowModel.setSourceBucketConfig(sourceBucketConfigString);

                final Map<String, String> destBucketConfigString = s3ConfigurationService
                        .getBucketConfiguration(s3DestClient, kmsDestClient, destBucketName);

                workflowModel.setDestBucketConfig(destBucketConfigString);

                // Prereplication setup to disable lifecycle rules on source and destination bucket
                s3ConfigurationService.modifyBucketLifecycleRulesStatus(s3SourceClient,
                        sourceBucketName,
                        ExpirationStatus.DISABLED);
                s3ConfigurationService.modifyBucketLifecycleRulesStatus(s3DestClient,
                        destBucketName,
                        ExpirationStatus.DISABLED);

                workflowRepository.updateWorkflow(workflowModel);
                return OrcaResponseBuilder.buildSuccessResponse(workflowModel, WorkflowStatus.FINISHED);
            }
        } catch (AwsServiceException exception) {
            LOGGER.error("Exception {} while configuring bucket for workflowName: {}, namespaceID: {}",
                    exception.getClass().getName(),
                    workflowName,
                    namespaceID,
                    exception);
            workflowModel.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowModel);
            return OrcaResponseBuilder.buildServiceErrorResponse(workflowModel, WorkflowStatus.FAILED, exception);
        } catch (RuntimeException exception) {
            LOGGER.error("RuntimeException while configuring bucket for workflowName: {}, namespaceID: {}",
                    workflowName,
                    namespaceID,
                    exception);
            workflowModel.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowModel);
            return OrcaResponseBuilder.buildRuntimeErrorResponse(workflowModel, WorkflowStatus.FAILED, exception);
        }
    }
}