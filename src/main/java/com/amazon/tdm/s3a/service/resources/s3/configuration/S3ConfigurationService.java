package com.amazon.tdm.s3a.service.resources.s3.configuration;


import com.amazon.tdm.s3a.model.AWSServiceException;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.resources.s3.bucket.S3CreateBucketService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AccessControlPolicy;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.BucketLoggingStatus;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketCorsResponse;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketLoggingRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLoggingResponse;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsResponse;
import software.amazon.awssdk.services.s3.model.GetBucketRequestPaymentRequest;
import software.amazon.awssdk.services.s3.model.GetBucketRequestPaymentResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetBucketWebsiteRequest;
import software.amazon.awssdk.services.s3.model.GetBucketWebsiteResponse;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationResponse;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LoggingEnabled;
import software.amazon.awssdk.services.s3.model.ObjectOwnership;
import software.amazon.awssdk.services.s3.model.Owner;
import software.amazon.awssdk.services.s3.model.OwnershipControls;
import software.amazon.awssdk.services.s3.model.PutBucketAclRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLoggingRequest;
import software.amazon.awssdk.services.s3.model.PutBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketRequestPaymentRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutBucketWebsiteRequest;
import software.amazon.awssdk.services.s3.model.PutObjectLockConfigurationRequest;
import software.amazon.awssdk.services.s3.model.RequestPaymentConfiguration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;
import software.amazon.awssdk.services.s3.model.WebsiteConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class S3ConfigurationService {

    private static final Logger LOGGER = LogManager.getLogger(S3ConfigurationService.class);

    private static final String BUCKET_VERSIONING = "bucketVersioning";
    private static final String BUCKET_REQUEST_PAYMENT = "bucketRequestPayment";
    private static final String BUCKET_WEBSITE = "bucketWebsite";
    private static final String BUCKET_CORS = "bucketCors";
    private static final String BUCKET_OBJECT_LOCK = "bucketObjectLock";
    private static final String BUCKET_OWNERSHIP_CONTROLS = "bucketOwnershipControls";
    private static final String BUCKET_ACL = "bucketACL";
    private static final String BUCKET_LIFECYCLE = "bucketLifecycle";
    private static final String BUCKET_ENCRYPTION = "bucketEncryption";
    private static final String BUCKET_KMS_KEY_POLICY = "bucketKmsKeyPolicy";
    private static final String BUCKET_CROSS_ACCOUNT_SID = "S3ASourceCrossAccountAccess";

    /**
     * Save configuration from source to destination.
     *
     */
    public void saveConfiguration(final S3Client sourceClient, final S3Client destClient,
                                  final String sourceBucketName, final String destBucketName) {

        try {
            copyBucketVersioning(sourceClient, destClient,
                    sourceBucketName, destBucketName);
            copyBucketRequestPayment(sourceClient, destClient,
                    sourceBucketName, destBucketName);
            copyBucketWebsite(sourceClient, destClient,
                    sourceBucketName, destBucketName);
            copyBucketCors(sourceClient, destClient,
                    sourceBucketName, destBucketName);
            copyObjectLock(sourceClient, destClient,
                    sourceBucketName, destBucketName);
            copyBucketLifecycleRules(sourceClient, destClient,
                    sourceBucketName, destBucketName);
        } catch (S3Exception s3Exception) {
            LOGGER.error("Error copying bucket configuration: {}", s3Exception.getMessage());
            throw s3Exception;
        }
        catch (AwsServiceException awsServiceException) {
            LOGGER.error("AWS Service failure: {}", awsServiceException.getMessage());
            throw awsServiceException;
        } catch (Exception exception) {
            LOGGER.error("Unexpected error: {}", exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    private void copyObjectLock(final S3Client sourceClient, final S3Client destClient,
                                          final String sourceBucket, final String destBucket) {

        try {
            GetObjectLockConfigurationResponse objectLockResponse =
                    sourceClient.getObjectLockConfiguration(GetObjectLockConfigurationRequest.builder()
                            .bucket(sourceBucket)
                            .build());
            if (objectLockResponse != null) {
                LOGGER.info("Copying object lock configuration: {} to {}", sourceBucket, destBucket);
                destClient.putObjectLockConfiguration(PutObjectLockConfigurationRequest.builder()
                        .bucket(destBucket)
                        .objectLockConfiguration(objectLockResponse.objectLockConfiguration() == null
                                ? null : objectLockResponse.objectLockConfiguration())
                        .build());
            }
        } catch (S3Exception s3Exception) {
            if (s3Exception.statusCode() != 404) {
                throw s3Exception;
            } else {
                LOGGER.warn("Object lock configuration not found for  {}", sourceBucket);
            }
        }
    }

    private void copyBucketVersioning(final S3Client sourceClient, final S3Client destClient,
                                      final String sourceBucket, final String destBucket) {
        try {
            GetBucketVersioningResponse versioningResponse =
                    sourceClient.getBucketVersioning(GetBucketVersioningRequest.builder()
                            .bucket(sourceBucket)
                            .build());
            if (versioningResponse != null) {
                LOGGER.info("Copying bucket versioning: {} to {}", sourceBucket, destBucket);
                destClient.putBucketVersioning(PutBucketVersioningRequest.builder()
                        .bucket(destBucket)
                        .versioningConfiguration(VersioningConfiguration.builder()
                                .status(versioningResponse.status() == null ? null : versioningResponse.status())
                                .build())
                        .build());
            }
        } catch (S3Exception s3Exception) {
            if (s3Exception.statusCode() != 404) {
                throw s3Exception;
            } else {
                LOGGER.warn("Bucket versioning not found for {}", sourceBucket);
            }
        }
    }

    private void copyBucketRequestPayment(final S3Client sourceClient, final S3Client destClient,
                                          final String sourceBucket, final String destBucket) {

        try {
            GetBucketRequestPaymentResponse requestPaymentResponse =
                    sourceClient.getBucketRequestPayment(GetBucketRequestPaymentRequest.builder()
                            .bucket(sourceBucket)
                            .build());
            if (requestPaymentResponse != null) {
                LOGGER.info("Copying bucket request payment: {} to {}", sourceBucket, destBucket);
                destClient.putBucketRequestPayment(PutBucketRequestPaymentRequest.builder()
                        .bucket(destBucket)
                        .requestPaymentConfiguration(RequestPaymentConfiguration.builder()
                                .payer(requestPaymentResponse.payer() == null ? null : requestPaymentResponse.payer())
                                .build())
                        .build());
            }
        } catch (S3Exception s3Exception) {
            if (s3Exception.statusCode() != 404) {
                throw s3Exception;
            } else {
                LOGGER.warn("Bucket request payment not found for {}", sourceBucket);
            }
        }
    }

    private void copyBucketWebsite(final S3Client sourceClient, final S3Client destClient,
                                   final String sourceBucket, final String destBucket) {
        try {
            GetBucketWebsiteResponse websiteResponse = sourceClient.getBucketWebsite(GetBucketWebsiteRequest.builder()
                    .bucket(sourceBucket)
                    .build());
            if (websiteResponse != null) {
                LOGGER.info("Copying bucket website: {} to {}", sourceBucket, destBucket);
                destClient.putBucketWebsite(PutBucketWebsiteRequest.builder()
                        .bucket(destBucket)
                        .websiteConfiguration(WebsiteConfiguration.builder()
                                .errorDocument(websiteResponse.errorDocument() == null
                                        ? null : websiteResponse.errorDocument())
                                .indexDocument(websiteResponse.indexDocument() == null
                                        ? null : websiteResponse.indexDocument())
                                .routingRules(websiteResponse.routingRules() == null
                                        ? null : websiteResponse.routingRules())
                                .redirectAllRequestsTo(websiteResponse.redirectAllRequestsTo() == null
                                        ? null : websiteResponse.redirectAllRequestsTo())
                                .build())
                        .build());
            }
        } catch (S3Exception s3Exception) {
            if (s3Exception.statusCode() != 404) {
                throw s3Exception;
            } else {
                LOGGER.warn("Bucket website not found for {}", sourceBucket);
            }
        }
    }

    private void copyBucketCors(final S3Client sourceClient, final S3Client destClient,
                                final String sourceBucket, final String destBucket) {
        try {
            GetBucketCorsResponse corsResponse = sourceClient.getBucketCors(GetBucketCorsRequest.builder()
                    .bucket(sourceBucket)
                    .build());
            if (corsResponse != null) {
                LOGGER.info("Copying bucket CORS: {} to {}", sourceBucket, destBucket);
                destClient.putBucketCors(PutBucketCorsRequest.builder()
                        .bucket(destBucket)
                        .corsConfiguration(CORSConfiguration.builder()
                                .corsRules(corsResponse.corsRules() == null ? null : corsResponse.corsRules())
                                .build())
                        .build());
            }
        } catch (S3Exception s3Exception) {
            if (s3Exception.statusCode() != 404) {
                throw s3Exception;
            } else {
                LOGGER.warn("Bucket CORS not found for {}", sourceBucket);
            }
        }
    }

    private boolean copyBucketOwnershipRules(final GetBucketOwnershipControlsResponse bucketOwnershipResponse,
                                          final S3Client destClient,
                                          final String sourceBucket,
                                          final String destBucket,
                                          final String destAccount) {
        final ObjectOwnership sourceBucketOwnership = bucketOwnershipResponse.ownershipControls()
            .rules()
            .get(0)
            .objectOwnership();
        if (sourceBucketOwnership != ObjectOwnership.BUCKET_OWNER_ENFORCED) {
            // Copy the ownership rules, only if they are different than the default (BucketOwnerEnforced)
            LOGGER.info("Non-default Rules! {}",
                bucketOwnershipResponse.ownershipControls().rules().toString());
            LOGGER.info("Copying bucket ownership controls from: {} to {}", sourceBucket, destBucket);
            destClient.putBucketOwnershipControls(PutBucketOwnershipControlsRequest.builder()
                    .bucket(destBucket)
                    .expectedBucketOwner(destAccount)
                    .ownershipControls(OwnershipControls.builder()
                            .rules(bucketOwnershipResponse.ownershipControls().rules())
                            .build())                            
                    .build());
            return true;
        }
        return false;
    }

    private void copyCustomBucketAcls(final S3Client sourceClient,
                                      final S3Client destClient,
                                      final String sourceBucket,
                                      final String destBucket,
                                      final String destAccount) {
        // Identify and apply the non-default ACL's to the destination bucket                            
        final GetBucketAclResponse sourceBucketAclResponse = sourceClient.getBucketAcl(GetBucketAclRequest.builder()
            .bucket(sourceBucket)
            .build());                    
        // Get the owner of the destination bucket
        final GetBucketAclResponse destBucketAclResponse = destClient.getBucketAcl(GetBucketAclRequest.builder()
            .bucket(destBucket)
            .build());
        final Owner destOwner = destBucketAclResponse.owner();

        if (sourceBucketAclResponse.hasGrants()) {
            LOGGER.info("Source Bucket ACL: {}", sourceBucketAclResponse.toString());
            List<Grant> destGrants = new ArrayList();
            for (Grant grant: sourceBucketAclResponse.grants()) {
                if (!grant.grantee().id().equals(sourceBucketAclResponse.owner().id())) {
                    destGrants.add(grant);       
                }
            }
            if (!destGrants.isEmpty()) {
                LOGGER.info("Dest Bucket ACL: {}", destGrants.toString());
                destClient.putBucketAcl(PutBucketAclRequest.builder()
                    .bucket(destBucket)
                    .expectedBucketOwner(destAccount)
                    .accessControlPolicy(AccessControlPolicy.builder()
                        .owner(destOwner)
                        .grants(destGrants)
                        .build())
                    .build());
            }
        }
    }

    /**
     * Public method to configure Bucket Ownership Controls.     
     * Note: The reason this is not combined with other configurations is
     *  1. Creating a public method isolates testing
     *  2. Reduce regressions of working code
     *  3. Logic involves using workflowModel.destAccountId
     * @param sourceClient S3 Client (source)
     * @param destClient S3 Client (destination)
     * @param workflowModel S3A Workflow persistence model
     */
    public void configureBucketOwnershipControls(final S3Client sourceClient, 
                                                 final S3Client destClient,
                                                 final WorkFlowModel workflowModel) { 
        final String destBucket = Arn.fromString(workflowModel.getDestBucketARN()).resourceAsString();
        final String sourceBucket = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();
        final String destAccount = workflowModel.getDestAccountNumber(); 
        try {
            final GetBucketOwnershipControlsResponse bucketOwnershipResponse =
                sourceClient.getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                    .bucket(sourceBucket)
                    .build());            
            if (bucketOwnershipResponse.ownershipControls().hasRules()) { 
                // Verify if ownership rules are non-standard and if any custom ACL's need to be copied
                if (copyBucketOwnershipRules(bucketOwnershipResponse, destClient,
                        sourceBucket, destBucket, destAccount)) {
                    copyCustomBucketAcls(sourceClient, destClient, sourceBucket, destBucket, destAccount);
                } else {
                    LOGGER.info("BucketOwnerEnforced default Rule is enabled, no action required");
                } 
            }                         
        } catch (S3Exception s3Exception) {            
            LOGGER.error("Exception while configuring bucket for workflowName: {}, namespaceID: {}, errorMessage: {}",
                workflowModel.getWorkflowName(),
                workflowModel.getNamespaceID(),
                s3Exception.getMessage());
            throw s3Exception;            
        }
    }

    /**
     * Get S3 Bucket configuration.
     *
     * @return Map of bucket configuration
     */
    public Map<String, String> getBucketConfiguration(final S3Client s3Client,
                                                      final KmsClient kmsClient, final String bucketName) {
        Map<String, String> bucketConfiguration = new HashMap<>();

        try {

            addIfValidResponse(bucketConfiguration, BUCKET_VERSIONING,
                    () -> s3Client.getBucketVersioning(GetBucketVersioningRequest.builder()
                            .bucket(bucketName)
                            .build()));

            addIfValidResponse(bucketConfiguration, BUCKET_REQUEST_PAYMENT,
                    () -> s3Client.getBucketRequestPayment(
                            GetBucketRequestPaymentRequest.builder()
                                    .bucket(bucketName)
                                    .build()));

            addIfValidResponse(bucketConfiguration, BUCKET_WEBSITE,
                    () -> s3Client.getBucketWebsite(
                            GetBucketWebsiteRequest.builder()
                                    .bucket(bucketName)
                                    .build()));


            addIfValidResponse(bucketConfiguration, BUCKET_CORS,
                    () -> s3Client.getBucketCors(
                            GetBucketCorsRequest.builder()
                                    .bucket(bucketName)
                                    .build()));

            addIfValidResponse(bucketConfiguration, BUCKET_OBJECT_LOCK,
                    () -> s3Client.getObjectLockConfiguration(
                            GetObjectLockConfigurationRequest.builder()
                                    .bucket(bucketName)
                                    .build()));

            addIfValidResponse(bucketConfiguration, BUCKET_OWNERSHIP_CONTROLS,
                    () -> s3Client.getBucketOwnershipControls(
                            GetBucketOwnershipControlsRequest.builder()
                                    .bucket(bucketName)
                                    .build()));

            addIfValidResponse(bucketConfiguration, BUCKET_ACL,
                    () -> s3Client.getBucketAcl(
                            GetBucketAclRequest.builder()
                                    .bucket(bucketName)
                                    .build()));

            addIfValidResponse(bucketConfiguration, BUCKET_LIFECYCLE,
                    () -> s3Client.getBucketLifecycleConfiguration(
                            GetBucketLifecycleConfigurationRequest.builder()
                                    .bucket(bucketName)
                                    .build()));

            GetBucketEncryptionResponse encryptionConfiguration = s3Client.getBucketEncryption(
                    GetBucketEncryptionRequest.builder()
                            .bucket(bucketName)
                            .build());

            addIfValidResponse(bucketConfiguration, BUCKET_ENCRYPTION,
                    () -> encryptionConfiguration);

            Optional<String> keyId = getKmsKeyId(encryptionConfiguration);

            keyId.ifPresent(kmsKeyId -> addIfValidResponse(bucketConfiguration, BUCKET_KMS_KEY_POLICY,
                    () -> kmsClient.getKeyPolicy(
                            GetKeyPolicyRequest.builder()
                                    .keyId(kmsKeyId)
                                    .build())));

            return bucketConfiguration;

        } catch (AwsServiceException awsServiceException) {
            LOGGER.error("AWS Service failure: {}", awsServiceException.getMessage());
            throw awsServiceException;
        } catch (Exception exception) {
            LOGGER.error("An unexpected error occurred: {}", exception.getMessage());
            throw new RuntimeException(exception);
        }

    }

    /**
     * Retrieves the KMS Key ID associated with the bucket's
     * encryption configuration.
     *
     * @return the KMS Key ID as a String wrapped in an Optional,
    or an empty Optional if no KMS encryption is found
     */
    public Optional<String> getKmsKeyId(final GetBucketEncryptionResponse encryptionResponse) {
        try {
            if (encryptionResponse.serverSideEncryptionConfiguration() == null
                    || encryptionResponse.serverSideEncryptionConfiguration().rules().isEmpty()) {
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
            return Optional.empty(); // No KMS encryption found
        } catch (Exception e) {
            LOGGER.error("Error retrieving encryption {}", e.getMessage());
            throw new AWSServiceException("Failed to retrieve encryption", e);
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


    private <T extends AwsResponse> void addIfValidResponse(final Map<String, String> bucketConfiguration,
                                                            final String key, final Supplier<T> supplier) {
        try {
            T response = supplier.get();
            ObjectMapper objectMapper = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

            if (response != null) {
                LOGGER.info("Response received for {}. Adding to bucket configuration.", key);
                String jsonValue = objectMapper.writeValueAsString(response.toBuilder());
                bucketConfiguration.put(key, jsonValue);

            } else {
                LOGGER.warn("No response received for {}. Skipping this configuration.", key);
            }
        } catch (S3Exception s3Exception) {
            if (s3Exception.statusCode() != 404) {
                throw s3Exception;
            } else {
                LOGGER.warn("Resource not found for: {}", s3Exception.getMessage());
            }
        } catch (AwsServiceException awsServiceException) {
            LOGGER.error("AWS Service failure for {}: {}", key, awsServiceException.getMessage());
            throw awsServiceException;
        } catch (JsonProcessingException jsonProcessingException) {
            LOGGER.error("JSON exception for {}: {}", key, jsonProcessingException.getMessage());
            throw new RuntimeException("Failed to process JSON configuration", jsonProcessingException);
        } catch (Exception exception) {
            LOGGER.error("An unexpected error occurred while retrieving {}: {}", key, exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    private String modifyBucketPolicy(final String policy, final String sourceBucket,
                                      final String destBucket) {

        String sourceBucketArn = String.format("arn:aws:s3:::%s", sourceBucket);
        String destBucketArn = String.format("arn:aws:s3:::%s", destBucket);

        return policy.replace(sourceBucketArn, destBucketArn);
    }

    /**
     * Public method to enable Server access logging.
     * Step 1: check if Server access logging is enabled
     * Step 2: If it is enabled, create a new server access logging bucket in target region
     * Step 3: Enable SAG on the target bucket
     * Note: The reason this is not combined with other configurations is
     *  1. Creating a public method isolates testing
     *  2. Reduce regressions of working code
     *  3. Logic involves using createbucketService
     */
    public void configureServerAccessLogging(final S3Client s3SourceClient, final S3Client s3DestClient,
                                          final WorkFlowModel workflowModel,
                                          final S3CreateBucketService s3CreateBucketService) {

        final String destBucketName = Arn.fromString(workflowModel.getDestBucketARN()).resourceAsString();
        final String sourceBucketName = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();

        // Check if source bucket has server access logging enabled
        boolean hasServerAccessLogging = isServerAccessLoggingEnabled(s3SourceClient,sourceBucketName, workflowModel);
        if (!hasServerAccessLogging) {
            LOGGER.info("Source bucket: {} does not have server access logging enabled for  "
                            + "workflowName: {}, namespaceID: {}",
                    sourceBucketName,
                    workflowModel.getWorkflowName(),
                    workflowModel.getNamespaceID());
            return;
        }

        //Generate the name and create the bucket
        String serverAccessLoggingBucketName = s3CreateBucketService.generateBucketName("server-access-logging");
        s3CreateBucketService.createS3Bucket(s3DestClient,
                workflowModel.getDestRoleARN(),
                workflowModel.getDestRegion(),
                serverAccessLoggingBucketName);

        // Enable server access logging for the destination bucket
        enableServerAccessLogging(s3DestClient,destBucketName, serverAccessLoggingBucketName, workflowModel);
    }

    private void enableServerAccessLogging(final S3Client s3Client, final String destBucketName,
                                           final String accessLoggingBucketName, final WorkFlowModel workflowModel) {
        try {
            PutBucketLoggingRequest loggingRequest = PutBucketLoggingRequest.builder()
                    .bucket(destBucketName)
                    .bucketLoggingStatus(BucketLoggingStatus.builder()
                            .loggingEnabled(LoggingEnabled.builder()
                                    .targetBucket(accessLoggingBucketName)
                                    .targetPrefix("logs/")
                                    .build())
                            .build())
                    .build();

            s3Client.putBucketLogging(loggingRequest);

            LOGGER.info("Successfully enabled Server access logging on destination bucket: {} "
                            + "for workflowName: {}, namespaceID: {}",
                    destBucketName,
                    workflowModel.getWorkflowName(),
                    workflowModel.getNamespaceID());
        } catch (S3Exception s3Exception) {
            LOGGER.error("Exception while configuring bucket for workflowName: {}, namespaceID: {}, errorMessage: {}",
                    workflowModel.getWorkflowName(),
                    workflowModel.getNamespaceID(),
                    s3Exception.getMessage());
            throw s3Exception;
        }
    }

    private boolean isServerAccessLoggingEnabled(final S3Client s3Client,
                                                 final String sourceBucketName,
                                                 final WorkFlowModel workflowModel) {
        try {
            GetBucketLoggingResponse loggingResponse = s3Client.getBucketLogging(
                    GetBucketLoggingRequest.builder().bucket(sourceBucketName).build()
            );

            return loggingResponse.loggingEnabled() != null;
        } catch (S3Exception e) {
            LOGGER.error("Exception while checking Server access logging for  workflowName: {}, "
                            + "namespaceID: {}, "
                            + "errorMessage: {}",
                    workflowModel.getWorkflowName(),
                    workflowModel.getNamespaceID(),
                    e.getMessage());
            return false;
        }
    }

    private void copyBucketLifecycleRules(final S3Client sourceS3Client,
                                          final S3Client destS3Client,
                                          final String sourceBucketName,
                                          final String destBucketName) {
        try {
            GetBucketLifecycleConfigurationResponse lifecycleResponse = sourceS3Client.getBucketLifecycleConfiguration(
                    GetBucketLifecycleConfigurationRequest.builder()
                            .bucket(sourceBucketName)
                            .build());
            if (hasLifecycleRules(lifecycleResponse)) {
                LOGGER.info("Copying lifecycle rules from {} to {}", sourceBucketName, destBucketName);
                destS3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                        .bucket(destBucketName)
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(lifecycleResponse.rules())
                                .build())
                        .build());
            }
        } catch (S3Exception s3Exception) {
            handleS3NotFoundException(s3Exception,
                    () -> LOGGER.info("Bucket lifecycle not found for {}", sourceBucketName));
        }
    }

    /**
     * Method to modify the status of bucket lifecycle rules if they exist.
     *
     * @param s3Client S3Client
     * @param bucketName Bucket name
     * @param status Updated status of rules, either Enabled or Disabled
     * */
    public void modifyBucketLifecycleRulesStatus(final S3Client s3Client,
                                                 final String bucketName,
                                                 final ExpirationStatus status) {
        try {
            GetBucketLifecycleConfigurationResponse lifecycleResponse = s3Client.getBucketLifecycleConfiguration(
                    GetBucketLifecycleConfigurationRequest.builder()
                            .bucket(bucketName)
                            .build());
            if (hasLifecycleRules(lifecycleResponse)) {
                LOGGER.info("Updating status for bucket lifecycle rules to {}", status);
                List<LifecycleRule> updatedRules = lifecycleResponse.rules().stream()
                        .map(rule -> rule.toBuilder()
                                .status(status)
                                .build())
                        .toList();
                s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                        .bucket(bucketName)
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(updatedRules)
                                .build())
                        .build());
                LOGGER.info("Successfully updated lifecycle rules for bucket {} to {}", bucketName, status);
            }
        } catch (S3Exception s3Exception) {
            handleS3NotFoundException(s3Exception, () -> LOGGER.info("Bucket lifecycle not found for {}", bucketName));
        }
    }

    private void handleS3NotFoundException(final S3Exception s3Exception, final Runnable logAction) {
        if (s3Exception.statusCode() != HttpStatusCode.NOT_FOUND) {
            throw s3Exception;
        } else {
            logAction.run();
        }
    }

    private boolean hasLifecycleRules(final GetBucketLifecycleConfigurationResponse response) {
        return response != null && response.hasRules();
    }

}