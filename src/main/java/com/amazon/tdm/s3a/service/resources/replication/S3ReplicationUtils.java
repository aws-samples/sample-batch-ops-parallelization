package com.amazon.tdm.s3a.service.resources.replication;

import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.s3.S3Client;

import static com.amazon.tdm.s3a.service.resources.replication.ReplicationConstants.BOPS_ROLE_NAME;

public class S3ReplicationUtils {
    private static final String REPLICATION_RULE_PREFIX = "S3A-";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private S3ReplicationUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a replication rule ID based on the destination bucket ARN and region.
     * @param destBucketArn The ARN of the destination bucket
     * @param destRegion The region of the destination bucket
     * @return The generated replication rule ID
     */
    public static String generateReplicationRuleId(final String destBucketArn,
                                                   final String destRegion) {
        if (destBucketArn == null || destBucketArn.isEmpty()) {
            throw new InvalidInputException("Destination bucket ARN cannot be null or empty");
        }
        if (destRegion == null || destRegion.isEmpty()) {
            throw new InvalidInputException("Destination region cannot be null or empty");
        }

        try {
            final String destBucketName = Arn.fromString(destBucketArn).resourceAsString();
            return REPLICATION_RULE_PREFIX + destBucketName + "-" + destRegion;
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid ARN format: " + destBucketArn, e);
        }
    }

    public static void addCrossAccountPolicyIfNeeded(
            final WorkFlowModel workflowDetails,
            final S3Client destS3Client,
            final S3ReplicationConfigurator replicationConfigurator) {

        final String sourceAccount = workflowDetails.getSourceAccountNumber();
        final String destAccount = workflowDetails.getDestAccountNumber();

        if (isCrossAccountReplication(sourceAccount, destAccount)) {
            String destBucketResource = Arn.fromString(workflowDetails.getSourceBucketARN())
                    .resourceAsString();
            replicationConfigurator.addCrossAccountBucketPolicy(
                    workflowDetails,
                    destS3Client,
                    destBucketResource
            );
        }
    }

    public static boolean isCrossAccountReplication(final String sourceAccount, final String destAccount) {
        return sourceAccount != null && !sourceAccount.equals(destAccount);
    }

    public static WorkFlowModel getUpdatedWorkflow(final WorkFlowModel workflowDetails) {
        if (workflowDetails == null) {
            throw new InvalidInputException("While setting up reverse replication, "
                    + "found that the source workflow is null");
        }

        return WorkFlowModel.builder()
                // Keep the same workflow identifiers
                .workflowName(workflowDetails.getWorkflowName())
                .namespaceID(workflowDetails.getNamespaceID())
                .status(workflowDetails.getStatus())
                .state(workflowDetails.getState())
                .workflowType(workflowDetails.getWorkflowType())

                // Swap source and destination ARNs
                .sourceBucketARN(workflowDetails.getDestBucketARN())
                .destBucketARN(workflowDetails.getSourceBucketARN())
                .sourceRoleARN(workflowDetails.getDestRoleARN())
                .destRoleARN(workflowDetails.getSourceRoleARN())

                // Swap account numbers
                .sourceAccountNumber(workflowDetails.getDestAccountNumber())
                .destAccountNumber(workflowDetails.getSourceAccountNumber())

                // Swap regions
                .sourceRegion(workflowDetails.getDestRegion())
                .destRegion(workflowDetails.getSourceRegion())

                // Swap bucket configurations
                .sourceBucketConfig(workflowDetails.getDestBucketConfig())
                .destBucketConfig(workflowDetails.getSourceBucketConfig())

                // Keep other configurations the same
                .workflowConfig(workflowDetails.getWorkflowConfig())
                .runtimeConfig(workflowDetails.getRuntimeConfig())
                .bopsJobID(workflowDetails.getBopsJobID())
                .ackedNotification(workflowDetails.getAckedNotification())
                .sentNotification(workflowDetails.getSentNotification())
                .jobReportBucketARN(workflowDetails.getJobReportBucketARN())
                .createdAt(workflowDetails.getCreatedAt())
                .startedAt(workflowDetails.getStartedAt())
                .bopsJobDuration(workflowDetails.getBopsJobDuration())
                .manifestLocation(workflowDetails.getManifestLocation())
                .bopsJobIds(workflowDetails.getBopsJobIds())
                .bopsJobDetails(workflowDetails.getBopsJobDetails())
                .monitoringDetails(workflowDetails.getMonitoringDetails())
                .build();
    }

    /**
     *  This method the Bops Role Arn given an account number.
     *
     * @param accountNumber The account number
     * @return The Bops role arn
     */
    public static String constructBopsRoleArn(final String accountNumber) {
        return String.format("arn:aws:iam::%s:role/%s", accountNumber, BOPS_ROLE_NAME);
    }
}

