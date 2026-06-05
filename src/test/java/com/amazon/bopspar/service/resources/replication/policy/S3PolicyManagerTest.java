package com.amazon.bopspar.service.resources.replication.policy;

import com.amazon.bopspar.model.AWSServiceException;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.replication.bucket.S3Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3PolicyManagerTest {
    private static final String SOURCE_ROLE_ARN = "arn:aws:iam::123456789012:role/source-role";
    private static final String SOURCE_BUCKET_ARN = "arn:aws:s3:::source-bucket";
    private static final String DEST_REGION1 = "dest-region1";
    private static final String DEST_BUCKET_ARN = "arn:aws:s3:::target-bucket";
    private static final String SOURCE_ACCOUNT_NUMBER = "123456789012";
    private static final String DEST_ACCOUNT_NUMBER = "123456789012";
    private static final String KMS_KEY_ID = "arn:aws:kms:region:account:key/test-key-id";

    @Mock
    private S3Client mockDestS3Client;

    @Mock
    private KmsClient mockKmsClient;

    private WorkFlowModel workflow;

    @InjectMocks
    private S3PolicyManager s3PolicyManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mocking a valid workflow object
        workflow = new WorkFlowModel();
        workflow.setSourceRoleARN(SOURCE_ROLE_ARN);
        workflow.setSourceBucketARN(SOURCE_BUCKET_ARN);
        workflow.setDestBucketARN(DEST_BUCKET_ARN);
        workflow.setDestRegion(DEST_REGION1);
        workflow.setSourceAccountNumber(SOURCE_ACCOUNT_NUMBER);
        workflow.setDestAccountNumber(DEST_ACCOUNT_NUMBER);
    }

    @Test
    void testBuildCrossAccountPutBucketPolicyRequest_WhenS3ExceptionNot404_ThrowsOriginalException() {
        // Given
        String destBucketName = "dest-bucket";

        // Create S3Exception with non-404 status code
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .message("Access Denied")
            .statusCode(403)
            .build();

        when(mockDestS3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
            .thenThrow(s3Exception);

        // When/Then
        S3Exception thrown = assertThrows(S3Exception.class, () ->
            s3PolicyManager.buildCrossAccountPutBucketPolicyRequest(
                destBucketName,
                SOURCE_ACCOUNT_NUMBER,
                mockDestS3Client
            )
        );

        assertAll(
            "Verify non-404 S3Exception handling",
            () -> assertEquals(403, thrown.statusCode(), "Status code should be 403"),
            () -> assertEquals("Access Denied", thrown.getMessage(), "Error message should match"),
            () -> verify(mockDestS3Client).getBucketPolicy(any(GetBucketPolicyRequest.class))
        );
    }

    @Test
    void testUpdateDestKeyPolicy_WhenS3ExceptionOccurs() {
        // Given
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .build();

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenThrow(s3Exception);

        // When/Then
        AWSServiceException thrown = assertThrows(AWSServiceException.class, () ->
            s3PolicyManager.updateDestKeyPolicyIfBucketIsKmsEncrypted(
                workflow, mockDestS3Client, mockKmsClient)
        );

        // Verify exception details
        assertTrue(thrown.getMessage().contains("Failed to get bucket encryption configuration for bucket"));
    }

    @Test
    void testMergePolicyStatements_WhenExceptionOccurs() {
        // Given
        String invalidJson = "invalid-json-format"; // Invalid JSON to trigger exception

        GetBucketEncryptionResponse bucketKMSEncryptionResponse =
            getGetBucketKMSEncryptionResponse();

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(bucketKMSEncryptionResponse);

        GetKeyPolicyResponse mockKeyPolicyResponse = GetKeyPolicyResponse.builder()
            .policy(invalidJson)
            .policyName("default")
            .build();
        when(mockKmsClient.getKeyPolicy(any(GetKeyPolicyRequest.class)))
            .thenReturn(mockKeyPolicyResponse);

        // When/Then
        assertThrows(AWSServiceException.class, () ->
            s3PolicyManager.updateDestKeyPolicyIfBucketIsKmsEncrypted(
                workflow, mockDestS3Client, mockKmsClient)
        );
    }

    @Test
    void testWhenBucketNotKMSEncrypted_SkipPolicyUpdate() {
        // Given
        // Mock SSE-S3 encryption
        GetBucketEncryptionResponse bucketS3EncryptionResponse =
            getGetBucketS3EncryptionResponse();

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(bucketS3EncryptionResponse);

        // When
        s3PolicyManager.updateDestKeyPolicyIfBucketIsKmsEncrypted(
            workflow, mockDestS3Client, mockKmsClient);

        // Then
        verify(mockKmsClient, never()).getKeyPolicy(any(GetKeyPolicyRequest.class));
        verify(mockKmsClient, never()).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    private GetBucketEncryptionResponse getGetBucketS3EncryptionResponse() {
        GetBucketEncryptionResponse mockEncryptionResponse = GetBucketEncryptionResponse.builder()
            .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                .rules(Collections.singletonList(
                    ServerSideEncryptionRule.builder()
                        .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder()
                            .sseAlgorithm(ServerSideEncryption.AES256)
                            .build())
                        .build()))
                .build())
            .build();
        return mockEncryptionResponse;
    }

    @Test
    void testWhenBucketKMSEncrypted_UpdatePolicySuccessfully() {
        // Given
        GetBucketEncryptionResponse bucketKMSEncryptionResponse =
            getGetBucketKMSEncryptionResponse();

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(bucketKMSEncryptionResponse);

        String existingPolicy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:GetObject\"],\"Resource\":[\"*\"]}]}";
        GetKeyPolicyResponse mockKeyPolicyResponse = GetKeyPolicyResponse.builder()
            .policy(existingPolicy)
            .policyName("default")
            .build();
        when(mockKmsClient.getKeyPolicy(any(GetKeyPolicyRequest.class)))
            .thenReturn(mockKeyPolicyResponse);

        // When
        s3PolicyManager.updateDestKeyPolicyIfBucketIsKmsEncrypted(
            workflow, mockDestS3Client, mockKmsClient);

        // Then
        verify(mockKmsClient).getKeyPolicy(any(GetKeyPolicyRequest.class));
        verify(mockKmsClient).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    private GetBucketEncryptionResponse getGetBucketKMSEncryptionResponse() {
        GetBucketEncryptionResponse mockEncryptionResponse = GetBucketEncryptionResponse.builder()
            .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                .rules(Collections.singletonList(
                    ServerSideEncryptionRule.builder()
                        .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder()
                            .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                            .kmsMasterKeyID(KMS_KEY_ID)
                            .build())
                        .build()))
                .build())
            .build();
        return mockEncryptionResponse;
    }

    @Test
    void testWhenKMSExceptionOccurs_VerifyErrorLogging() {
        // Given
        GetBucketEncryptionResponse bucketKMSEncryptionResponse = getGetBucketKMSEncryptionResponse();

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(bucketKMSEncryptionResponse);

        KmsException kmsException = (KmsException) KmsException.builder()
            .message("KMS Error")
            .build();

        when(mockKmsClient.getKeyPolicy(any(GetKeyPolicyRequest.class)))
            .thenThrow(kmsException);


        // When/Then
        AWSServiceException thrown = assertThrows(AWSServiceException.class, () ->
            s3PolicyManager.updateDestKeyPolicyIfBucketIsKmsEncrypted(
                workflow, mockDestS3Client, mockKmsClient)
        );

        // Verify exception details
        assertEquals("Failed to update KMS key policy for key: " + KMS_KEY_ID, thrown.getMessage());
        assertEquals(kmsException, thrown.getCause());
    }

    @Test
    public void testBuildCrossAccountPutBucketPolicyRequest() {
        String destBucketName = "dest-bucket";
        String sourceBucketArn = "arn";

        StringBuilder originalPolicyBuilder = new StringBuilder();

        originalPolicyBuilder.append("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"1\",\"Effect\":\"Allow\"," +
            "\"Principal\":{\"AWS\":\"arn\"},\"Action\":\"s3:*\",\"Resource\":[\"arn:aws:s3:::dest-bucket\"]}]}");

        GetBucketPolicyResponse mockResponse = GetBucketPolicyResponse.builder()
            .policy(originalPolicyBuilder.toString())
            .build();

        when(mockDestS3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
            .thenReturn(mockResponse);

        PutBucketPolicyRequest putBucketPolicyRequest = s3PolicyManager
            .buildCrossAccountPutBucketPolicyRequest(destBucketName, sourceBucketArn, mockDestS3Client);


        StringBuilder expectedPolicyBuilder = new StringBuilder();

        // Define the bucket policy for cross account migration in destination bucket as a JSON string

        expectedPolicyBuilder.append("{\"Version\":\"2012-10-17\",\"Statement\":[" +
            "{\"Sid\":\"1\",\"Effect\":\"Allow\"," +
            "\"Principal\":{\"AWS\":\"arn\"},\"Action\":\"s3:*\",\"Resource\":\"arn:aws:s3:::dest-bucket\"},"+
            "{\"Sid\":\"S3ASourceCrossAccountAccess\"," +
            "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"arn:aws:iam::arn:role/s3a-bops-permissions\"},\"Action\":[\"s3:GetBucketVersioning\",\"s3:PutBucketVersioning\",\"s3:List*\",\"s3:ReplicateObject\",\"s3:ReplicateDelete\",\"s3:ReplicateTags\",\"s3:GetObjectVersionTagging\",\"s3:ObjectOwnerOverrideToBucketOwner\",\"s3:GetObjectVersionForReplication\"]," +
            "\"Resource\":[\"arn:aws:s3:::dest-bucket\",\"arn:aws:s3:::dest-bucket/*\"]}]}");

        assertAll(
            () -> assertNotNull(putBucketPolicyRequest, "PutBucketPolicyRequest should not be null"),
            () -> assertEquals(destBucketName, putBucketPolicyRequest.bucket(), "Bucket name should match"),
            () -> assertNotNull(putBucketPolicyRequest.policy(), "Policy should not be null"),
            () -> assertEquals(expectedPolicyBuilder.toString(), putBucketPolicyRequest.policy(), "Policy JSON should match"));
    }

    @Test
    public void testBuildCrossAccountPutBucketPolicyRequest_NoPolicyFound() {
        String destBucketName = "dest-bucket";
        String sourceBucketArn = "arn";

        AwsServiceException noBucketPolicyException = S3Exception.builder()
            .statusCode(404)
            .build();

        when(mockDestS3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
            .thenThrow(noBucketPolicyException);

        PutBucketPolicyRequest putBucketPolicyRequest = s3PolicyManager
            .buildCrossAccountPutBucketPolicyRequest(destBucketName, sourceBucketArn, mockDestS3Client);


        StringBuilder expectedPolicyBuilder = new StringBuilder();

        // Define the bucket policy for cross account migration in destination bucket as a JSON string

        expectedPolicyBuilder.append("{\"Version\":\"2012-10-17\",\"Statement\":" +
            "{\"Sid\":\"S3ASourceCrossAccountAccess\"," +
            "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"arn:aws:iam::arn:role/s3a-bops-permissions\"},\"Action\":[\"s3:GetBucketVersioning\",\"s3:PutBucketVersioning\",\"s3:List*\",\"s3:ReplicateObject\",\"s3:ReplicateDelete\",\"s3:ReplicateTags\",\"s3:GetObjectVersionTagging\",\"s3:ObjectOwnerOverrideToBucketOwner\",\"s3:GetObjectVersionForReplication\"]," +
            "\"Resource\":[\"arn:aws:s3:::dest-bucket\",\"arn:aws:s3:::dest-bucket/*\"]}}");

        assertAll(
            () -> assertNotNull(putBucketPolicyRequest, "PutBucketPolicyRequest should not be null"),
            () -> assertEquals(destBucketName, putBucketPolicyRequest.bucket(), "Bucket name should match"),
            () -> assertNotNull(putBucketPolicyRequest.policy(), "Policy should not be null"),
            () -> assertEquals(expectedPolicyBuilder.toString(), putBucketPolicyRequest.policy(), "Policy JSON should match"));
    }

    @Test
    void testAddCrossAccountBucketPolicy_Success() {
        // Arrange
        String destBucketName = "target-bucket";
        String sourceAccountNumber = "123456789012";
        workflow.setSourceAccountNumber(sourceAccountNumber);

        // Mock successful policy put
        when(mockDestS3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
            .thenReturn(PutBucketPolicyResponse.builder().build());

        StringBuilder originalPolicyBuilder = new StringBuilder();

        originalPolicyBuilder.append("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"1\",\"Effect\":\"Allow\"," +
            "\"Principal\":{\"AWS\":\"arn\"},\"Action\":[\"s3:*\"],\"Resource\":[\"arn:aws:s3:::dest-bucket\"]}]}");

        GetBucketPolicyResponse mockResponse = GetBucketPolicyResponse.builder()
            .policy(originalPolicyBuilder.toString())
            .build();

        when(mockDestS3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
            .thenReturn(mockResponse);

        // Act
        assertDoesNotThrow(() ->
            s3PolicyManager.addCrossAccountBucketPolicy(workflow, mockDestS3Client, destBucketName)
        );

        // Assert
        ArgumentCaptor<PutBucketPolicyRequest> policyRequestCaptor =
            ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(mockDestS3Client).putBucketPolicy(policyRequestCaptor.capture());

        PutBucketPolicyRequest capturedRequest = policyRequestCaptor.getValue();
        assertNotNull(capturedRequest);
        assertTrue(capturedRequest.policy().contains(sourceAccountNumber));
        assertTrue(capturedRequest.policy().contains(destBucketName));
    }

    @Test
    void testAddCrossAccountBucketPolicy_ThrowsS3Exception() {
        // Arrange
        String destBucketName = "target-bucket";
        String sourceAccountNumber = "123456789012";
        workflow.setSourceAccountNumber(sourceAccountNumber);

        StringBuilder originalPolicyBuilder = new StringBuilder();

        originalPolicyBuilder.append("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"1\",\"Effect\":\"Allow\"," +
            "\"Principal\":{\"AWS\":\"arn\"},\"Action\":[\"s3:*\"],\"Resource\":[\"arn:aws:s3:::dest-bucket\"]}]}");

        GetBucketPolicyResponse mockResponse = GetBucketPolicyResponse.builder()
            .policy(originalPolicyBuilder.toString())
            .build();

        when(mockDestS3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
            .thenReturn(mockResponse);

        // Mock S3Exception
        when(mockDestS3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
            .thenThrow(S3Exception.builder().message("Access Denied").build());

        // Act & Assert
        S3Exception exception = assertThrows(S3Exception.class, () ->
            s3PolicyManager.addCrossAccountBucketPolicy(workflow, mockDestS3Client, destBucketName)
        );

        verify(mockDestS3Client).putBucketPolicy(any(PutBucketPolicyRequest.class));
        assertNotNull(exception);
        assertEquals("Access Denied", exception.getMessage());
    }



}
