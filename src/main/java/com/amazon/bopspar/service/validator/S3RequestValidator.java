package com.amazon.bopspar.service.validator;

import com.amazon.bopspar.model.AWSServiceException;
import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.Workflow;
import com.amazon.bopspar.service.resources.replication.bucket.EncryptionType;
import com.amazon.bopspar.service.resources.replication.bucket.S3Bucket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketAccelerateConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAccelerateConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketNotificationConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketNotificationConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsResponse;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.ObjectOwnership;
import software.amazon.awssdk.services.s3.model.Owner;
import software.amazon.awssdk.services.s3.model.OwnershipControls;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.ListAccessPointsRequest;
import software.amazon.awssdk.services.s3control.model.ListAccessPointsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class S3RequestValidator {
    private static final Logger LOGGER = LogManager.getLogger(S3RequestValidator.class);
    private static final String WIKI_URL = "https://w.amazon.com/bin/view/TDM/S3A/#HWhatareS3AcceleratorMLPFeatures3F";

    //Maintain this set and add unsupported regions as and when we find one. For Now its only ZAZ
    private final Set<String> unSupportedRegions = Set.of("eu-south-2");


    /**
     Public method to test whether a request meets the criteria for  bucket migration.
     If it does not, we reject the request
     1. Source bucket must exist
     2. Destination bucket must exist
     3. Buckets leverage SSE-S3 default keys/encryption disabled or KMS encryption.
     Source and destination buckets must maintain same encryption type(SSE-S3 or SSE-KMS)
     4. Buckets do not use object ACLs
     5. Event notifications should be disabled
     6. Transfer acceleration should be disabled (Since new regions do not support this feature)
     7. Access points should not present
     8. Valid KMS key ARN format in destination bucket.

     All conditions must be met
     */
    public void validateMigrationRequest(final Workflow request, final S3Client sourceS3Client,
                                         final S3Client destS3Client, final S3ControlClient s3ControlClient) {

        final String sourceBucketName = Arn.fromString(
                        request.getSourceBucketARN())
                .resourceAsString();
        final String sourceAccountNumber = request.getSourceAccountNumber();
        final String sourceBucketRegion = request.getSourceRegion();

        //List of all conditions that failed.
        List<String> validationErrors = new ArrayList<>();

        S3Bucket sourceBucket = S3Bucket
                .builder()
                .arn(request.getSourceBucketARN())
                .s3Client(sourceS3Client)
                .build();

        S3Bucket destBucket = S3Bucket
                    .builder()
                    .arn(request.getDestBucketARN())
                    .s3Client(destS3Client)
                    .build();

        validateCondition(assertBucketExists(sourceBucket),
                "Source bucket must exist"
                        + " before start migration", validationErrors);
        validateCondition(assertBucketExists(destBucket),
                "Destination bucket must exist"
                        + " before start migration", validationErrors);

        validateCondition(assertEncryption(sourceBucket, destBucket),
                "Source and destination buckets with different encryption types"
                        + " are not supported for migration", validationErrors);

        if (destBucket.getEncryptionType()
                .equals(EncryptionType.SSE_KMS)) {
            validateCondition(assertKmsKeyArnFormat(destBucket.getKmsKeyId().get()),
                    "Invalid KMS key ARN format in destination bucket."
                            + " Please provide a valid ARN in the format:"
                            + " arn:aws:kms:<region>:<account-id>:key/<key-id>",validationErrors);
        }

        validateCondition(assertEventNotificationsAreTurnedOff(sourceBucketName, sourceS3Client),
                "Buckets with Event notifications turned ON are not supported for migration", validationErrors);

        validateCondition(assertIfTransferAccelerationisDisabled(sourceBucketName, sourceBucketRegion, sourceS3Client),
                "Buckets with Transfer acceleration enabled are not supported", validationErrors);

        validateCondition(assertForNoAccessPoints(sourceBucketName, sourceAccountNumber, s3ControlClient),
                "Buckets with access points are not supported", validationErrors);
        if (request.getRuntimeConfig() == null
                || (request.getRuntimeConfig().isSkipBucketOwnershipValidationAndCopy() == null
                    || !request.getRuntimeConfig().isSkipBucketOwnershipValidationAndCopy())) {
            LOGGER.info("S3RequestValidator: Running Bucket Ownership Validation");
            validateCondition(assertObjectOwnership(sourceBucket),
                    "Buckets with custom ACLs with write access granted to external parties are not supported. "
                            + "Bucket: " + sourceBucket.getName(),
                    validationErrors);
            validateCondition(assertObjectOwnership(destBucket),
                    "Buckets with custom ACLs with write access granted to external parties are not supported. "
                            + "Bucket: " + destBucket.getName(),
                    validationErrors);
        }

        if (!validationErrors.isEmpty()) {
            String errorMessage = buildErrorMessage(sourceBucketName, validationErrors);
            LOGGER.error("createWorkflow validation(s) failed for  workflow: {}, namespaceId: {} with error(s): {}",
                    request.getWorkflowName(),
                    request.getNamespaceID(),
                    errorMessage);
            LOGGER.info("For a detailed eligibility criteria supported by S3A currently"
                    + ", please refer to this wiki: {}", WIKI_URL);
            throw new InvalidInputException(errorMessage);
        }

    }

    public String buildErrorMessage(final String sourceBucketName, final List<String> validationErrors) {
        StringBuilder errorBuilder = new StringBuilder();
        errorBuilder.append("Request failed for S3 bucket: ").append(sourceBucketName).append("\n");
        errorBuilder.append("The following conditions were not met:\n");
        for (int i = 0; i < validationErrors.size(); i++) {
            errorBuilder.append(i + 1).append(". ").append(validationErrors.get(i)).append("\n");
        }
        errorBuilder.append("For a detailed eligibility criteria supported by S3A currently, "
                + "please refer to :" + WIKI_URL);
        return errorBuilder.toString();
    }

    public void validateCondition(final boolean condition, final String errorMessage,
                                   final List<String> validationErrors) {
        if (!condition) {
            validationErrors.add(errorMessage);
        }
    }

    public boolean assertForNoAccessPoints(final String sourceBucketName, final String sourceAccountNumber,
                                         final S3ControlClient s3ControlClient) {
        try {
            ListAccessPointsRequest request = ListAccessPointsRequest.builder()
                    .bucket(sourceBucketName)
                    .accountId(sourceAccountNumber)
                    .build();
            ListAccessPointsResponse response = s3ControlClient.listAccessPoints(request);
            return response.accessPointList().isEmpty(); // Returns true if there are no AP
        } catch (S3Exception e) {
            throw new AWSServiceException(e.getMessage(), e);
        }
    }

    public boolean assertIfTransferAccelerationisDisabled(final String sourceBucketName,
                                                           final String sourceBucketRegion,
                                                           final S3Client s3Client) {

        // If TA is not supported in source region, we pass the validation check and let the migration go through.
        // This helps rollback from ZAZ ->DUB
        if (unSupportedRegions.contains(sourceBucketRegion)) {
            return true;
        }
        try {
            GetBucketAccelerateConfigurationRequest request = GetBucketAccelerateConfigurationRequest.builder()
                    .bucket(sourceBucketName)
                    .build();
            GetBucketAccelerateConfigurationResponse response = s3Client.getBucketAccelerateConfiguration(request);
            return !"Enabled".equals(response.statusAsString()); // Returns true if acceleration is disabled
        } catch (S3Exception e) {
            if ("BucketAccelerateConfigurationNotFoundError".equals(e.awsErrorDetails().errorCode())) {
                return true; // Transfer acceleration is not enabled
            }
            throw new AWSServiceException(e.getMessage(), e);
        }
    }

    public boolean assertEventNotificationsAreTurnedOff(final String sourceBucketName, final S3Client s3Client) {
        try {
            GetBucketNotificationConfigurationResponse notificationResponse =
                    s3Client.getBucketNotificationConfiguration(GetBucketNotificationConfigurationRequest.builder()
                            .bucket(sourceBucketName)
                            .build());

            // Check true if nothing is present
            return !notificationResponse.hasTopicConfigurations()
                    || !notificationResponse.hasQueueConfigurations()
                    || !notificationResponse.hasLambdaFunctionConfigurations()
                    || notificationResponse.eventBridgeConfiguration() == null;
        } catch (S3Exception e) {
            throw new AWSServiceException(e.getMessage(), e);
        }
    }


    // Asserts that the source and destination buckets have the same encryption type.
    public boolean assertEncryption(final S3Bucket sourceS3Bucket, final S3Bucket destS3Bucket) {
        try {
            return !sourceS3Bucket.getEncryptionType()
                    .equals(EncryptionType.UNKNOWN)
                    && sourceS3Bucket.getEncryptionType()
                    .equals(destS3Bucket.getEncryptionType());

        } catch (S3Exception e) {
            throw new AWSServiceException(e.getMessage(), e);
        }
    }

    // Asserts that a bucket exists
    public boolean assertBucketExists(final S3Bucket s3Bucket) {
        try {
            return s3Bucket.isBucketExists();
        } catch (Exception e) {
            LOGGER.error("Error validating existence of bucket: {}",
                    e.getMessage(), e);
            return false; // we will not proceed with migration
        }
    }

    public boolean assertObjectOwnership(final S3Bucket s3Bucket) {
        final S3Client s3Client = s3Bucket.getS3Client();
        try {
            if (isBucketOwnerEnforcedEnabled(s3Bucket)) {
                LOGGER.info("Bucket {} has default BucketOwnerEnforced enabled.", s3Bucket.getName());
                return true;
            }
            GetBucketAclRequest aclRequest = GetBucketAclRequest.builder()
                    .bucket(s3Bucket.getName())
                    .build();
            GetBucketAclResponse aclResponse = s3Client.getBucketAcl(aclRequest);
            final Owner bucketOwner = aclResponse.owner();

            boolean hasWritePermissions = aclResponse.grants().stream()
                    .filter(grant -> !bucketOwner.id().equals(grant.grantee().id()))
                    .anyMatch(this::hasWritePermissions);

            if (hasWritePermissions) {
                LOGGER.warn("Bucket {} has Custom ACL with write permissions present.", s3Bucket.getName());
                return false;
            }
            return true; // Custom ACL present with read-only permissions
        } catch (S3Exception exception) {
            LOGGER.error("Error checking Object Ownership: {}", exception.getMessage());
            return false;
        }
    }

    private boolean isBucketOwnerEnforcedEnabled(final S3Bucket s3Bucket) {
        try {
            final S3Client s3Client = s3Bucket.getS3Client();
            final GetBucketOwnershipControlsResponse bucketOwnershipResponse =
                    s3Client.getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                            .bucket(s3Bucket.getName())
                            .build());
            final OwnershipControls bucketOwnershipControls = bucketOwnershipResponse.ownershipControls();

            return bucketOwnershipControls.rules().stream()
                    .anyMatch(rule -> rule.objectOwnership() == ObjectOwnership.BUCKET_OWNER_ENFORCED);
        } catch (S3Exception exception) {
            if (exception.awsErrorDetails().errorCode().equals("OwnershipControlsNotFoundError")) {
                LOGGER.warn("Ownership controls not found for bucket {}, likely an old bucket.", s3Bucket.getName());
                return false; // OwnershipControlsNotFound will default to ObjectWriter, so we still want to check ACLs
            }
            throw exception;
        }
    }

    private boolean hasWritePermissions(final Grant grant) {
        return grant.permission() == Permission.WRITE
                || grant.permission() == Permission.WRITE_ACP
                || grant.permission() == Permission.FULL_CONTROL;
    }

    /**
     * Asserts that the KMS key ARN is in the correct format.
     * @param kmsKeyArn the KMS key ARN to validate
     * @return true if the KMS key ARN is valid, false otherwise
     */
    public boolean assertKmsKeyArnFormat(final String kmsKeyArn) {
        if (kmsKeyArn == null) {
            LOGGER.error("KMS key ARN cannot be null");
            return false;
        }
        try {
            Arn arn = Arn.fromString(kmsKeyArn);
            if (!"kms".equals(arn.service())) {
                LOGGER.error("Invalid service in ARN. Expected 'kms' but got '{}'", arn.service());
                return false;
            }
            String resource = arn.resourceAsString();
            if (!resource
                    .startsWith("key/")) {
                LOGGER.error("Invalid KMS resource format. Resource must start with 'key/'");
                return false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid KMS key ARN format: {}", e.getMessage());
            return false;
        }
    }

}