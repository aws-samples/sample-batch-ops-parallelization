package com.amazon.tdm.s3a.service.resources.replication.rule;


import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.resources.replication.S3ReplicationUtils;
import com.amazon.tdm.s3a.service.resources.replication.bucket.EncryptionType;
import com.amazon.tdm.s3a.service.resources.replication.bucket.S3Bucket;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AccessControlTranslation;
import software.amazon.awssdk.services.s3.model.DeleteBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.DeleteMarkerReplication;
import software.amazon.awssdk.services.s3.model.DeleteMarkerReplicationStatus;
import software.amazon.awssdk.services.s3.model.Destination;
import software.amazon.awssdk.services.s3.model.EncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationResponse;
import software.amazon.awssdk.services.s3.model.Metrics;
import software.amazon.awssdk.services.s3.model.MetricsStatus;
import software.amazon.awssdk.services.s3.model.PutBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.ReplicationConfiguration;
import software.amazon.awssdk.services.s3.model.ReplicationRule;
import software.amazon.awssdk.services.s3.model.ReplicationRuleFilter;
import software.amazon.awssdk.services.s3.model.ReplicationRuleStatus;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.SourceSelectionCriteria;
import software.amazon.awssdk.services.s3.model.SseKmsEncryptedObjects;
import software.amazon.awssdk.services.s3.model.SseKmsEncryptedObjectsStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * This class manages the replication rules for S3 buckets.
 */
public class S3ReplicationRuleManager {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(S3ReplicationRuleManager.class);
    private static int replicationRulePriority = 0;

    /**
     * This method creates a replication rule for the given replication configuration.
     *
     * @param replicationConfig The replication configuration
     * @param bopsRole The BOPS role name
     * @return The PutBucketReplicationRequest
     */
    private PutBucketReplicationRequest buildReplicationRequest(final ReplicationConfig replicationConfig,
                                                                final String bopsRole) {

        final S3Bucket sourceBucket = replicationConfig.getSourceBucket();
        final S3Bucket destBucket = replicationConfig.getDestBucket();
        final String sourceAccountNumber = replicationConfig.getWorkflow().getSourceAccountNumber();
        final String destAccountNumber = replicationConfig.getWorkflow().getDestAccountNumber();
        final String destRegion = replicationConfig.getWorkflow().getDestRegion();
        final String replicationRuleId = S3ReplicationUtils.generateReplicationRuleId(destBucket.getArn(), destRegion);

        //2025-03-18: Removing .storageClass(StorageClass.STANDARD) to preserve source bucket configuration
        Destination.Builder destinationBuilder = Destination.builder()
            .bucket(replicationConfig
                .getWorkflow()
                .getDestBucketARN())
            .metrics(Metrics.builder() // Enabling replication metrics
                .status(MetricsStatus.ENABLED)
                .build());

        if (!sourceAccountNumber.equals(destAccountNumber)) {
            destinationBuilder
                .account(destAccountNumber)  // Specify the destination account ID
                .accessControlTranslation(AccessControlTranslation.builder()
                    .owner("Destination")
                    .build());
        }


        boolean canReplicateKMSEncryptedObjects = canReplicateKMSEncryptedObjects(sourceBucket, destBucket);

        if (canReplicateKMSEncryptedObjects) {
            // Create encryption configuration for destination
            EncryptionConfiguration encryptionConfig = EncryptionConfiguration
                .builder()
                .replicaKmsKeyID(destBucket
                    .getKmsKeyId()
                    .get())
                .build();
            destinationBuilder.encryptionConfiguration(encryptionConfig);
        }

        // Build the destination
        Destination destination = destinationBuilder.build();

        // Create the replication rule
        ReplicationRule.Builder replicationRuleBuilder = ReplicationRule.builder()
            .id(replicationRuleId)
            .status(ReplicationRuleStatus.ENABLED)
            .priority(replicationRulePriority)
            .filter(ReplicationRuleFilter.builder().prefix("").build())
            .destination(destination)
            .deleteMarkerReplication(DeleteMarkerReplication.builder()
                .status(DeleteMarkerReplicationStatus.ENABLED)
                .build());

        // Add KMS source selection criteria only if replicateObjectsEncryptedWithKMS
        if (canReplicateKMSEncryptedObjects) {
            replicationRuleBuilder.sourceSelectionCriteria(SourceSelectionCriteria
                .builder()
                .sseKmsEncryptedObjects(SseKmsEncryptedObjects.builder()
                    .status(SseKmsEncryptedObjectsStatus.ENABLED)
                    .build())
                .build());
        }

        // Setting ReplicationTimeControl on the CRR
        if (isReplicationTimeControlEnabled(replicationConfig)) {
            replicationRuleBuilder.destination(destination.toBuilder()
                .replicationTime(software.amazon.awssdk.services.s3.model.ReplicationTime.builder()
                    .status(software.amazon.awssdk.services.s3.model.ReplicationTimeStatus.ENABLED)
                    .time(software.amazon.awssdk.services.s3.model.ReplicationTimeValue.builder()
                        .minutes(15)
                        .build())
                    .build())
                .metrics(Metrics.builder()
                    .status(MetricsStatus.ENABLED)
                    .eventThreshold(software.amazon.awssdk.services.s3.model.ReplicationTimeValue.builder()
                        .minutes(15)
                        .build())
                    .build())
                .build());
        }

        // Add the new rule to the existing rules
        replicationConfig
            .getExistingRules()
            .add(replicationRuleBuilder.build());


        // Build the replication configuration
        ReplicationConfiguration putBucketReplicationConfig = ReplicationConfiguration.builder()
            .role(bopsRole)
            .rules(replicationConfig
                .getExistingRules())
            .build();

        String sourceBucketName = sourceBucket.getName();

        return PutBucketReplicationRequest.builder()
            .bucket(sourceBucketName)
            .replicationConfiguration(putBucketReplicationConfig)
            .build();
    }

    private boolean isReplicationTimeControlEnabled(ReplicationConfig replicationConfig) {
        return replicationConfig.getWorkflow()
            .getRuntimeConfig() != null && replicationConfig.getWorkflow()
            .getRuntimeConfig()
            .isReplicationTimeControlEnabled();
    }

    /**
     *  This method checks if the source and destination buckets support KMS encryption.
     * @param sourceBucket The source bucket
     * @param destBucket The destination bucket
     * @return true if both buckets support KMS encryption, false otherwise
     */
    private boolean canReplicateKMSEncryptedObjects(final S3Bucket sourceBucket, final S3Bucket destBucket) {
        return sourceBucket.getEncryptionType()
            .equals(EncryptionType.SSE_KMS)
            && destBucket.getEncryptionType()
            .equals(EncryptionType.SSE_KMS);
    }

    /**
     * This method checks if the S3A replication rule is present in the existing rules.
     *
     * @param s3AReplicationRuleId The S3A replication rule id
     * @param existingRules The existing rules
     * @return True if the S3A replication rule is present, false otherwise
     */
    private boolean isS3AReplicationRulePresent( final String s3AReplicationRuleId,
                                                 final List<ReplicationRule> existingRules) {
        return existingRules.stream()
            .anyMatch(rule -> rule.id().equals(s3AReplicationRuleId));
    }

    /**
     * This method gets the existing replication rules.
     * @param workflow The workflow model
     * @param s3Client The S3 client
     * @return The list of existing replication rules
     */
    private List<ReplicationRule> getExistingReplicationRules(final WorkFlowModel workflow, final S3Client s3Client) {
        List<ReplicationRule> existingRules = new ArrayList<>();
        try {
            // Get existing replication configuration
            GetBucketReplicationResponse replicationResponse = s3Client.getBucketReplication(
                GetBucketReplicationRequest.builder().bucket(Arn.fromString(workflow.getSourceBucketARN())
                        .resourceAsString())
                    .build()
            );

            // Determine the next priority in case of multiple rules
            if (replicationResponse.replicationConfiguration() != null
                && replicationResponse.replicationConfiguration().hasRules()) {
                existingRules.addAll(replicationResponse.replicationConfiguration().rules());
                replicationRulePriority = existingRules.stream()
                    .map(ReplicationRule::priority)
                    .max(Integer::compare)
                    .orElse(0) + 1;
            }
        }
        catch (S3Exception s3Exception) {
            // If no rules exist, we get an exception rather than an empty array response.
            // We need to handle this case here
            if ("ReplicationConfigurationNotFoundError".equals(s3Exception.awsErrorDetails().errorCode())) {
                log.info("No existing replication configuration found for bucket: {}",
                    Arn.fromString(workflow.getSourceBucketARN()).resourceAsString());
                replicationRulePriority = 1; // Default priority for the first rule
            } else {
                log.error("S3Exception while checking for existing replication rules for workflowName: {}, "
                        + "namespaceId: {}",
                    workflow.getWorkflowName(), workflow.getNamespaceID(), s3Exception);
                throw new RuntimeException(s3Exception.awsErrorDetails().errorMessage());
            }
        }

        return existingRules;
    }

    /**
     * This method sets up the live replication.
     *
     * @param workflow The workflow model
     * @param sourceS3Client The source S3 client
     * @param destS3Client The destination S3 client
     * @param bopsRole The BOPS role name
     */
    public void setupLiveReplication(final WorkFlowModel workflow, final S3Client sourceS3Client,
                                     final S3Client destS3Client, final String bopsRole) {

        // Check for existing live replication rules. If they exist, we need to append our rule.
        // Not performing this check will result in our rule overwriting other rules.
        List<ReplicationRule> existingRules = getExistingReplicationRules(workflow, sourceS3Client);

        // Checks if there is an S3A replication rule in the existingRules.
        // If it exists we return.
        final String replicationRuleId = S3ReplicationUtils
            .generateReplicationRuleId(workflow.getDestBucketARN(),workflow.getDestRegion());
        if (isS3AReplicationRulePresent(replicationRuleId, existingRules)) {
            log.info("Existing S3A replication rule found for workflowName: {}, namespaceID: {}."
                    + " Skipping replication setup.",
                workflow.getWorkflowName(), workflow.getNamespaceID());
            return;
        }

        // Build S3A source bucket
        S3Bucket sourceBucket = S3Bucket.builder()
            .arn(workflow.getSourceBucketARN())
            .s3Client(sourceS3Client)
            .build();

        // Build S3A destination bucket
        S3Bucket destBucket = S3Bucket.builder()
            .arn(workflow.getDestBucketARN())
            .s3Client(destS3Client)
            .build();

        // Build the replication configuration
        ReplicationConfig config = ReplicationConfig.builder()
            .workflow(workflow)
            .existingRules(existingRules)
            .sourceBucket(sourceBucket)
            .destBucket(destBucket)
            .build();

        var replicationRequest = buildReplicationRequest(config, bopsRole);

        // Execute the replication setup using the S3Client
        try {
            sourceS3Client.putBucketReplication(replicationRequest);
        } catch (S3Exception s3Exception) {
            log.error("S3Exception while setting up live replication for workflowName: {}, namespaceId: {}",
                workflow.getWorkflowName(), workflow.getNamespaceID(), s3Exception);
            throw new RuntimeException(s3Exception.awsErrorDetails().errorMessage());
        }

        log.info("Live Replication setup successful for workflowName: {}, namespaceID: {}",
            workflow.getWorkflowName(),
            workflow.getNamespaceID());
    }

    /**
     * This method removes the replication rule for the given workflow.
     *
     * @param s3Client The S3 client
     * @param workflowModel The workflow model
     */
    public void removeReplicationRule(final S3Client s3Client, final WorkFlowModel workflowModel) {
        final String replicationRuleId = S3ReplicationUtils.generateReplicationRuleId(
            workflowModel.getDestBucketARN(), workflowModel.getDestRegion());

        log.info("Attempting to remove replication rule: {}", replicationRuleId);

        try {
            final String sourceBucketName = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();
            final GetBucketReplicationRequest getBucketReplicationRequest = GetBucketReplicationRequest.builder()
                .bucket(sourceBucketName)
                .build();

            try {
                final GetBucketReplicationResponse getBucketReplicationResponse =
                    s3Client.getBucketReplication(getBucketReplicationRequest);

                final ReplicationConfiguration replicationConfiguration =
                    getBucketReplicationResponse.replicationConfiguration();

                // Check if the replication rule exists
                final boolean ruleExists = replicationConfiguration.rules().stream()
                    .anyMatch(rule -> rule.id().equals(replicationRuleId));

                if (!ruleExists) {
                    log.info("Replication rule not found for workflowName: {}, namespaceId: {}. No action taken.",
                        workflowModel.getWorkflowName(),
                        workflowModel.getNamespaceID());
                    return;
                }


                final List<ReplicationRule> updatedRules = new ArrayList<>(replicationConfiguration.rules());
                updatedRules.removeIf(rule -> rule.id().equals(replicationRuleId));

                if (updatedRules.isEmpty()) {
                    final DeleteBucketReplicationRequest deleteBucketReplicationRequest =
                        DeleteBucketReplicationRequest.builder()
                            .bucket(sourceBucketName)
                            .build();
                    s3Client.deleteBucketReplication(deleteBucketReplicationRequest);
                } else {
                    // Create a new ReplicationConfiguration with the updated rules
                    final ReplicationConfiguration updatedReplicationConfiguration = ReplicationConfiguration.builder()
                        .role(replicationConfiguration.role())
                        .rules(updatedRules)
                        .build();

                    final PutBucketReplicationRequest putBucketReplicationRequest =
                        PutBucketReplicationRequest.builder()
                            .bucket(sourceBucketName)
                            .replicationConfiguration(updatedReplicationConfiguration)
                            .build();
                    s3Client.putBucketReplication(putBucketReplicationRequest);
                }
                log.info("Successfully removed replication rule for workflowName: {}, namespaceId: {}",
                    workflowModel.getWorkflowName(),
                    workflowModel.getNamespaceID());
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404) {
                    log.warn("No replication configuration found for bucket {}. "
                            + "WorkflowName: {}, namespaceId: {}. No action needed.",
                        sourceBucketName,
                        workflowModel.getWorkflowName(),
                        workflowModel.getNamespaceID());
                    return;
                }
                throw exception;
            }
        } catch (Exception exception) {
            log.error("Error deleting replication rule for workflowName: {}, namespaceId: {}",
                workflowModel.getWorkflowName(),
                workflowModel.getNamespaceID(),
                exception);
            throw new RuntimeException("Error deleting replication rule", exception);
        }
    }
}
