package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.bopspar.service.resources.replication.S3ReplicationUtils;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazon.bopspar.service.validator.WorkflowRequestValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;


/**
 * Lambda to set up Cross-region replication from region 2 -> region 1.
 * This can be triggered either as part of bidirectional replication setup or for rollback.
 */
public class S3RollbackReplicationSetupLambda implements RequestHandler<WorkflowRequest, WorkflowResponse> {
    private static final Logger LOGGER = LogManager.getLogger(S3RollbackReplicationSetupLambda.class);
    private static final String BIDIRECTIONAL_FLAG = "bidirectional";

    private final WorkflowRepository workflowRepository;
    private final S3ReplicationConfigurator replicationConfigurator;
    private final S3ClientFactory s3ClientFactory;
    private static final String REPLICATION_ROLE_NAME = "s3a-bops-permissions";

    public S3RollbackReplicationSetupLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.replicationConfigurator = lambdaComponent.getS3ReplicationConfigurator();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
    }

    // for unit testing
    S3RollbackReplicationSetupLambda(final WorkflowRepository workflowRepository,
                                     final S3ReplicationConfigurator setupReplication,
                                     final S3ClientFactory s3ClientFactory) {
        this.workflowRepository = workflowRepository;
        this.replicationConfigurator = setupReplication;
        this.s3ClientFactory = s3ClientFactory;
    }

    @Override
    public WorkflowResponse handleRequest(final WorkflowRequest workflowRequest, final Context context) {
        WorkflowRequestValidator.validateWorkflowRequest(workflowRequest);
        final String workflowName = workflowRequest.getWorkflowName();
        final String namespaceID = workflowRequest.getNamespaceID();

        // Get workflow details (By swapping source and destinations fields as this is setting up reverse replication)
        WorkFlowModel workflowDetails = S3ReplicationUtils
                    .getUpdatedWorkflow(workflowRepository.getWorkflow(workflowName, namespaceID));


        // Log whether this is part of bidirectional setup or rollback
        boolean isBidirectional = workflowDetails.getWorkflowConfig() != null
                && workflowDetails.getWorkflowConfig().containsKey(BIDIRECTIONAL_FLAG);
        LOGGER.info("Setting up replication from source region: {} to destination region: {} "
                        + "for workflow: {}, namespaceID: {}. Triggered by: {}",
                workflowName, namespaceID, isBidirectional ? "bidirectional setup" : "rollback request",
                workflowDetails.getSourceRegion(),workflowDetails.getDestRegion());

        final String bopsRole = String.format("arn:aws:iam::%s:role/%s", workflowDetails.getSourceAccountNumber(),
                REPLICATION_ROLE_NAME);

        try (final S3Client sourceS3Client = s3ClientFactory.createS3Client(
                workflowDetails.getSourceRoleARN(),
                workflowDetails.getSourceRegion());

             final S3Client destS3Client = s3ClientFactory.createS3Client(
                     workflowDetails.getDestRoleARN(),
                     workflowDetails.getDestRegion());

             final KmsClient destKmsClient = !workflowDetails
                     .getSourceAccountNumber().equals(workflowDetails.getDestAccountNumber())
                     ? s3ClientFactory.createKmsClient(workflowDetails.getDestRoleARN(),
                        workflowDetails.getDestRegion())
                     : null
        ) {
            // Setup cross-account policy if needed
            S3ReplicationUtils.addCrossAccountPolicyIfNeeded(workflowDetails, destS3Client, replicationConfigurator);

            // Setup CRR Rule
            replicationConfigurator.setupLiveReplication(
                    workflowDetails, sourceS3Client, destS3Client, bopsRole);

            // Handle KMS configuration if needed
            if (!workflowDetails.getSourceAccountNumber().equals(workflowDetails.getDestAccountNumber())) {
                replicationConfigurator.updateDestKeyPolicyIfBucketIsKmsEncrypted(
                        workflowDetails, destS3Client, destKmsClient);
            }

        } catch (AwsServiceException awsServiceException) {
            LOGGER.error("AWS Service exception setting up replication for workflow: {}, namespaceId: {}",
                    workflowName, namespaceID, awsServiceException);
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            return WorkflowResponseBuilder.buildServiceErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, awsServiceException);
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Runtime error setting up replication for workflow: {}, namespaceId: {}",
                    workflowName, namespaceID, runtimeException);
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            return WorkflowResponseBuilder.buildRuntimeErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, runtimeException);
        }

        return WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
    }
}
