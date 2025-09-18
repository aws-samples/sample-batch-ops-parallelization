package com.amazon.tdm.s3a.service.validator;

//import com.amazon.tdm.common.exceptions.InvalidInputException;
import com.amazon.tdm.s3a.model.AWSServiceException;
import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.model.CreateWorkflowRequest;
import com.amazon.tdm.s3a.model.RuntimeConfig;
import com.amazon.tdm.s3a.model.Workflow;
import com.amazon.tdm.s3a.service.resources.replication.bucket.S3Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAccelerateStatus;
import software.amazon.awssdk.services.s3.model.EventBridgeConfiguration;
import software.amazon.awssdk.services.s3.model.GetBucketAccelerateConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAccelerateConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketNotificationConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketNotificationConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsResponse;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.Grantee;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.ObjectOwnership;
import software.amazon.awssdk.services.s3.model.Owner;
import software.amazon.awssdk.services.s3.model.OwnershipControls;
import software.amazon.awssdk.services.s3.model.OwnershipControlsRule;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;
import software.amazon.awssdk.services.s3.model.Type;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.AccessPoint;
import software.amazon.awssdk.services.s3control.model.ListAccessPointsRequest;
import software.amazon.awssdk.services.s3control.model.ListAccessPointsResponse;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.reset;

class S3RequestValidatorTest {

    @Mock
    private S3Client sourceS3Client;

    @Mock
    private S3Client destS3Client;

    @Mock
    private S3ControlClient s3ControlClient;

    @InjectMocks
    private S3RequestValidator validator; // Class under test

    private CreateWorkflowRequest validRequest;

    private Workflow mockWorkflow;

    // Define unsupported regions in a centralized map
    private static final Set<String> unSupportedRegions = Set.of("eu-south-1");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create a valid CreateWorkflowRequest object
        mockWorkflow = new Workflow();
        mockWorkflow.setSourceBucketARN("arn:aws:s3:::source-bucket");
        mockWorkflow.setDestBucketARN("arn:aws:s3:::dest-bucket");
        mockWorkflow.setSourceAccountNumber("123456789012");
        mockWorkflow.setDestAccountNumber("123456789012");
        mockWorkflow.setSourceRegion("us-east-1");
        validRequest = new CreateWorkflowRequest();
        validRequest.setWorkflow(mockWorkflow);
    }

    // Success Case: All validation checks pass
    @Test
    void testValidateMigrationRequest_Success() {
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));
    }

    // Failure case: Different encryption on source and destination buckets
    @Test
    void testValidateMigrationRequest_FailsDueToEncryption() {
        mockBothBucketsExist();
        mockBucketsWithDifferentEncryption();
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );
        assertTrue(exception.getMessage().contains("Source and destination buckets with different encryption types"));
    }

    // Failure case: Source bucket does not exist
    @Test
    void testValidateMigrationRequest_FailsDueToSourceBucketsDoNotExist() {
        mockSourceBucketDoesNotExist();
        mockBucketsWithSameEncryption();
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );
        assertTrue(exception.getMessage().contains("Source bucket must exist"));
    }

    // Failure case: Destination bucket does not exist
    @Test
    void testValidateMigrationRequest_FailsDueToDestBucketsDoNotExist() {
        mockDestinationBucketDoesNotExist();
        mockBucketsWithSameEncryption();
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );
        assertTrue(exception.getMessage().contains("Destination bucket must exist"));
    }

    // Failure case: Destination bucket kms key has wrong format
    @Test
    void testValidateMigrationRequest_FailsDueToDestKmsKeyFormat() {
        mockBothBucketsExist();
        mockBucketsWithDifferentEncryption();
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );
        assertTrue(exception.getMessage().contains("Invalid KMS key ARN format in destination bucket"));
    }

    // Failure case: Destination bucket kms key has invalid service in kms arn
    @Test
    void testValidateMigrationRequest_FailsDueInvalidServiceInKmsArn() {
        mockBothBucketsExist();
        mockDestBucketWithInvalidServiceInKmsArn();
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );
        assertTrue(exception.getMessage().contains("Invalid KMS key ARN format in destination bucket"));
    }

    // Failure case: Destination bucket kms key has invalid resource in kms arn
    @Test
    void testValidateMigrationRequest_FailsDueInvalidResourceInKmsArn() {
        mockBothBucketsExist();
        mockDestBucketWithInvalidResourceInKmsArn();
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );
        assertTrue(exception.getMessage().contains("Invalid KMS key ARN format in destination bucket"));
    }

    // Success case
    @Test
    void testValidateMigrationRequest_success() {
        mockBothBucketsExist();
        mockDestBucketWithValidKmsMasterKeyID();
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );
        assertFalse(exception.getMessage().contains("Invalid KMS key ARN format in destination bucket"));
    }

    @Test
    void testValidateMigrationRequest_SkipDueToTAInUnSupportedRegion() {
        mockBothBucketsExist();
        mockDefaultEncryptionCheck(true);
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(false, "eu-south-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        // Verify s3Client was never called for unsupported region
        verify(sourceS3Client, never()).getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class));
    }

    @Test
    void testValidateMigrationRequest_skipBucketOwnershipFlagEnabled_Success() {
        setWorkflowRuntimeConfig(true);
        mockBothBucketsExist();
        mockDefaultEncryptionCheck(true);
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));

        // Verify getBucketOwnershipControls is never called because skip flag is true
        verify(sourceS3Client, never()).getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class));
        verify(destS3Client, never()).getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class));
    }

    @Test
    void testValidateMigrationRequest_runtimeConfigNullStillCallsBucketOwnershipControls() {
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));
        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testValidateMigrationRequest_skipBucketOwnershipFlagNull_StillCallsBucketOwnershipControls() {
        RuntimeConfig config = RuntimeConfig.builder().build();
        mockWorkflow.setRuntimeConfig(config);

        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));

        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testValidateMigrationRequest_skipBucketOwnershipFlagFalse_StillCallsBucketOwnershipControls() {
        setWorkflowRuntimeConfig(false);
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));

        // Verify bucket ownership controls are called when flag is explicitly false
        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testValidateMigrationRequest_BucketOwnerEnforcedEnabled_Success() {
        setWorkflowRuntimeConfig(false);
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));
        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                        .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testValidateMigrationRequest_OwnershipControlsNotFoundError_WriteAccess_Failure() {
        AwsServiceException s3Exception = S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("OwnershipControlsNotFoundError")
                        .build())
                .statusCode(404)
                .build();
        GetBucketAclResponse bucketAclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder()
                        .id("123").build())
                .grants(Grant.builder()
                        .grantee(Grantee.builder()
                                .id("456")
                                .type(Type.CANONICAL_USER).build())
                        .permission(Permission.WRITE)
                        .build())
                .build();
        setWorkflowRuntimeConfig(false);
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        when(sourceS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class))).thenThrow(s3Exception);
        when(destS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class))).thenThrow(s3Exception);
        when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);
        when(destS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);

        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));

        assertTrue(exception.getMessage().contains("Buckets with custom ACLs with write access granted to external parties are not supported"));
        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testValidateMigrationRequest_OwnershipControlsNotFoundError_ReadOnlyAccess_Success() {
        AwsServiceException s3Exception = S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("OwnershipControlsNotFoundError")
                        .build())
                .statusCode(404)
                .build();
        GetBucketAclResponse bucketAclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder()
                        .id("123").build())
                .grants(Grant.builder()
                        .grantee(Grantee.builder()
                                .id("456")
                                .type(Type.CANONICAL_USER).build())
                        .permission(Permission.READ)
                        .build())
                .build();
        setWorkflowRuntimeConfig(false);
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        when(sourceS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class))).thenThrow(s3Exception);
        when(destS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class))).thenThrow(s3Exception);
        when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);
        when(destS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));

        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testValidateMigrationRequest_BucketOwnerPreferred_WriteAccessEnabled_Failure() {
        GetBucketAclResponse bucketAclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder()
                        .id("123").build())
                .grants(Grant.builder()
                        .grantee(Grantee.builder()
                                .id("456")
                                .type(Type.CANONICAL_USER).build())
                        .permission(Permission.WRITE)
                        .build())
                .build();
        setWorkflowRuntimeConfig(false);
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_PREFERRED);
        when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);
        when(destS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);

        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));

        assertTrue(exception.getMessage().contains("Buckets with custom ACLs with write access granted to external parties are not supported"));
        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testValidateMigrationRequest_BucketOwnerPreferred_ReadOnlyAccessEnabled_Success() {
        GetBucketAclResponse bucketAclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder()
                        .id("123").build())
                .grants(Grant.builder()
                        .grantee(Grantee.builder()
                                .id("456")
                                .type(Type.CANONICAL_USER).build())
                        .permission(Permission.READ)
                        .build())
                .build();
        setWorkflowRuntimeConfig(false);
        mockBothBucketsExist();
        mockBucketsWithSameEncryption();
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_PREFERRED);
        when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);
        when(destS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(bucketAclResponse);

        assertDoesNotThrow(() -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient));

        verify(sourceS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("source-bucket")
                .build());
        verify(destS3Client, times(1)).getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder()
                .bucket("dest-bucket")
                .build());
    }

    @Test
    void testAssertObjectOwnership_CanonicalUser() {
        S3Bucket s3Bucket = S3Bucket.builder()
                .arn("arn:aws:s3:::amzn-s3-demo-bucket")
                .s3Client(sourceS3Client)
                .build();

        when(sourceS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class)))
                .thenReturn(GetBucketOwnershipControlsResponse.builder()
                        .ownershipControls(OwnershipControls.builder()
                                .rules(OwnershipControlsRule.builder()
                                        .objectOwnership(ObjectOwnership.OBJECT_WRITER)
                                        .build())
                                .build())
                        .build());
        when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class)))
                .thenReturn(GetBucketAclResponse.builder()
                        .owner(Owner.builder()
                                .id("123")
                                .build())
                        .grants(Grant.builder()
                                .grantee(Grantee.builder()
                                        .id("456")
                                        .uri(null)
                                        .type(Type.CANONICAL_USER)
                                        .build())
                                .permission(Permission.FULL_CONTROL)
                                .build())
                        .build());

        boolean response = assertDoesNotThrow(() -> validator.assertObjectOwnership(s3Bucket));

        assertFalse(response);
        verify(sourceS3Client, times(1)).getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class));
        verify(sourceS3Client, times(1)).getBucketAcl(any(GetBucketAclRequest.class));
    }

    @Test
    void testAssertObjectOwnership_Group() {
        S3Bucket s3Bucket = S3Bucket.builder()
                .arn("arn:aws:s3:::amzn-s3-demo-bucket")
                .s3Client(sourceS3Client)
                .build();

        when(sourceS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class)))
                .thenReturn(GetBucketOwnershipControlsResponse.builder()
                        .ownershipControls(OwnershipControls.builder()
                                .rules(OwnershipControlsRule.builder()
                                        .objectOwnership(ObjectOwnership.OBJECT_WRITER)
                                        .build())
                                .build())
                        .build());
        when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class)))
                .thenReturn(GetBucketAclResponse.builder()
                        .owner(Owner.builder()
                                .id("123")
                                .build())
                        .grants(Grant.builder()
                                .grantee(Grantee.builder()
                                        .id(null)
                                        .uri("testUri")
                                        .type(Type.GROUP)
                                        .build())
                                .permission(Permission.FULL_CONTROL)
                                .build())
                        .build());

        boolean response = assertDoesNotThrow(() -> validator.assertObjectOwnership(s3Bucket));

        assertFalse(response);
        verify(sourceS3Client, times(1)).getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class));
        verify(sourceS3Client, times(1)).getBucketAcl(any(GetBucketAclRequest.class));
    }

    // Helper Method to Mock Runtime Config
    private void setWorkflowRuntimeConfig(boolean flag) {
        mockWorkflow.setRuntimeConfig(RuntimeConfig.builder().withSkipBucketOwnershipValidationAndCopy(flag).build());
    }

    // Helper Methods to Mock AWS S3 Calls
    private void mockDefaultEncryptionCheck(boolean enabled) {
        if (enabled) {
            GetBucketEncryptionResponse encryptionResponse = GetBucketEncryptionResponse.builder()
                    .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                            .rules(ServerSideEncryptionRule.builder()
                                    .applyServerSideEncryptionByDefault(
                                            ServerSideEncryptionByDefault.builder()
                                                    .sseAlgorithm(ServerSideEncryption.AES256)
                                                    .build())
                                    .build())
                            .build())
                    .build();
            when(sourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionResponse);
            when(destS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionResponse);
        } else {
            when(sourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                    .thenThrow(S3Exception.builder().awsErrorDetails(AwsErrorDetails.builder()
                            .errorCode("ServerSideEncryptionConfigurationNotFoundError")
                            .build()).build());
        }
    }

    // Mocks the behavior of the S3Client to return a dest bucket with a valid KmsMasterKeyID
    private void mockDestBucketWithValidKmsMasterKeyID() {
        GetBucketEncryptionResponse encryptionSSE_S3 = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AES256)
                                                .build())
                                .build())
                        .build())
                .build();

        GetBucketEncryptionResponse encryptionKMS = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                .kmsMasterKeyID("arn:aws:kms:eu-south-2:111111111111:key/111-111-111")
                                                .build())
                                .build())
                        .build())
                .build();
        when(sourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionSSE_S3);
        when(destS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionKMS);
    }


    // Mocks the behavior of the S3Client to return different encryption types for source and destination buckets.
    private void mockBucketsWithDifferentEncryption() {
            GetBucketEncryptionResponse encryptionSSE_S3 = GetBucketEncryptionResponse.builder()
                    .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                            .rules(ServerSideEncryptionRule.builder()
                                    .applyServerSideEncryptionByDefault(
                                            ServerSideEncryptionByDefault.builder()
                                                    .sseAlgorithm(ServerSideEncryption.AES256)
                                                    .build())
                                    .build())
                            .build())
                    .build();

        GetBucketEncryptionResponse encryptionKMS = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                .kmsMasterKeyID("123-123-123")
                                                .build())
                                .build())
                        .build())
                .build();
            when(sourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionSSE_S3);
            when(destS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionKMS);
    }

    // Mocks the behavior of the S3Client to return invalid service in kms arn
    private void mockDestBucketWithInvalidServiceInKmsArn() {
        GetBucketEncryptionResponse encryptionSSE_S3 = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AES256)
                                                .build())
                                .build())
                        .build())
                .build();

        GetBucketEncryptionResponse encryptionKMS = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                .kmsMasterKeyID("arn:aws:s3:eu-south-2:111111111111:key/111-111-111")//wrong service (s3)
                                                .build())
                                .build())
                        .build())
                .build();
        when(sourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionSSE_S3);
        when(destS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionKMS);
    }
    // Mocks the behavior of the S3Client to return invalid resource in kms arn
    private void mockDestBucketWithInvalidResourceInKmsArn() {
        GetBucketEncryptionResponse encryptionSSE_S3 = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AES256)
                                                .build())
                                .build())
                        .build())
                .build();

        GetBucketEncryptionResponse encryptionKMS = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                        .rules(ServerSideEncryptionRule.builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                .kmsMasterKeyID("arn:aws:kms:eu-south-2:111111111111:keyy/111-111-111")// wrong key (keyy)
                                                .build())
                                .build())
                        .build())
                .build();
        when(sourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionSSE_S3);
        when(destS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionKMS);
    }

    // Mocks the behavior of the S3Client to return the same encryption type for both source and destination buckets.
    private void mockBucketsWithSameEncryption() {
            GetBucketEncryptionResponse encryptionResponse = GetBucketEncryptionResponse.builder()
                    .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                            .rules(ServerSideEncryptionRule.builder()
                                    .applyServerSideEncryptionByDefault(
                                            ServerSideEncryptionByDefault.builder()
                                                    .sseAlgorithm(ServerSideEncryption.AES256)
                                                    .build())
                                    .build())
                            .build())
                    .build();
            when(sourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionResponse);
            when(destS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(encryptionResponse);
        }

    // Mocks the behavior of the S3Client when both source and destination buckets exist
    private void mockBothBucketsExist(){
        reset(sourceS3Client, destS3Client);
        HeadBucketResponse headBucketResponse = HeadBucketResponse.builder().build();

        when(sourceS3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headBucketResponse);
        when(destS3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(headBucketResponse);
    }

    // Mocks the behavior of the sourceS3Client when the source bucket does not exist
    private void mockSourceBucketDoesNotExist() {
        doThrow(S3Exception.class).when(sourceS3Client).headBucket(any(Consumer.class));
    }

    // Mocks the behavior of the destS3Client when the destination bucket does not exist
    private void mockDestinationBucketDoesNotExist() {
        doThrow(S3Exception.class).when(destS3Client).headBucket(any(Consumer.class));
    }

    // Mocks sourceS3Client to throw exceptions when attempting to access the source bucket
    private void mockSourceBucketDoesNotExistWithException() {
        doThrow(RuntimeException.class).when(sourceS3Client).headBucket(any(Consumer.class));
    }

    private void mockNoAclCheck(boolean noAcls) {
        if (noAcls) {
            GetBucketAclResponse aclResponse = GetBucketAclResponse.builder()
                    .grants(Collections.emptyList()) // No ACLs
                    .build();
            when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(aclResponse);
        } else {
            GetBucketAclResponse aclResponse = GetBucketAclResponse.builder()
                    .grants(Grant.builder().permission(Permission.FULL_CONTROL).build(),
                            Grant.builder().grantee(Grantee.builder().type(Type.CANONICAL_USER).build()).permission(Permission.READ).build()) // ACLs present
                    .build();
            when(sourceS3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(aclResponse);
        }
    }

    private void mockBucketOwnershipControls(ObjectOwnership objectOwnership) {
        GetBucketOwnershipControlsResponse ownershipControlsResponse = GetBucketOwnershipControlsResponse.builder()
                .ownershipControls(OwnershipControls.builder()
                        .rules(Collections.singletonList(OwnershipControlsRule.builder()
                                .objectOwnership(objectOwnership)
                                .build()))
                        .build())
                .build();
        when(sourceS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class))).thenReturn(ownershipControlsResponse);
        when(destS3Client.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class))).thenReturn(ownershipControlsResponse);
    }

    private void mockNoAccessPointCheck(boolean noAccessPoints) {
        if (!noAccessPoints) {
            ListAccessPointsResponse accessPointsResponse = ListAccessPointsResponse.builder()
                    .accessPointList(AccessPoint.builder().build()) // Access points present
                    .build();
            when(s3ControlClient.listAccessPoints(any(ListAccessPointsRequest.class)))
                    .thenReturn(accessPointsResponse);
        } else {
            ListAccessPointsResponse accessPointsResponse = ListAccessPointsResponse.builder()
                    .accessPointList(Collections.emptyList()) // No access points
                    .build();
            when(s3ControlClient.listAccessPoints(any(ListAccessPointsRequest.class)))
                    .thenReturn(accessPointsResponse);
        }
    }

    private void mockNoTransferAcceleratorCheck(boolean noTransferAccelerationEnabled, String sourceBucketRegion) {
        // Mock region-based check

        if (unSupportedRegions.contains(sourceBucketRegion)) {
            return; // Test should expect true without calling S3
        }

        if (!noTransferAccelerationEnabled) {
            GetBucketAccelerateConfigurationResponse accelerateConfigurationResponse = GetBucketAccelerateConfigurationResponse.builder()
                    .status(BucketAccelerateStatus.ENABLED) // Transfer acceleration enabled
                    .build();
            when(sourceS3Client.getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class)))
                    .thenReturn(accelerateConfigurationResponse);
        } else {
            GetBucketAccelerateConfigurationResponse accelerateConfigurationResponse = GetBucketAccelerateConfigurationResponse.builder()
                    .status(BucketAccelerateStatus.SUSPENDED) // Transfer acceleration disabled
                    .build();
            when(sourceS3Client.getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class)))
                    .thenReturn(accelerateConfigurationResponse);
        }

        // Mock behavior when AWS returns an error (e.g., "BucketAccelerateConfigurationNotFoundError")
        doThrow(S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("BucketAccelerateConfigurationNotFoundError").build())
                .build())
                .when(sourceS3Client).getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class));
    }


    private void mockNoEventNotificationCheck(boolean noEventNotifications) {
        if (!noEventNotifications) {
            GetBucketNotificationConfigurationResponse notificationResponse = GetBucketNotificationConfigurationResponse.builder()
                    .eventBridgeConfiguration(EventBridgeConfiguration.builder().build()) // Event notifications present
                    .build();
            when(sourceS3Client.getBucketNotificationConfiguration(any(GetBucketNotificationConfigurationRequest.class)))
                    .thenReturn(notificationResponse);
        } else {
            GetBucketNotificationConfigurationResponse notificationResponse = GetBucketNotificationConfigurationResponse.builder()
                    .build(); // No event notifications
            when(sourceS3Client.getBucketNotificationConfiguration(any(GetBucketNotificationConfigurationRequest.class)))
                    .thenReturn(notificationResponse);
        }
    }


    @Test
    void testValidateMigrationRequest_MultipleFailures() {
        mockDestinationBucketDoesNotExist();
        mockDefaultEncryptionCheck(false);
        mockNoAclCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoAccessPointCheck(false);
        mockNoTransferAcceleratorCheck(false, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        AWSServiceException exception = assertThrows(
                AWSServiceException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );

        String errorMessage = exception.getMessage();
        assertFalse(errorMessage.contains("Buckets with life cycle policies are not supported for migration"));
    }

    @Test
    void testValidateMigrationRequest_WhenIsBucketExistsThrowsException() {
        // Given
        mockSourceBucketDoesNotExistWithException();
        mockBucketsWithSameEncryption();
        mockNoAclCheck(true);
        mockNoAccessPointCheck(true);
        mockNoEventNotificationCheck(true);
        mockNoTransferAcceleratorCheck(true, "us-east-1");
        mockBucketOwnershipControls(ObjectOwnership.BUCKET_OWNER_ENFORCED);

        // When/Then
        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> validator.validateMigrationRequest(mockWorkflow, sourceS3Client, destS3Client, s3ControlClient)
        );

        assertTrue(exception.getMessage().contains("Source bucket must exist before start migration"));
    }
}