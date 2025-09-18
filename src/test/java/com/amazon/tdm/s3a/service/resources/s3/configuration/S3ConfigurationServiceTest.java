package com.amazon.tdm.s3a.service.resources.s3.configuration;

import com.amazon.tdm.s3a.persistence.model.RuntimeConfig;
import com.amazon.tdm.s3a.service.resources.s3.bucket.S3CreateBucketService;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetBucketAccelerateConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketCorsResponse;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketLoggingRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLoggingResponse;
import software.amazon.awssdk.services.s3.model.GetBucketRequestPaymentResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetBucketWebsiteResponse;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LoggingEnabled;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.PutBucketLoggingRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLoggingResponse;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutBucketWebsiteRequest;
import software.amazon.awssdk.services.s3.model.GetBucketRequestPaymentRequest;
import software.amazon.awssdk.services.s3.model.PutBucketRequestPaymentRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAccelerateConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketWebsiteRequest;
import software.amazon.awssdk.services.s3.model.GetBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetObjectLockConfigurationResponse;
import software.amazon.awssdk.services.s3.model.PutObjectLockConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsResponse;
import software.amazon.awssdk.services.s3.model.PutBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.model.ObjectOwnership;
import software.amazon.awssdk.services.s3.model.Owner;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.PutBucketAclRequest;
import software.amazon.awssdk.services.s3.model.Grantee;
import software.amazon.awssdk.services.s3.model.OwnershipControls;
import software.amazon.awssdk.services.s3.model.OwnershipControlsRule;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Payer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.mockito.ArgumentCaptor;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.util.Map;
import java.util.List;


class S3ConfigurationServiceTest {

    @Mock
    private S3Client s3SourceClient;

    @Mock
    private S3Client s3DestClient;

    @Mock
    private KmsClient kmsClient;

    private WorkFlowModel workflowModel;

    @Mock
    private S3CreateBucketService s3CreateBucketService;

    private final S3ConfigurationService s3ConfigurationService = new S3ConfigurationService();
    private static final String VALID_KMS_KEY = "arn:aws:kms:region:account:key/key-id";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock WorkflowModel
        workflowModel = new WorkFlowModel();
        workflowModel.setSourceBucketARN("arn:aws:s3:::source-bucket");
        workflowModel.setDestBucketARN("arn:aws:s3:::destination-bucket");
        workflowModel.setDestRoleARN("arn:aws:iam::123456789012:role/DestRole");
        workflowModel.setDestAccountNumber("1234567890");
        workflowModel.setDestRegion("us-west-2");
    }

    @Test
    void testSaveConfiguration_success() {

        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        GetBucketVersioningRequest versioningRequest = GetBucketVersioningRequest.builder()
                .bucket("source-bucket")
                .build();

        GetBucketRequestPaymentRequest paymentRequest = GetBucketRequestPaymentRequest.builder()
                .bucket("source-bucket")
                .build();

        GetBucketWebsiteRequest websiteRequest = GetBucketWebsiteRequest.builder()
                .bucket("source-bucket")
                .build();

        GetBucketCorsRequest corsRequest = GetBucketCorsRequest.builder()
                .bucket("source-bucket")
                .build();

        GetObjectLockConfigurationRequest objectLockConfigurationRequest = GetObjectLockConfigurationRequest.builder()
                .bucket("source-bucket")
                .build();

        GetBucketLifecycleConfigurationRequest bucketLifecycleConfigurationRequest = GetBucketLifecycleConfigurationRequest.builder()
                .bucket("source-bucket")
                .build();

        when(s3SourceClient.getBucketVersioning(eq(versioningRequest))).thenReturn(GetBucketVersioningResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketRequestPayment(eq(paymentRequest))).thenReturn(GetBucketRequestPaymentResponse.builder().payer("BucketOwner").build());
        when(s3SourceClient.getBucketWebsite(eq(websiteRequest))).thenReturn(GetBucketWebsiteResponse.builder().build());
        when(s3SourceClient.getBucketCors(eq(corsRequest))).thenReturn(GetBucketCorsResponse.builder().build());
        when(s3SourceClient.getObjectLockConfiguration(eq(objectLockConfigurationRequest))).thenReturn(GetObjectLockConfigurationResponse.builder().build());
        when(s3SourceClient.getBucketLifecycleConfiguration(eq(bucketLifecycleConfigurationRequest))).thenReturn(GetBucketLifecycleConfigurationResponse.builder().rules(LifecycleRule.builder().build()).build());

        s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName, destBucketName);

        ArgumentCaptor<PutBucketVersioningRequest> versioningRequestCaptor = ArgumentCaptor.forClass(PutBucketVersioningRequest.class);
        ArgumentCaptor<PutBucketRequestPaymentRequest> paymentRequestCaptor = ArgumentCaptor.forClass(PutBucketRequestPaymentRequest.class);
        ArgumentCaptor<PutBucketWebsiteRequest> websiteRequestCaptor = ArgumentCaptor.forClass(PutBucketWebsiteRequest.class);
        ArgumentCaptor<PutBucketCorsRequest> corsRequestCaptor = ArgumentCaptor.forClass(PutBucketCorsRequest.class);
        ArgumentCaptor<PutObjectLockConfigurationRequest> objectLockRequestArgumentCaptor = ArgumentCaptor.forClass(PutObjectLockConfigurationRequest.class);
        ArgumentCaptor<PutBucketLifecycleConfigurationRequest> bucketLifecycleConfigurationRequestArgumentCaptor = ArgumentCaptor.forClass(PutBucketLifecycleConfigurationRequest.class);

        verify(s3DestClient, times(1)).putBucketVersioning(versioningRequestCaptor.capture());
        verify(s3DestClient, times(1)).putBucketRequestPayment(paymentRequestCaptor.capture());
        verify(s3DestClient, times(1)).putBucketWebsite(websiteRequestCaptor.capture());
        verify(s3DestClient, times(1)).putBucketCors(corsRequestCaptor.capture());
        verify(s3DestClient, times(1)).putObjectLockConfiguration(objectLockRequestArgumentCaptor.capture());
        verify(s3DestClient, times(1)).putBucketLifecycleConfiguration(bucketLifecycleConfigurationRequestArgumentCaptor.capture());

        PutBucketVersioningRequest putVersioningRequest = versioningRequestCaptor.getValue();
        assertEquals(destBucketName, putVersioningRequest.bucket());
        assertEquals(BucketVersioningStatus.ENABLED, putVersioningRequest.versioningConfiguration().status());

        PutBucketRequestPaymentRequest putPaymentRequest = paymentRequestCaptor.getValue();
        assertEquals(destBucketName, putPaymentRequest.bucket());
        assertEquals(Payer.BUCKET_OWNER, putPaymentRequest.requestPaymentConfiguration().payer());

        PutBucketWebsiteRequest putWebsiteRequest = websiteRequestCaptor.getValue();
        assertEquals(destBucketName, putWebsiteRequest.bucket());

        PutBucketCorsRequest putCorsRequest = corsRequestCaptor.getValue();
        assertEquals(destBucketName, putCorsRequest.bucket());

        PutObjectLockConfigurationRequest putObjectLockRequest = objectLockRequestArgumentCaptor.getValue();
        assertEquals(destBucketName, putObjectLockRequest.bucket());

        PutBucketLifecycleConfigurationRequest putBucketLifecycleConfigurationRequest = bucketLifecycleConfigurationRequestArgumentCaptor.getValue();
        assertEquals(destBucketName, putBucketLifecycleConfigurationRequest.bucket());

    }

    @Test
    void testSaveConfiguration_s3ExceptionThrown() {

        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal server error").build());
        when(s3SourceClient.getBucketRequestPayment(any(GetBucketRequestPaymentRequest.class)))
                .thenReturn(GetBucketRequestPaymentResponse.builder().payer("BucketOwner").build());
        when(s3SourceClient.getBucketWebsite(any(GetBucketWebsiteRequest.class)))
                .thenReturn(GetBucketWebsiteResponse.builder().build());
        when(s3SourceClient.getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class)))
                .thenReturn(GetBucketAccelerateConfigurationResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketCors(any(GetBucketCorsRequest.class)))
                .thenReturn(GetBucketCorsResponse.builder().build());
        when(s3SourceClient.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(GetBucketEncryptionResponse.builder().build());
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(GetBucketLifecycleConfigurationResponse.builder().build());


        S3Exception thrownException = assertThrows(S3Exception.class, () -> {
            s3ConfigurationService.saveConfiguration(s3SourceClient, s3SourceClient, sourceBucketName, destBucketName);
        });

        assertEquals(500, thrownException.statusCode());
        assertEquals("Internal server error", thrownException.getMessage());
    }

    @Test
    void testSaveConfiguration_otherExceptionThrown() {
        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketRequestPayment(any(GetBucketRequestPaymentRequest.class)))
                .thenReturn(GetBucketRequestPaymentResponse.builder().payer("BucketOwner").build());
        when(s3SourceClient.getBucketWebsite(any(GetBucketWebsiteRequest.class)))
                .thenReturn(GetBucketWebsiteResponse.builder().build());
        when(s3SourceClient.getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class)))
                .thenReturn(GetBucketAccelerateConfigurationResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketCors(any(GetBucketCorsRequest.class)))
                .thenReturn(GetBucketCorsResponse.builder().build());
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));
        when(s3SourceClient.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(GetBucketEncryptionResponse.builder().build());

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName, destBucketName);
        });

        assertTrue(thrownException.getMessage().contains("Unexpected error"));
    }

    @Test
    void testSaveConfiguration_awsServiceExceptionThrown() {
        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketRequestPayment(any(GetBucketRequestPaymentRequest.class)))
                .thenReturn(GetBucketRequestPaymentResponse.builder().payer("BucketOwner").build());
        when(s3SourceClient.getBucketWebsite(any(GetBucketWebsiteRequest.class)))
                .thenReturn(GetBucketWebsiteResponse.builder().build());
        when(s3SourceClient.getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class)))
                .thenReturn(GetBucketAccelerateConfigurationResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketCors(any(GetBucketCorsRequest.class)))
                .thenReturn(GetBucketCorsResponse.builder().build());
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenThrow(AwsServiceException.builder().message("AWS Service error").build());
        when(s3SourceClient.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(GetBucketEncryptionResponse.builder().build());

        AwsServiceException thrownException = assertThrows(AwsServiceException.class, () -> {
            s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName, destBucketName);
        });

        assertEquals("AWS Service error", thrownException.getMessage());
    }

    @Test

    void testSaveConfiguration_NoLifecycleRules_Success() {
        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(GetBucketLifecycleConfigurationResponse.builder().build());

        assertDoesNotThrow(() -> s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName, destBucketName));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3DestClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testSaveConfiguration_NoLifecycleConfigurationResponse_Success() {
        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(null);

        assertDoesNotThrow(() -> s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName, destBucketName));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3DestClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testSaveConfiguration_LifecycleRules_S3Exception() {
        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenThrow(S3Exception.class);

        assertThrows(S3Exception.class, () -> s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName, destBucketName));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3DestClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testSaveConfiguration_LifecycleRules_S3Exception_NotFound() {
        String sourceBucketName = "source-bucket";
        String destBucketName = "dest-bucket";

        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertDoesNotThrow(() -> s3ConfigurationService.saveConfiguration(s3SourceClient, s3DestClient, sourceBucketName, destBucketName));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3DestClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testGetBucketConfiguration_success_KMS_encryption() {
        String sourceBucketName = "source-bucket";

        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketRequestPayment(any(GetBucketRequestPaymentRequest.class)))
                .thenReturn(GetBucketRequestPaymentResponse.builder().payer("BucketOwner").build());
        when(s3SourceClient.getBucketWebsite(any(GetBucketWebsiteRequest.class)))
                .thenReturn(GetBucketWebsiteResponse.builder().build());
        when(s3SourceClient.getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class)))
                .thenReturn(GetBucketAccelerateConfigurationResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketCors(any(GetBucketCorsRequest.class)))
                .thenReturn(GetBucketCorsResponse.builder().corsRules(List.of()).build());
        when(s3SourceClient.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(GetBucketEncryptionResponse.builder().build());

        GetBucketEncryptionResponse getBucketEncryptionResponse = getGetBucketKmsEncryptionResponse();

        when(s3SourceClient.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(getBucketEncryptionResponse);

        GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder()
                .policy("{\"Version\":\"2012-10-17\",\"Statement\":"
                        + "[{\"Sid\":\"Allow access\"," +
                        "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"12345678\"},"
                        + "\"Action\":\"kms:*\",\"Resource\":\"*\"}]}")
                .build();

        when(kmsClient.getKeyPolicy(any(GetKeyPolicyRequest.class)))
                .thenReturn(getKeyPolicyResponse);

        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(GetBucketLifecycleConfigurationResponse.builder().build());

        Map<String, String> result = s3ConfigurationService.getBucketConfiguration(s3SourceClient,kmsClient, sourceBucketName);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        assertEquals("{\"status\":\"Enabled\"}", result.get("bucketVersioning"));
        assertEquals("{\"payer\":\"BucketOwner\"}", result.get("bucketRequestPayment"));
        assertTrue(result.containsKey("bucketCors"), "Bucket CORS configuration should be present");
        assertTrue(result.containsKey("bucketWebsite"), "Bucket website configuration should be present");
        assertTrue(result.containsKey("bucketLifecycle"), "Bucket lifecycle configuration should be present");
        assertTrue(result.containsKey("bucketEncryption"), "Bucket encryption configuration should be present");
        assertTrue(result.containsKey("bucketKmsKeyPolicy"), "Bucket kms key policy configuration should be present");
    }

    private static GetBucketEncryptionResponse getGetBucketKmsEncryptionResponse() {
        GetBucketEncryptionResponse getBucketEncryptionResponse = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration
                        .builder()
                        .rules(ServerSideEncryptionRule
                                .builder()
                                .applyServerSideEncryptionByDefault(
                                ServerSideEncryptionByDefault.builder()
                                        .kmsMasterKeyID(VALID_KMS_KEY)
                                        .sseAlgorithm("aws:kms").build()
                        ).build()
                ).build()
        ).build();
        return getBucketEncryptionResponse;
    }

    @Test
    void testGetBucketConfiguration_success_S3_encryption() {
        String sourceBucketName = "source-bucket";

        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketRequestPayment(any(GetBucketRequestPaymentRequest.class)))
                .thenReturn(GetBucketRequestPaymentResponse.builder().payer("BucketOwner").build());
        when(s3SourceClient.getBucketWebsite(any(GetBucketWebsiteRequest.class)))
                .thenReturn(GetBucketWebsiteResponse.builder().build());
        when(s3SourceClient.getBucketAccelerateConfiguration(any(GetBucketAccelerateConfigurationRequest.class)))
                .thenReturn(GetBucketAccelerateConfigurationResponse.builder().status("Enabled").build());
        when(s3SourceClient.getBucketCors(any(GetBucketCorsRequest.class)))
                .thenReturn(GetBucketCorsResponse.builder().corsRules(List.of()).build());
        when(s3SourceClient.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(GetBucketEncryptionResponse.builder().build());

        // build and SSE-S3 bucket encryption response
        GetBucketEncryptionResponse getBucketEncryptionResponse = getGetBucketS3EncryptionResponse();

        when(s3SourceClient.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(getBucketEncryptionResponse);

        GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder()
                .policy("{\"Version\":\"2012-10-17\",\"Statement\":"
                        + "[{\"Sid\":\"Allow access\"," +
                        "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"12345678\"},"
                        + "\"Action\":\"kms:*\",\"Resource\":\"*\"}]}")
                .build();

        when(kmsClient.getKeyPolicy(any(GetKeyPolicyRequest.class)))
                .thenReturn(getKeyPolicyResponse);

        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(GetBucketLifecycleConfigurationResponse.builder().build());

        Map<String, String> result = s3ConfigurationService.getBucketConfiguration(s3SourceClient,kmsClient, sourceBucketName);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        assertEquals("{\"status\":\"Enabled\"}", result.get("bucketVersioning"));
        assertEquals("{\"payer\":\"BucketOwner\"}", result.get("bucketRequestPayment"));
        assertTrue(result.containsKey("bucketCors"), "Bucket CORS configuration should be present");
        assertTrue(result.containsKey("bucketWebsite"), "Bucket website configuration should be present");
        assertTrue(result.containsKey("bucketLifecycle"), "Bucket lifecycle configuration should be present");
        assertTrue(result.containsKey("bucketEncryption"), "Bucket encryption configuration should be present");
        assertFalse(result.containsKey("bucketKmsKeyPolicy"), "Bucket kms key policy configuration should not be present");
    }

    private static GetBucketEncryptionResponse getGetBucketS3EncryptionResponse() {
        return GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration
                        .builder()
                        .rules(ServerSideEncryptionRule
                                .builder()
                                .applyServerSideEncryptionByDefault(
                                        ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm("AES256").build()
                                ).build()
                        ).build()
                ).build();
    }

    @Test
    void testGetBucketConfiguration_s3ExceptionThrown() {
        String sourceBucketName = "source-bucket";

        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());

        S3Exception thrownException = assertThrows(S3Exception.class, () -> {
            s3ConfigurationService.getBucketConfiguration(s3SourceClient, kmsClient, sourceBucketName);
        });

        assertEquals(403, thrownException.statusCode());
    }

    @Test
    void testGetBucketConfiguration_awsServiceExceptionThrown() {
        String sourceBucketName = "source-bucket";

        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenThrow(AwsServiceException.builder().message("Service Unavailable").build());

        AwsServiceException thrownException = assertThrows(AwsServiceException.class, () -> {
            s3ConfigurationService.getBucketConfiguration(s3SourceClient, kmsClient, sourceBucketName);
        });

        assertEquals("Service Unavailable", thrownException.getMessage());
    }

    @Test
    void testGetBucketConfiguration_otherExceptionThrown() {
        String sourceBucketName = "source-bucket";
        when(s3SourceClient.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            s3ConfigurationService.getBucketConfiguration(s3SourceClient,kmsClient, sourceBucketName);
        });

        assertTrue(thrownException.getMessage().contains("Unexpected error"));
    }

    @Test
    void testConfigureServerAccessLogging_WhenEnabled_Success() {
        // Mock server access logging enabled check
        when(s3SourceClient.getBucketLogging(any(GetBucketLoggingRequest.class)))
                .thenReturn(GetBucketLoggingResponse.builder()
                        .loggingEnabled(LoggingEnabled.builder().build())
                        .build());

        // Mock bucket creation
        when(s3CreateBucketService.generateBucketName("server-access-logging"))
                .thenReturn("server-access-logging-bucket");
        doNothing().when(s3CreateBucketService).createS3Bucket(
                eq(s3DestClient),
                eq(workflowModel.getDestRoleARN()),
                eq(workflowModel.getDestRegion()),
                eq("server-access-logging-bucket"));

        // Mock enabling server access logging
        when(s3DestClient.putBucketLogging(any(PutBucketLoggingRequest.class)))
                .thenReturn(PutBucketLoggingResponse.builder().build());

        // Create ArgumentCaptor to capture the actual request
        ArgumentCaptor<PutBucketLoggingRequest> loggingRequestCaptor = ArgumentCaptor.forClass(PutBucketLoggingRequest.class);

        // Call the method
        assertDoesNotThrow(() -> s3ConfigurationService.configureServerAccessLogging(
                s3SourceClient, s3DestClient, workflowModel, s3CreateBucketService));

        // Verify interactions
        verify(s3SourceClient, times(1)).getBucketLogging(any(GetBucketLoggingRequest.class));
        verify(s3CreateBucketService, times(1)).createS3Bucket(
                eq(s3DestClient),
                eq(workflowModel.getDestRoleARN()),
                eq(workflowModel.getDestRegion()),
                eq("server-access-logging-bucket"));
        verify(s3DestClient, times(1)).putBucketLogging(loggingRequestCaptor.capture());

        // Assert the captured request
        PutBucketLoggingRequest capturedRequest = loggingRequestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("destination-bucket", capturedRequest.bucket());
        assertNotNull(capturedRequest.bucketLoggingStatus());
        assertNotNull(capturedRequest.bucketLoggingStatus().loggingEnabled());
        assertEquals("server-access-logging-bucket", capturedRequest.bucketLoggingStatus().loggingEnabled().targetBucket());
        assertEquals("logs/", capturedRequest.bucketLoggingStatus().loggingEnabled().targetPrefix());
    }

    @Test
    void testConfigureServerAccessLogging_WhenEnabled_SetupFailure() {
        // Mock server access logging enabled check
        when(s3SourceClient.getBucketLogging(any(GetBucketLoggingRequest.class)))
                .thenReturn(GetBucketLoggingResponse.builder()
                        .loggingEnabled(LoggingEnabled.builder().build())
                        .build());

        // Mock bucket creation
        when(s3CreateBucketService.generateBucketName("server-access-logging"))
                .thenReturn("server-access-logging-bucket");
        doNothing().when(s3CreateBucketService).createS3Bucket(
                eq(s3DestClient),
                eq(workflowModel.getDestRoleARN()),
                eq(workflowModel.getDestRegion()),
                eq("server-access-logging-bucket"));

        // Simulate failure in enabling logging
        doThrow(S3Exception.builder().message("Failed to set logging").build())
                .when(s3DestClient).putBucketLogging(any(PutBucketLoggingRequest.class));

        // Call the method and expect exception
        S3Exception exception = assertThrows(S3Exception.class, () -> s3ConfigurationService.configureServerAccessLogging(
                s3SourceClient, s3DestClient, workflowModel, s3CreateBucketService));

        // Verify interactions
        verify(s3SourceClient, times(1)).getBucketLogging(any(GetBucketLoggingRequest.class));
        verify(s3CreateBucketService, times(1)).createS3Bucket(
                eq(s3DestClient),
                eq(workflowModel.getDestRoleARN()),
                eq(workflowModel.getDestRegion()),
                eq("server-access-logging-bucket"));
        verify(s3DestClient, times(1)).putBucketLogging(any(PutBucketLoggingRequest.class));

        // Assert exception message
        assertEquals("Failed to set logging", exception.getMessage());
    }

    @Test
    void testConfigureServerAccessLogging_WhenDisabledOnSource() {
        // Mock server access logging not enabled
        when(s3SourceClient.getBucketLogging(any(GetBucketLoggingRequest.class)))
                .thenReturn(GetBucketLoggingResponse.builder().build()); // No logging enabled

        // Call the method
        assertDoesNotThrow(() -> s3ConfigurationService.configureServerAccessLogging(
                s3SourceClient, s3DestClient, workflowModel, s3CreateBucketService));

        // Verify interactions
        verify(s3SourceClient, times(1)).getBucketLogging(any(GetBucketLoggingRequest.class));

        // Ensure no interactions with methods to create bucket or enable logging
        verify(s3CreateBucketService, times(0)).createS3Bucket(any(), any(), any(), any());
        verify(s3DestClient, times(0)).putBucketLogging(any(PutBucketLoggingRequest.class));
    }

    @Test
    void testConfigureBucketOwnershipControls_DefaultRules() {
        workflowModel.setRuntimeConfig(RuntimeConfig.builder()
                .skipBucketOwnershipValidationAndCopy(false)
                .build());

        GetBucketOwnershipControlsResponse ownershipResponse = GetBucketOwnershipControlsResponse.builder()
                .ownershipControls(OwnershipControls.builder()
                        .rules(OwnershipControlsRule.builder()
                                .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                                .build())
                        .build())
                .build();
        when(s3SourceClient.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class)))
                .thenReturn(ownershipResponse);

        // Act
        assertDoesNotThrow(() -> s3ConfigurationService.configureBucketOwnershipControls(
                s3SourceClient, s3DestClient, workflowModel));

        // Assert
        verify(s3DestClient, never()).putBucketOwnershipControls(any(PutBucketOwnershipControlsRequest.class));
        verify(s3DestClient, never()).putBucketAcl(any(PutBucketAclRequest.class));
    }

    @Test
    void testConfigureBucketOwnershipControls_NonDefaultRules() {
        workflowModel.setRuntimeConfig(RuntimeConfig.builder()
                .skipBucketOwnershipValidationAndCopy(false)
                .build());

        GetBucketOwnershipControlsResponse ownershipResponse = GetBucketOwnershipControlsResponse.builder()
                .ownershipControls(OwnershipControls.builder()
                        .rules(OwnershipControlsRule.builder()
                                .objectOwnership(ObjectOwnership.OBJECT_WRITER)
                                .build())
                        .build())
                .build();
        when(s3SourceClient.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class)))
                .thenReturn(ownershipResponse);

        GetBucketAclResponse sourceAclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder().id("source-owner-id").build())
                .grants(Grant.builder()
                        .grantee(Grantee.builder().id("grantee-id").build())
                        .permission("READ")
                        .build())
                .build();
        when(s3SourceClient.getBucketAcl(any(GetBucketAclRequest.class)))
                .thenReturn(sourceAclResponse);

        GetBucketAclResponse destAclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder().id("dest-owner-id").build())
                .build();
        when(s3DestClient.getBucketAcl(any(GetBucketAclRequest.class)))
                .thenReturn(destAclResponse);

        // Act
        assertDoesNotThrow(() -> s3ConfigurationService.configureBucketOwnershipControls(
                s3SourceClient, s3DestClient, workflowModel));

        // Assert
        verify(s3DestClient).putBucketOwnershipControls(any(PutBucketOwnershipControlsRequest.class));
        verify(s3DestClient).putBucketAcl(any(PutBucketAclRequest.class));
    }
    
    @Test
    void testConfigureBucketOwnershipControls_awsS3ExceptionThrown() {        
        when(s3SourceClient.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());
                
        S3Exception thrownException = assertThrows(S3Exception.class, () -> {
                s3ConfigurationService.configureBucketOwnershipControls(
                        s3SourceClient, s3DestClient, workflowModel);
        });

        assertEquals(403, thrownException.statusCode());
    }

    @Test
    void testModifyBucketLifecycleRulesStatus_Success() {
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(GetBucketLifecycleConfigurationResponse.builder()
                        .rules(LifecycleRule.builder().build())
                        .build());
        when(s3SourceClient.putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class)))
                .thenReturn(PutBucketLifecycleConfigurationResponse.builder()
                        .build());

        assertDoesNotThrow(() -> s3ConfigurationService.modifyBucketLifecycleRulesStatus(
                s3SourceClient, "test-bucket", ExpirationStatus.DISABLED));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3SourceClient, times(1)).putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                .bucket("test-bucket")
                .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                        .rules(LifecycleRule.builder().status(ExpirationStatus.DISABLED).build())
                        .build())
                .build());
    }

    @Test
    void testModifyBucketLifecycleRulesStatus_WhenNoRules_Success() {
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(GetBucketLifecycleConfigurationResponse.builder()
                        .build());

        assertDoesNotThrow(() -> s3ConfigurationService.modifyBucketLifecycleRulesStatus(
                s3SourceClient, "test-bucket", ExpirationStatus.DISABLED));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testModifyBucketLifecycleRulesStatus_NoLifecycleConfigurationResponse_Success() {
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenReturn(null);

        assertDoesNotThrow(() -> s3ConfigurationService.modifyBucketLifecycleRulesStatus(
                s3SourceClient, "test-bucket", ExpirationStatus.DISABLED));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testModifyBucketLifecycleRulesStatus_WhenNoLifecycleConfigurationFound_Success() {
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertDoesNotThrow(() -> s3ConfigurationService.modifyBucketLifecycleRulesStatus(
                s3SourceClient, "test-bucket", ExpirationStatus.DISABLED));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testModifyBucketLifecycleRulesStatus_ThrowsS3Exception() {
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("S3Exception").build());

        S3Exception s3Exception = assertThrows(S3Exception.class, () -> s3ConfigurationService.modifyBucketLifecycleRulesStatus(
                s3SourceClient, "test-bucket", ExpirationStatus.DISABLED));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
        assertEquals("S3Exception", s3Exception.getMessage());
    }

    @Test
    void testModifyBucketLifecycleRulesStatus_ThrowsAwsServiceException() {
        when(s3SourceClient.getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class)))
                .thenThrow(AwsServiceException.builder().statusCode(500).message("AwsServiceException").build());

        AwsServiceException awsServiceException = assertThrows(AwsServiceException.class, () -> s3ConfigurationService.modifyBucketLifecycleRulesStatus(
                s3SourceClient, "test-bucket", ExpirationStatus.DISABLED));

        verify(s3SourceClient, times(1)).getBucketLifecycleConfiguration(any(GetBucketLifecycleConfigurationRequest.class));
        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
        assertEquals("AwsServiceException", awsServiceException.getMessage());
    }

}