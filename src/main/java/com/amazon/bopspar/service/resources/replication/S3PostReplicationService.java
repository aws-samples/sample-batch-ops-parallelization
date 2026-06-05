package com.amazon.bopspar.service.resources.replication;

import com.amazon.bopspar.model.AWSServiceException;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.utils.JsonMapperUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.amazon.bopspar.service.resources.replication.ReplicationConstants.BUCKET_CROSS_ACCOUNT_SID;
import static com.amazon.bopspar.service.resources.replication.ReplicationConstants.BUCKET_ENCRYPTION;
import static com.amazon.bopspar.service.resources.replication.ReplicationConstants.BUCKET_KMS_KEY_POLICY;
import static com.amazon.bopspar.service.resources.replication.ReplicationConstants.BUCKET_LIFECYCLE;
import static com.amazonaws.util.StringUtils.isNullOrEmpty;

/**
 * Service class for cleaning up S3A-created configurations for source and destination buckets.
 */

public class S3PostReplicationService {

    private static final Logger LOGGER = LogManager.getLogger(S3PostReplicationService.class);

    /**
     * Method to re-enable the bucket lifecycle rules if they exist.
     *
     * @param s3Client   S3Client
     * @param bucketName Bucket name
     * @param workflow   Workflow containing source bucket config
     */
    public void reEnableBucketLifecycleRules(final S3Client s3Client,
                                             final String bucketName,
                                             final WorkFlowModel workflow) {
        try {
            final String lifecycleConfigJson = workflow.getSourceBucketConfig().get(BUCKET_LIFECYCLE);
            if (isNullOrEmpty(lifecycleConfigJson)) {
                LOGGER.info("No lifecycle configuration found for bucket {}", bucketName);
                return;
            }
            GetBucketLifecycleConfigurationResponse lifecycleResponse = JsonMapperUtil.readValue(
                            lifecycleConfigJson,
                            GetBucketLifecycleConfigurationResponse.serializableBuilderClass())
                    .build();

            if (hasLifecycleRules(lifecycleResponse)) {
                LOGGER.info("Re-enabling bucket lifecycle rules for workflowName: {}, namespaceID: {}, and bucket: {}",
                        workflow.getWorkflowName(),
                        workflow.getNamespaceID(),
                        bucketName);
                s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                        .bucket(bucketName)
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(lifecycleResponse.rules())
                                .build())
                        .build());
                LOGGER.info("Successfully re-enabled bucket lifecycle rules for workflowName: {}, "
                                + "namespaceID: {}, and bucket: {}",
                        workflow.getWorkflowName(),
                        workflow.getNamespaceID(),
                        bucketName);
            }
        } catch (S3Exception s3Exception) {
            LOGGER.error("Error re-enabling bucket lifecycle rules for "
                            + "workflowName: {}, namespaceID: {}, and bucket: {}",
                    workflow.getWorkflowName(),
                    workflow.getNamespaceID(),
                    bucketName,
                    s3Exception);
            throw new AWSServiceException("Error re-enabling lifecycle rules", s3Exception);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing JSON", e);
        }
    }

    public void resetKmsKeyPolicyIfBucketIsKMSEncrypted(final KmsClient kmsClient,
                                                        final Map<String, String> bucketConfig) {
        try {
            final String bucketEncryption = bucketConfig.get(BUCKET_ENCRYPTION);
            final String bucketKmsKeyPolicy = bucketConfig.get(BUCKET_KMS_KEY_POLICY);

            if (bucketKmsKeyPolicy == null || bucketKmsKeyPolicy.trim().isEmpty()) {
                return;
            }

            JsonNode rootNode = JsonMapperUtil.readTree(bucketEncryption);
            JsonNode rulesNode = rootNode.get("serverSideEncryptionConfiguration")
                    .get("rules")
                    .get(0);

            JsonNode defaultEncryptionNode = rulesNode.get("applyServerSideEncryptionByDefault");
            String keyId = defaultEncryptionNode.get("kmsMasterKeyID").asText();

            JsonNode policyNode = JsonMapperUtil.readTree(bucketKmsKeyPolicy);
            String actualPolicy = policyNode.get("policy").asText();
            String policyName = policyNode.get("policyName").asText();
            PutKeyPolicyRequest putKeyPolicyRequest = PutKeyPolicyRequest.builder()
                    .policy(actualPolicy)
                    .keyId(keyId)
                    .policyName(policyName)
                    .build();

            kmsClient.putKeyPolicy(putKeyPolicyRequest);
            LOGGER.info("Successfully applied kms key policy to kms key: {}", keyId);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing encryption configuration: {}", e.getMessage());
            throw new AWSServiceException("Failed to parse encryption configuration", e);
        } catch (KmsException e) {
            LOGGER.error("Error applying kms key policy to kms key: {}", e.getMessage());
            throw new AWSServiceException("Failed to apply kms key policy", e);
        }
    }

    public void resetBucketPolicy(final S3Client s3Client, final String bucketName, final WorkFlowModel workflow) {

        try {
            GetBucketPolicyRequest getPolicyRequest = GetBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .build();

            GetBucketPolicyResponse policyResponse = s3Client.getBucketPolicy(getPolicyRequest);
            String currentPolicy = policyResponse.policy();


            JsonNode policyNode = JsonMapperUtil.readTree(currentPolicy);

            // Get statements
            List<JsonNode> statementsList = new ArrayList<>();
            policyNode.get("Statement").forEach(statementsList::add);

            statementsList.removeIf(statement ->
                    statement.has("Sid") && BUCKET_CROSS_ACCOUNT_SID.equals(statement.get("Sid").asText())
            );

            ((ObjectNode) policyNode).set("Statement", JsonMapperUtil.valueToTree(statementsList));

            // Update the bucket policy
            String updatedPolicy = JsonMapperUtil.writeValueAsString(policyNode);
            PutBucketPolicyRequest putBucketPolicyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(updatedPolicy)
                    .build();
            // If there are no statements left, delete the entire policy

            if (statementsList.size() == 0) {
                DeleteBucketPolicyRequest deletePolicyRequest = DeleteBucketPolicyRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.deleteBucketPolicy(deletePolicyRequest);
                LOGGER.info("Deleted entire bucket policy for bucket: {}", bucketName);
                return;
            }

            s3Client.putBucketPolicy(putBucketPolicyRequest);
            LOGGER.info("Successfully updated bucket policy for bucket: {}", bucketName);

        } catch (S3Exception e) {
            LOGGER.error("Error applying bucket resource policy to bucket: {} for bucket {}",
                    e.getMessage(), bucketName);
            throw new AWSServiceException("Failed to apply bucket policy to bucket", e);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error processing policy JSON: {} for bucket {}", e.getMessage(), bucketName);
            throw new RuntimeException("Failed to process policy JSON", e);
        }

    }

    private boolean hasLifecycleRules(final GetBucketLifecycleConfigurationResponse response) {
        return response != null && response.hasRules();
    }


}
