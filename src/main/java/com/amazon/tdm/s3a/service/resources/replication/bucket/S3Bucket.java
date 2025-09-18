package com.amazon.tdm.s3a.service.resources.replication.bucket;

import com.amazon.tdm.s3a.model.AWSServiceException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.util.Optional;

@Builder
@Getter
@Slf4j
/**
 * Represents an S3 bucket with its associated S3Client.
 * This class provides methods to interact with the S3 bucket
 * and its encryption configuration.
 */
public class S3Bucket {
    private String arn;
    private S3Client s3Client;

    /**
     * Retrieves the bucket name from the ARN associated with this
     * S3Bucket instance.
     *
     * @return the bucket name as a String
     */
    public String getName() {
        return Arn.fromString(arn).resourceAsString();
    }

    /**
     * Determines the encryption type of the S3 bucket.
     *
     * @return the EncryptionType enum value representing the bucket's encryption configuration
     or UNKNOWN if there was an error retrieving the configuration or the KMS key is missing.
     */
    public EncryptionType getEncryptionType() {
        String bucketName = getName();
        try {
            GetBucketEncryptionResponse encryptionResponse = s3Client.getBucketEncryption(
                    GetBucketEncryptionRequest.builder().bucket(bucketName).build()
            );

            if (encryptionResponse.serverSideEncryptionConfiguration() == null
                    || encryptionResponse.serverSideEncryptionConfiguration().rules().isEmpty()) {
                return EncryptionType.NONE;
            }

            for (ServerSideEncryptionRule rule : encryptionResponse.serverSideEncryptionConfiguration().rules()) {
                ServerSideEncryptionByDefault encryptionByDefault = rule.applyServerSideEncryptionByDefault();

                if (ServerSideEncryption.AWS_KMS.equals(encryptionByDefault.sseAlgorithm())
                        || ServerSideEncryption.AWS_KMS_DSSE.equals(encryptionByDefault.sseAlgorithm())) {
                    if (encryptionByDefault.kmsMasterKeyID() == null) {
                        return EncryptionType.UNKNOWN;
                    }
                    return EncryptionType.SSE_KMS;
                }

                if (ServerSideEncryption.AES256.equals(encryptionByDefault.sseAlgorithm())) {
                    return EncryptionType.SSE_S3;
                }
            }

            return EncryptionType.NONE;
        } catch (Exception e) {
            log.error("Failed to get encryption configuration for {}: {}", bucketName, e.getMessage());
            throw new AWSServiceException("Failed to get bucket encryption configuration for bucket: " + bucketName);
        }
    }

    /**
     * Retrieves the KMS Key ID associated with the bucket's
     * encryption configuration.
     *
     * @return the KMS Key ID as a String wrapped in an Optional,
    or an empty Optional if no KMS encryption is found
     */
    public Optional<String> getKmsKeyId() {
        String bucketName = getName();
        try {
            GetBucketEncryptionResponse encryptionResponse = s3Client.getBucketEncryption(
                    GetBucketEncryptionRequest.builder().bucket(bucketName).build()
            );

            if (encryptionResponse.serverSideEncryptionConfiguration() == null
                    || encryptionResponse.serverSideEncryptionConfiguration().rules().isEmpty()) {
                log.info("Bucket {} does not have encryption enabled.", bucketName);
                return Optional.empty(); // No encryption rules
            }

            for (ServerSideEncryptionRule rule : encryptionResponse.serverSideEncryptionConfiguration().rules()) {
                ServerSideEncryptionByDefault encryptionByDefault = rule.applyServerSideEncryptionByDefault();

                if (isKmsEncryption(encryptionByDefault)) {
                    if (encryptionByDefault.kmsMasterKeyID() != null) {
                        return Optional.of(encryptionByDefault.kmsMasterKeyID()); // KMS encryption found
                    }
                }
            }

            log.info("Bucket {} does not use KMS encryption.", bucketName);
            return Optional.empty(); // No KMS encryption found
        } catch (Exception e) {
            log.info("Failed to retrieve encryption for bucket {}: {}", bucketName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Checks if the bucket's encryption configuration uses KMS encryption.
     *
     * @return true if the bucket uses KMS encryption, false otherwise
     */
    private boolean isKmsEncryption(final ServerSideEncryptionByDefault encryption) {
        return encryption != null && encryption.sseAlgorithm() != null
                && (ServerSideEncryption.AWS_KMS.equals(encryption.sseAlgorithm())
                        || ServerSideEncryption.AWS_KMS_DSSE.equals(encryption.sseAlgorithm()));
    }

    /**
     * Checks if the S3 bucket exists and is accessible.
     *
     * @return true if the bucket exists and is accessible, false otherwise
     */
    public boolean isBucketExists() {
        final String bucketName = getName();
        try {
            s3Client.headBucket(request -> request.bucket(bucketName));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 403) {
                log.info("Bucket {} exists but access is forbidden.", bucketName);
            } else if (e.statusCode() == 404) {
                log.info("Bucket {} does not exist.", bucketName);
            } else {
                log.error("Error checking bucket {}: {}", bucketName, e.getMessage());
            }
            return false;
        }
    }
}
