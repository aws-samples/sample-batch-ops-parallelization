package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.validator.WorkflowRequestValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

public class S3CreateBucketLambda implements RequestHandler<WorkflowRequest, WorkflowResponse> {
    private static final Logger LOGGER = LogManager.getLogger(S3CreateBucketLambda.class);
    private final WorkflowRepository workflowRepository;
    private final S3CreateBucketService createBucketService;
    private final S3ClientFactory  s3ClientFactory;
    private final WorkflowStatusManager workflowStatusManager;

    public S3CreateBucketLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.createBucketService = lambdaComponent.getS3CreateBucketService();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
        this.workflowStatusManager = lambdaComponent.getWorkflowStatusManager();
    }

    //for unit testing
    S3CreateBucketLambda(final WorkflowRepository workflowRepository,
                         final S3CreateBucketService createBucket,
                         final S3ClientFactory s3ClientFactory,
                         final WorkflowStatusManager workflowStatusManager) {
        this.workflowRepository = workflowRepository;
        this.createBucketService =  createBucket;
        this.s3ClientFactory = s3ClientFactory;
        this.workflowStatusManager = workflowStatusManager;
    }

    /**
     * Handles creation of buckets
     *  1. checks if Job Report and Destination bucket exist
     *  2. Creates the buckets if non-existant
     *  3. Return a proper response (success/failure)
     * @param workflowRequest Contains workflow name and namespaceID
     * @return WorkflowResponse success/failure message
     */
    @Override
    public WorkflowResponse handleRequest(final WorkflowRequest workflowRequest, final Context context) {
        WorkflowRequestValidator.validateWorkflowRequest(workflowRequest);
        final String workflowName = workflowRequest.getWorkflowName();
        final String namespaceID = workflowRequest.getNamespaceID();
        LOGGER.info("Workflow Request: {} {}", workflowName, namespaceID);

        WorkFlowModel workflowModel = workflowRepository.getWorkflow(workflowName, namespaceID);

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
            ) {

            final String jobReportBucketName = createBucketService.generateBucketName("manifest");
            final String destBucketName = (isNullOrEmpty(workflowModel.getDestBucketARN()))
                    ? createBucketService.generateBucketName("")
                    : Arn.fromString(workflowModel.getDestBucketARN()).resourceAsString();

            boolean jobReportBucketExists = createBucketService.checkBucketExists(s3SourceClient, jobReportBucketName);
            boolean destBucketExists = createBucketService.checkBucketExists(s3DestClient, destBucketName);

            if (!jobReportBucketExists) {
                createBucketService.createS3Bucket(s3SourceClient, workflowModel.getSourceRoleARN(),
                        workflowModel.getSourceRegion(), jobReportBucketName);
                workflowModel.setJobReportBucketARN("arn:aws:s3:::" + jobReportBucketName);
                LOGGER.info("Job Report bucket {} created.", jobReportBucketName);
            } else {
                workflowModel.setJobReportBucketARN("arn:aws:s3:::" + jobReportBucketName);
                LOGGER.info("Job Report bucket {} already exists.", jobReportBucketName);
            }

            if (!destBucketExists) {
                createBucketService.createS3Bucket(s3DestClient, workflowModel.getDestRoleARN(),
                        workflowModel.getDestRegion(),destBucketName);
                workflowModel.setDestBucketARN("arn:aws:s3:::" + destBucketName);

                LOGGER.info("Destination bucket {} created.", destBucketName);
            } else {
                workflowModel.setDestBucketARN("arn:aws:s3:::" + destBucketName);
                LOGGER.info("Destination bucket {} already exists.", destBucketName);
            }

            //Update Worfklow in DB
            workflowRepository.updateWorkflow(workflowModel);

            //After successful creation of Dest bucket enable versioning on both source and dest buckets
            createBucketService.enableBucketVersioning(workflowModel, s3SourceClient, s3DestClient);

            return WorkflowResponseBuilder.buildSuccessResponse(workflowModel, WorkflowStatus.CREATED);

        } catch (AwsServiceException exception) {
            LOGGER.error("Exception {} while creating bucket for workflowName: {}, namespaceID: {}",
                    exception.getClass().getName(),
                    workflowName,
                    namespaceID,
                    exception);
            workflowModel.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowModel);
            return WorkflowResponseBuilder.buildServiceErrorResponse(workflowModel, WorkflowStatus.FAILED, exception);
        } catch (RuntimeException exception) {
            LOGGER.error("Exception while creating bucket for workflowName: {}, namespaceID: {}",
                    workflowName,
                    namespaceID,
                    exception);
            workflowModel.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowModel);
            return WorkflowResponseBuilder.buildRuntimeErrorResponse(workflowModel, WorkflowStatus.FAILED, exception);
        }
    }
}
