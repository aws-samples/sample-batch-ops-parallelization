package com.amazon.bopspar.service.resources.replication.policy;

import com.amazon.bopspar.model.AWSServiceException;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.replication.bucket.EncryptionType;
import com.amazon.bopspar.service.resources.replication.bucket.S3Bucket;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.policybuilder.iam.IamEffect;
import software.amazon.awssdk.policybuilder.iam.IamPolicy;
import software.amazon.awssdk.policybuilder.iam.IamPrincipal;
import software.amazon.awssdk.policybuilder.iam.IamStatement;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.UncheckedIOException;

import static com.amazon.bopspar.service.resources.replication.S3ReplicationUtils.constructBopsRoleArn;

/**
 * S3PolicyManager class is responsible for managing S3 bucket and KMS policies.
 */
public class S3PolicyManager {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(S3PolicyManager.class);
    private static final String CROSS_ACCOUNT_STATEMENT_SID = "S3ASourceCrossAccountAccess";

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
        String kmsKeyId = null;
        try {
            S3Bucket destBucket = S3Bucket.builder()
                .arn(workflowDetails.getDestBucketARN())
                .s3Client(destS3Client)
                .build();

            if (!destBucket.getEncryptionType().equals(EncryptionType.SSE_KMS)) {
                log.info("Destination Bucket {} is not KMS encrypted. Skipping KMS policy update.",
                    workflowDetails.getDestBucketARN());
                return;
            }

            kmsKeyId = destBucket.getKmsKeyId().get();

            GetKeyPolicyRequest getKeyPolicyRequest = GetKeyPolicyRequest.builder()
                .keyId(kmsKeyId)
                .build();

            // Get the key policy
            GetKeyPolicyResponse keyPolicyResponse = destKmsClient.getKeyPolicy(getKeyPolicyRequest);

            // Create new statement for cross account replication
            IamStatement crossAccountStatement = buildCrossAccountKMSStatement(workflowDetails);

            // Merge the existing policy with the cross account statements
            IamPolicy existingPolicy = IamPolicy.fromJson(keyPolicyResponse.policy());


            IamPolicy updatedPolicy = existingPolicy.copy(p -> p.addStatement(crossAccountStatement));

            PutKeyPolicyRequest putKeyPolicyRequest = PutKeyPolicyRequest.builder()
                .keyId(kmsKeyId)
                .policyName(keyPolicyResponse.policyName())
                .policy(updatedPolicy.toJson())
                .build();

            // Update the destination key policy
            destKmsClient.putKeyPolicy(putKeyPolicyRequest);
            log.info("Successfully updated KMS key policy for key: {}", kmsKeyId);

        } catch (KmsException e) {
            log.error("Failed to update KMS key policy for key: " + kmsKeyId);
            throw new AWSServiceException("Failed to update KMS key policy for key: "
                + kmsKeyId, e);
        } catch (AWSServiceException e) {
            log.error("Failed to get bucket encryption configuration for bucket: "
                + workflowDetails.getDestBucketARN());
            throw new AWSServiceException("Failed to get bucket encryption configuration for bucket: "
                + workflowDetails.getDestBucketARN(), e);
        } catch (UncheckedIOException e) {
            log.error("Invalid JSON for key policy: " + kmsKeyId);
            throw new AWSServiceException("Invalid JSON for key policy: "
                + kmsKeyId, e);
        }
    }

    /**
     * This method creates the cross account KMS statements for the given workflow details.
     *
     * @param workflowDetails is the workflow model
     * @return the IAM statement for cross account KMS access
     */
    private IamStatement buildCrossAccountKMSStatement(final WorkFlowModel workflowDetails) {

        String bopsRole = constructBopsRoleArn(workflowDetails.getSourceAccountNumber());

        return IamStatement.builder()
            .sid("AllowCrossAccountKMSAccess")
            .effect(IamEffect.ALLOW)
            .addPrincipal(IamPrincipal.create("AWS", bopsRole))
            .addAction("kms:Decrypt")
            .addAction("kms:Encrypt")
            .addAction("kms:DescribeKey")
            .addAction("kms:GenerateDataKey")
            .addResource("*")
            .build();
    }

    /**
     * This method creates the cross account policy request. This includes getting the existing policy and merging it
     * with the new statement. If there is not existing policy, we add the statement as a new policy
     *
     * @param destBucketName The destination bucket name
     * @param sourceAccountNumber The source account number
     * @param s3Client The S3Client for the destination bucket
     * @return the cross account policy request object
     */
    public PutBucketPolicyRequest buildCrossAccountPutBucketPolicyRequest(final String destBucketName,
                                                                          final String sourceAccountNumber,
                                                                          final S3Client s3Client) {

        String bopsRole = constructBopsRoleArn(sourceAccountNumber);

        IamStatement statement = IamStatement.builder()
            .sid(CROSS_ACCOUNT_STATEMENT_SID)
            .effect(IamEffect.ALLOW)
            .addPrincipal(IamPrincipal.create("AWS", bopsRole))
            .addAction("s3:GetBucketVersioning")
            .addAction("s3:PutBucketVersioning")
            .addAction("s3:List*")
            .addAction("s3:ReplicateObject")
            .addAction("s3:ReplicateDelete")
            .addAction("s3:ReplicateTags")
            .addAction("s3:GetObjectVersionTagging")
            .addAction("s3:ObjectOwnerOverrideToBucketOwner")
            .addAction("s3:GetObjectVersionForReplication")
            .addResource("arn:aws:s3:::" + destBucketName)
            .addResource("arn:aws:s3:::" + destBucketName + "/*")
            .build();

        String bucketPolicy;

        try {
            GetBucketPolicyRequest getBucketPolicyRequest = GetBucketPolicyRequest.builder()
                .bucket(destBucketName)
                .build();
            GetBucketPolicyResponse getBucketPolicyResponse = s3Client.getBucketPolicy(getBucketPolicyRequest);

            // Parse existing policy and merge with new statement
            IamPolicy existingPolicy = IamPolicy.fromJson(getBucketPolicyResponse.policy());
            IamPolicy updatedPolicy = existingPolicy.copy(p -> p.addStatement(statement));
            bucketPolicy = updatedPolicy.toJson();

        } catch (S3Exception s3Exception) {
            // If there is no existing policy, we add the statement as a new policy
            if (s3Exception.statusCode() != 404) {
                throw s3Exception;
            } else {
                IamPolicy newPolicy = IamPolicy.builder()
                    .addStatement(statement)
                    .build();
                bucketPolicy = newPolicy.toJson();
            }
        }

        return PutBucketPolicyRequest.builder()
            .bucket(destBucketName)
            .policy(bucketPolicy)
            .build();
    }

    /**
     *  This method adds the cross account bucket policy to the destination bucket.
     *
     * @param workflow The workflow details containing the source account number
     * @param s3Client The S3Client for the destination bucket
     * @param destBucketName The name of the destination bucket
     */
    public void addCrossAccountBucketPolicy(final WorkFlowModel workflow, final S3Client s3Client,
                                            final String destBucketName) {

        PutBucketPolicyRequest putBucketPolicyRequest =
            buildCrossAccountPutBucketPolicyRequest(destBucketName,
                workflow.getSourceAccountNumber(), s3Client);

        try {
            s3Client.putBucketPolicy(putBucketPolicyRequest);
            log.info("Bucket Policy added successfully");
        } catch (S3Exception exception) {
            log.error("Error adding bucket policy for destBucketName bucket: {} exception {} workflow id {}",
                destBucketName, exception, workflow.getWorkflowName());
            throw exception;
        }
    }
}
