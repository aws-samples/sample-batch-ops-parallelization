package com.amazon.bopspar.service.resources.s3.bucket;

import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazonaws.services.s3.internal.BucketNameUtils;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

import javax.inject.Inject;
import java.util.Locale;
import java.util.UUID;

@Log4j2
public class S3CreateBucketService {
    private final S3CreateBucketImpl createBucketImpl;

    // Constants for S3 bucket name constraints
    private static final int S3_BUCKET_NAME_MAX_LENGTH = 63;
    private static final int UUID_LENGTH = 10;
    private static final String S3_BUCKET_NAME_VALID_CHARACTERS = "^[a-z0-9.-]+$";


    @Inject
    public S3CreateBucketService(final S3CreateBucketImpl createBucketImpl) {
        this.createBucketImpl = createBucketImpl;

    }

    // Generate bucket name with prefix if provided
    public String generateBucketName(final String prefix) {
        // Validate and format prefix if provided
        String effectivePrefix = null;
        if (prefix != null && !prefix.isEmpty()) {
            effectivePrefix = prefix.trim().toLowerCase(Locale.ROOT);
            if (!effectivePrefix.matches(S3_BUCKET_NAME_VALID_CHARACTERS)) {
                throw new IllegalArgumentException(
                        "Prefix must contain only lowercase letters, numbers, dots, and hyphens");
            }

            // Remove trailing hyphen from prefix to prevent invalid bucket name
            if (effectivePrefix.endsWith("-")) {
                effectivePrefix = effectivePrefix.substring(0, effectivePrefix.length() - 1);
            }

            // Check if prefix is too long to accommodate UUID length
            // '-1' accounts for the hyphen
            if (effectivePrefix.length() > S3_BUCKET_NAME_MAX_LENGTH - UUID_LENGTH - 1) {
                throw new IllegalArgumentException(
                        "Prefix too long: must leave room for at least " + UUID_LENGTH + " UUID characters");
            }
        }

        // Generate UUID and remove hyphens
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");

        // Create bucket name
        String bucketName;
        if (effectivePrefix != null && !effectivePrefix.isEmpty()) {
            bucketName = effectivePrefix + "-" + uuid.substring(0, UUID_LENGTH);
        } else {
            bucketName = uuid.substring(0, UUID_LENGTH);
        }

        // Validate using AWS BucketNameUtils
        if (!BucketNameUtils.isValidV2BucketName(bucketName)) {
            throw new IllegalStateException("Generated bucket name does not meet S3 naming requirements");
        }

        return bucketName;
    }

    public void createS3Bucket(final S3Client s3Client, final String roleARN, final String region,
                               final String bucketName) {
        var createBucketRequest = createBucketImpl.buildCreateBucketConfig(roleARN, region, bucketName);

        try {
            s3Client.createBucket(createBucketRequest);
            log.info("Bucket created successfully");
        } catch (Exception exception) {
            log.error("Error creating bucket with bucketName: {}, region: {}",
                    bucketName, region, exception);
            throw exception;
        }
    }

    public boolean checkBucketExists(final S3Client s3Client, final String bucketName) {

        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                log.error("Bucket {} does not exist", bucketName);
                return false;
            } else if (exception.statusCode() == 403) {
                log.error("Access denied for bucket {}", bucketName);
                throw exception;
            } else {
                log.error("Error checking bucket exists: {}", exception.getMessage());
                throw exception;
            }

        }

    }


    /* TODO: Source Bucket versioning code needs to be removed from Replication Lambda and Copy bucket versioning
             Needs to be removed from Configuration bucket Lambda    */
    // Code to enable versioning on both source and target buckets
    public void enableBucketVersioning(final WorkFlowModel workflow, final S3Client s3SourceClient,
                                       final S3Client s3DestClient) {
        try {
            // Log the start of the versioning process
            String sourceBucketArn = Arn.fromString(workflow.getSourceBucketARN()).resourceAsString();
            String destBucketArn = Arn.fromString(workflow.getDestBucketARN()).resourceAsString();
            String workflowName = workflow.getWorkflowName();

            log.info("Enabling versioning for source bucket: {} and dest bucket: {} associated with workflow: {}",
                    sourceBucketArn, destBucketArn, workflowName);

            // Call the S3 client's putBucketVersioning method for source and target buckets
            s3SourceClient.putBucketVersioning(createEnableBucketVersioningRequest(sourceBucketArn));
            s3DestClient.putBucketVersioning(createEnableBucketVersioningRequest(destBucketArn));

            // Log a successful message
            log.info("Versioning enabled successfully for source bucket {} dest bucket {} associated with workflow {}",
                    sourceBucketArn, destBucketArn, workflowName);

        } catch (S3Exception s3Exception) {
            String errorMessage = s3Exception.awsErrorDetails().errorMessage();

            log.error("S3Exception while enabling bucket versioning for workflowName: {}, namespaceID: {}",
                    workflow.getWorkflowName(), workflow.getNamespaceID(), s3Exception);
            throw new RuntimeException(errorMessage);
        }
    }


    // 1-Helper method to enable the Versioned bucket config request
    private PutBucketVersioningRequest createEnableBucketVersioningRequest(final String bucketArn) {
        PutBucketVersioningRequest request = PutBucketVersioningRequest.builder()
                .bucket(bucketArn)
                .versioningConfiguration(
                        VersioningConfiguration.builder()
                                .status(BucketVersioningStatus.ENABLED).build())
                .build();
        log.info("PutBucketVersioningRequest: {}", request);
        return request;
    }
}