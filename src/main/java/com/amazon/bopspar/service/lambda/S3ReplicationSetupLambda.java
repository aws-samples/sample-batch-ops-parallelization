
package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazon.bopspar.service.validator.WorkflowRequestValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3control.S3ControlClient;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

/**
 * Lambda to set up S3 Batch operations and Cross-region replication.
 */
public class S3ReplicationSetupLambda implements RequestHandler<WorkflowRequest, WorkflowResponse> {
    private static final Logger LOGGER = LogManager.getLogger(S3ReplicationSetupLambda.class);
    private final WorkflowRepository workflowRepository;
    private final S3ReplicationConfigurator replicationConfigurator;
    private final S3ClientFactory  s3ClientFactory;
    private final WorkflowStatusManager workflowStatusManager;
    private static final String BOPS_ROLE_NAME = "s3a-bops-permissions";

    public S3ReplicationSetupLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.replicationConfigurator = lambdaComponent.getS3ReplicationConfigurator();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
        this.workflowStatusManager = lambdaComponent.getWorkflowStatusManager();
    }

    //for unit testing
    S3ReplicationSetupLambda(final WorkflowRepository workflowRepository,
                             final S3ReplicationConfigurator setupReplication,
                             final S3ClientFactory s3ClientFactory,
                             final WorkflowStatusManager workflowStatusManager) {
        this.workflowRepository = workflowRepository;
        this.replicationConfigurator = setupReplication;
        this.s3ClientFactory = s3ClientFactory;
        this.workflowStatusManager = workflowStatusManager;
    }

    @Override
    public WorkflowResponse handleRequest(final WorkflowRequest workflowRequest, final Context context) {
        WorkflowRequestValidator.validateWorkflowRequest(workflowRequest);
        final String workflowName = workflowRequest.getWorkflowName();
        final String namespaceID = workflowRequest.getNamespaceID();
        LOGGER.info("Workflow Request: {} {}", workflowName, namespaceID);

        //Get workflow details
        WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);

        if (WorkflowStatus.STOPPING.name().equals(workflowDetails.getStatus())) {
            return workflowStatusManager.handleStoppingStatus(workflowDetails);
        }

        final String bopsRole = String.format("arn:aws:iam::%s:role/%s", workflowDetails.getSourceAccountNumber(),
                BOPS_ROLE_NAME);

        try (
            // Build S3 Client using wf details retrieved for Source Region Bucket.
            final S3Client sourceS3Client = s3ClientFactory.createS3Client(
                workflowDetails.getSourceRoleARN(),
                workflowDetails.getSourceRegion());

            // Build S3 Client using wf details retrieved for Destination Region Bucket.
            final S3Client destS3Client = s3ClientFactory.createS3Client(
                workflowDetails.getDestRoleARN(),
                workflowDetails.getDestRegion());

            // Build S3ControlClient using wf details retrieved for Source Region Bucket.
            final S3ControlClient s3ControlClient = s3ClientFactory.createS3ControlClient(
                workflowDetails.getSourceRoleARN(),
                workflowDetails.getSourceRegion());

            // Build KmsClient if needed
            final KmsClient destKmsClient = !workflowDetails
                .getSourceAccountNumber().equals(workflowDetails.getDestAccountNumber())
                ? s3ClientFactory.createKmsClient(workflowDetails.getDestRoleARN(), workflowDetails.getDestRegion())
                : null
            ) {

            //Start Replication for WorkflowRequest
            //1. Ensure Versioning on the Source Bucket Enabled
            replicationConfigurator.enableBucketVersioning(workflowDetails, sourceS3Client);
            //2. If source Account and Dest account are different - set up for cross account migration
            addCrossAccountPolicyIfNeeded(workflowDetails, destS3Client);
            //3. Setup CRR Rule on the Source Bucket
            replicationConfigurator.setupLiveReplication(workflowDetails, sourceS3Client, destS3Client, bopsRole);
            //4. If source Account and Dest account are different, update the KMS Key policy
            // when buckets are using KMS encryption
            if (!workflowDetails.getSourceAccountNumber().equals(workflowDetails.getDestAccountNumber())) {
                replicationConfigurator.updateDestKeyPolicyIfBucketIsKmsEncrypted(workflowDetails,
                        destS3Client, destKmsClient);
            }
            //5. Create, configure and start Batch Operation (BOPS) and update DDB with record
            replicationConfigurator.setupBOPSJob(workflowDetails, s3ControlClient, workflowRepository, bopsRole);

            workflowRepository.updateWorkflow(workflowDetails);

        } catch (AwsServiceException awsServiceException) {
            LOGGER.error("Exception {} setting up replication for workflow: {}, namespaceId: {}",
                    awsServiceException.getClass().getName(), workflowName, namespaceID, awsServiceException);
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            // Send Failed Workflow Response
            return WorkflowResponseBuilder.buildServiceErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, awsServiceException);
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Error setting up replication for workflow: {}, namespaceId: {}",
                    workflowName, namespaceID, runtimeException);
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            // Send Failed Workflow Response
            return WorkflowResponseBuilder.buildRuntimeErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, runtimeException);
        }

        // Send Success Workflow Response
        return WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
    }

    private void addCrossAccountPolicyIfNeeded(final WorkFlowModel workflowDetails, final S3Client destS3Client) {
        String sourceAccount = workflowDetails.getSourceAccountNumber();
        String destAccount = workflowDetails.getDestAccountNumber();

        if (isCrossAccountReplication(sourceAccount, destAccount)) {
            String destBucketResource = Arn.fromString(workflowDetails.getDestBucketARN())
                    .resourceAsString();
            replicationConfigurator.addCrossAccountBucketPolicy(
                    workflowDetails,
                    destS3Client,
                    destBucketResource
            );
        }
    }

    private boolean isCrossAccountReplication(final String sourceAccount, final String destAccount) {
        return sourceAccount != null && !sourceAccount.equals(destAccount);
    }
}
