package com.amazon.bopspar.service.resources.replication;

import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.replication.bops.S3BatchOperationsManager;
import com.amazon.bopspar.service.resources.replication.bucket.S3BucketVersioningManager;
import com.amazon.bopspar.service.resources.replication.policy.S3PolicyManager;
import com.amazon.bopspar.service.resources.replication.rule.S3ReplicationRuleManager;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningResponse;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.CreateJobResponse;
import software.amazon.awssdk.services.s3control.model.RequestedJobStatus;

import javax.inject.Inject;

/**
 * Service class for setting up replication on a source bucket.
 * This service class is responsible for configuring the replication configuration
 * for a given source bucket and executing the replication setup using the S3Client.
 */
public class S3ReplicationConfigurator {
    private final S3BatchOperationsManager s3BatchOperationsManager;
    private final S3BucketVersioningManager s3BucketVersioningManager;
    private final S3PolicyManager s3PolicyManager;
    private final S3ReplicationRuleManager s3ReplicationRuleManager;

    @Inject
    public S3ReplicationConfigurator(final S3BatchOperationsManager s3BatchOperationsManager,
                                     final S3BucketVersioningManager s3BucketVersioningManager,
                                     final S3PolicyManager s3PolicyManager,
                                     final S3ReplicationRuleManager s3ReplicationRuleManager) {
        this.s3BatchOperationsManager = s3BatchOperationsManager;
        this.s3BucketVersioningManager = s3BucketVersioningManager;
        this.s3PolicyManager = s3PolicyManager;
        this.s3ReplicationRuleManager = s3ReplicationRuleManager;
    }

    /**
     * This method retrieves the existing key policy, creates new statements for cross-account
     * permissions, and merges them with the existing policy. Then Updates the key policy of the
     * destination bucket key with the cross account permissions required for replication.
     *
     * @param workflowDetails The workflow details containing the destination bucket ARN
     * @param destS3Client The S3Client for the destination bucket
     * @param destKmsClient The KMSClient for the destination bucket
     */
    public void updateDestKeyPolicyIfBucketIsKmsEncrypted(final WorkFlowModel workflowDetails,
                                                          final S3Client destS3Client,
                                                          final KmsClient destKmsClient) {
        s3PolicyManager
            .updateDestKeyPolicyIfBucketIsKmsEncrypted(workflowDetails, destS3Client, destKmsClient);
    }

    /**
     *  Enable bucket versioning.
     *
     * @param workflow  WorkFlowModel containing the source and destination bucket information.
     * @param s3Client  S3Client for the bucket.
     * @return PutBucketVersioningResponse containing the response from the S3 service.
     */
    public PutBucketVersioningResponse enableBucketVersioning(final WorkFlowModel workflow, final S3Client s3Client) {
        return s3BucketVersioningManager
            .enableBucketVersioning(workflow, s3Client);
    }

    /**
     * Add cross account policy to the destination bucket.
     *
     * @param workflow WorkFlowModel containing the source and destination bucket information.
     * @param s3Client S3Client for the destination bucket.
     * @param destBucketName Name of the destination bucket.
     */
    public void addCrossAccountBucketPolicy(final WorkFlowModel workflow, final S3Client s3Client,
                                            final String destBucketName) {
        s3PolicyManager
            .addCrossAccountBucketPolicy(workflow, s3Client, destBucketName);
    }

    /**
     *  Setup replication rule on the source bucket.
     *
     * @param workflow WorkFlowModel containing the source and destination bucket information.
     * @param sourceS3Client S3Client for the source bucket.
     * @param destS3Client S3Client for the destination bucket.
     * @param bopsRole BOPS role ARN.
     */
    public void setupLiveReplication(final WorkFlowModel workflow, final S3Client sourceS3Client,
                                     final S3Client destS3Client, final String bopsRole) {
        s3ReplicationRuleManager
            .setupLiveReplication(workflow, sourceS3Client, destS3Client, bopsRole);
    }

    /**
     * Setup BOPS job for the given workflow.
     *
     * @param workflow WorkFlowModel containing the source and destination bucket information.
     * @param s3ControlClient S3ControlClient for the S3 service.
     * @param workflowRepository WorkflowRepository for the S3 service.
     * @param bopsRole BOPS role ARN.
     * @return CreateJobResponse containing the response from the S3 service.
     */
    public CreateJobResponse setupBOPSJob(final WorkFlowModel workflow, final S3ControlClient s3ControlClient,
                                          final WorkflowRepository workflowRepository, final String bopsRole) {
        return s3BatchOperationsManager
            .setupBOPSJob(workflow, s3ControlClient, workflowRepository, bopsRole);
    }

    /**
     * Build cross account put bucket policy request.
     *
     * @param destBucketName Name of the destination bucket.
     * @param sourceAccountNumber Source account number.
     * @param s3Client S3Client for the S3 service.
     * @return PutBucketPolicyRequest containing the request for the S3 service.
     */
    public PutBucketPolicyRequest buildCrossAccountPutBucketPolicyRequest(final String destBucketName,
                                                                          final String sourceAccountNumber,
                                                                          final S3Client s3Client) {
        return s3PolicyManager
            .buildCrossAccountPutBucketPolicyRequest(destBucketName, sourceAccountNumber, s3Client);
    }

    /**
     * Remove replication rule from the source bucket.
     *
     * @param s3Client S3Client for the S3 service.
     * @param workflowModel WorkFlowModel containing the source and destination bucket information.
     */
    public void removeReplicationRule(final S3Client s3Client, final WorkFlowModel workflowModel) {
        s3ReplicationRuleManager
            .removeReplicationRule(s3Client, workflowModel);
    }

    /**
     * Batch update job status.
     *
     * @param s3ControlClient S3ControlClient for the S3 service.
     * @param workFlowModel WorkFlowModel containing the source and destination bucket information.
     * @param requestedJobStatus RequestedJobStatus containing the status of the job.
     */
    public void batchUpdateJobStatus(final S3ControlClient s3ControlClient,
                                     final WorkFlowModel workFlowModel,
                                     final RequestedJobStatus requestedJobStatus) {
        s3BatchOperationsManager
            .batchUpdateJobStatus(s3ControlClient, workFlowModel, requestedJobStatus);
    }
}
