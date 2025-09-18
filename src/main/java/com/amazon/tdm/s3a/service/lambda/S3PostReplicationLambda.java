package com.amazon.tdm.s3a.service.lambda;

import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.dagger.DaggerLambdaComponent;
import com.amazon.tdm.s3a.service.dagger.LambdaComponent;
import com.amazon.tdm.s3a.service.requests.OrcaRequest;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import com.amazon.tdm.s3a.service.resources.replication.S3PostReplicationService;
import com.amazon.tdm.s3a.service.responses.OrcaResponse;
import com.amazon.tdm.s3a.service.responses.OrcaResponseBuilder;
import com.amazon.tdm.s3a.service.validator.OrcaRequestValidator;
import com.amazon.tdm.s3a.service.validator.WorkflowValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.Instant;

import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Lambda to process post-replication steps after migration/CRR is complete.
 */
public class S3PostReplicationLambda implements RequestHandler<OrcaRequest, OrcaResponse> {
    private static final Logger LOGGER = LogManager.getLogger(S3PostReplicationLambda.class);
    private final WorkflowRepository workflowRepository;
    private final S3ClientFactory s3ClientFactory;
    private final S3PostReplicationService s3PostReplicationService;

    public S3PostReplicationLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
        this.s3PostReplicationService = lambdaComponent.getS3PostReplicationService();
    }

    // Unit testing
    S3PostReplicationLambda(final WorkflowRepository workflowRepository,
                            final S3ClientFactory s3ClientFactory,
                            final S3PostReplicationService s3PostReplicationService) {
        this.workflowRepository = workflowRepository;
        this.s3ClientFactory = s3ClientFactory;
        this.s3PostReplicationService = s3PostReplicationService;
    }

    @Override
    public OrcaResponse handleRequest(final OrcaRequest orcaRequest, final Context context) {
        OrcaRequestValidator.validateOrcaRequest(orcaRequest);
        final String workflowName = orcaRequest.getWorkflowName();
        final String namespaceID = orcaRequest.getNamespaceID();
        LOGGER.info("Orca Request: {} {}", workflowName, namespaceID);

        //Get workflow details
        final WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);
        WorkflowValidator.validateWorkflowArns(workflowDetails);
        final String sourceBucketName = Arn.fromString(workflowDetails.getSourceBucketARN()).resourceAsString();
        final String destBucketName = Arn.fromString(workflowDetails.getDestBucketARN()).resourceAsString();
        LOGGER.info("Re-enabling bucket lifecycle rules for source bucket {} and destination bucket {}",
                sourceBucketName,
                destBucketName);

        try (final S3Client sourceS3Client = s3ClientFactory.createS3Client(
            workflowDetails.getSourceRoleARN(),
            workflowDetails.getSourceRegion());

             final S3Client destS3Client = s3ClientFactory.createS3Client(
                 workflowDetails.getDestRoleARN(),
                 workflowDetails.getDestRegion());

             final KmsClient destKmsClient = s3ClientFactory.createKmsClient(
                 workflowDetails.getDestRoleARN(),
                 workflowDetails.getDestRegion());
        ) {
            // Re-enable lifecycle rules to original source configuration
            s3PostReplicationService.reEnableBucketLifecycleRules(sourceS3Client, sourceBucketName, workflowDetails);
            s3PostReplicationService.reEnableBucketLifecycleRules(destS3Client, destBucketName, workflowDetails);
            // Revert destination bucket's KMS key policy to its initial configuration state if cross account
            if (workflowDetails.getDestAccountNumber() != null && !workflowDetails.getDestAccountNumber()
                    .equals(workflowDetails.getSourceAccountNumber())) {
                s3PostReplicationService.resetKmsKeyPolicyIfBucketIsKMSEncrypted(destKmsClient,
                        workflowDetails.getDestBucketConfig());

                // Revert destination bucket's resource policy to its initial configuration state
                s3PostReplicationService.resetBucketPolicy(destS3Client, destBucketName, workflowDetails);

            }

            workflowDetails.setCompletedAt(Instant.now().toString());
            workflowRepository.updateWorkflow(workflowDetails);

        } catch (RuntimeException runtimeException) {
            LOGGER.error("Failed to restore original bucket configurations during PostReplication for"
                            + " workflow: {}, namespaceId: {}",
                    workflowName, namespaceID, runtimeException);

            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);

            // Send Failed Orca Response
            return OrcaResponseBuilder.buildRuntimeErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, runtimeException);
        }

        // Send Success Orca Response
        return OrcaResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
    }
}