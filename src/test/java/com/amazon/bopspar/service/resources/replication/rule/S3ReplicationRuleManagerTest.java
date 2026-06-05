package com.amazon.bopspar.service.resources.replication.rule;

import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.replication.S3ReplicationUtils;
import com.amazon.bopspar.service.resources.replication.rule.S3ReplicationRuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationResponse;
import software.amazon.awssdk.services.s3.model.PutBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketReplicationResponse;
import software.amazon.awssdk.services.s3.model.ReplicationConfiguration;
import software.amazon.awssdk.services.s3.model.ReplicationRule;
import software.amazon.awssdk.services.s3.model.ReplicationRuleStatus;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3ReplicationRuleManagerTest {
    private static final String SOURCE_ROLE_ARN = "arn:aws:iam::123456789012:role/source-role";
    private static final String SOURCE_BUCKET_ARN = "arn:aws:s3:::source-bucket";
    private static final String DEST_REGION1 = "dest-region1";
    private static final String DEST_BUCKET_ARN = "arn:aws:s3:::target-bucket";
    private static final String SOURCE_ACCOUNT_NUMBER = "123456789012";
    private static final String DEST_ACCOUNT_NUMBER = "123456789012";
    private static final String KMS_KEY_ID = "arn:aws:kms:region:account:key/test-key-id";
    private static final String BOPS_ROLE = "arn:aws:iam::123456789012:role/bops-role";
    private static final String DEST_REGION2 = "dest-region2";

    @Mock
    private S3Client mockDestS3Client;

    @Mock
    private S3Client mockSourceS3Client;

    @Captor
    private ArgumentCaptor<PutBucketReplicationRequest> replicationRequestCaptor;

    private WorkFlowModel workflow;

    @InjectMocks
    private S3ReplicationRuleManager s3ReplicationRuleManager;

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
    void getExistingReplicationRules_whenS3ExceptionOccurs_throwsRuntimeException() {
        // Arrange
        String errorMessage = "Access Denied";
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .awsErrorDetails(AwsErrorDetails.builder()
                .errorCode("AccessDenied")
                .errorMessage(errorMessage)
                .build())
            .build();

        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenThrow(s3Exception);

        // Act & Assert
        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> s3ReplicationRuleManager.setupLiveReplication(workflow, mockSourceS3Client, mockDestS3Client, "bopsRole"),
            "Expected setupLiveReplication to throw RuntimeException"
        );

        assertEquals(errorMessage, thrown.getMessage());
    }

    @Test
    void testRemoveReplicationRule_WhenRuleDoesNotExist() {
        // Arrange
        ReplicationConfiguration replicationConfig = ReplicationConfiguration.builder()
            .role("arn:aws:iam::123456789012:role/replication-role")
            .rules(Collections.singletonList(ReplicationRule.builder()
                .id("different-rule-id")
                .build()))
            .build();

        GetBucketReplicationResponse getBucketReplicationResponse = GetBucketReplicationResponse.builder()
            .replicationConfiguration(replicationConfig)
            .build();

        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenReturn(getBucketReplicationResponse);

        // Act
        s3ReplicationRuleManager.removeReplicationRule(mockSourceS3Client, workflow);

        // Assert
        verify(mockSourceS3Client, never()).deleteBucketReplication(any(DeleteBucketReplicationRequest.class));
        verify(mockSourceS3Client, never()).putBucketReplication(any(PutBucketReplicationRequest.class));
    }

    @Test
    void testRemoveReplicationRule_WhenRuleExistsAndIsLastRule() {
        // Arrange
        ReplicationConfiguration replicationConfig = ReplicationConfiguration.builder()
            .role("arn:aws:iam::123456789012:role/replication-role")
            .rules(Collections.singletonList(ReplicationRule.builder()
                .id(S3ReplicationUtils.generateReplicationRuleId(DEST_BUCKET_ARN, DEST_REGION1))
                .build()))
            .build();

        GetBucketReplicationResponse getBucketReplicationResponse = GetBucketReplicationResponse.builder()
            .replicationConfiguration(replicationConfig)
            .build();

        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenReturn(getBucketReplicationResponse);

        // Act
        s3ReplicationRuleManager.removeReplicationRule(mockSourceS3Client, workflow);

        // Assert
        verify(mockSourceS3Client,times(1)).deleteBucketReplication(any(DeleteBucketReplicationRequest.class));
        verify(mockSourceS3Client, never()).putBucketReplication(any(PutBucketReplicationRequest.class));
    }

    @Test
    void testRemoveReplicationRule_WhenRuleExistsWithOtherRules() {
        // Arrange
        ReplicationConfiguration replicationConfig = ReplicationConfiguration.builder()
            .role("arn:aws:iam::123456789012:role/replication-role")
            .rules(Arrays.asList(
                ReplicationRule.builder()
                    .id(S3ReplicationUtils.generateReplicationRuleId(DEST_BUCKET_ARN, DEST_REGION1))
                    .build(),
                ReplicationRule.builder().id("other-rule").build()))
            .build();

        GetBucketReplicationResponse getBucketReplicationResponse = GetBucketReplicationResponse.builder()
            .replicationConfiguration(replicationConfig)
            .build();

        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenReturn(getBucketReplicationResponse);

        // Act
        s3ReplicationRuleManager.removeReplicationRule(mockSourceS3Client, workflow);

        // Assert
        verify(mockSourceS3Client, never()).deleteBucketReplication(any(DeleteBucketReplicationRequest.class));
        verify(mockSourceS3Client, times(1)).putBucketReplication(any(PutBucketReplicationRequest.class));
    }

    @Test
    void testRemoveReplicationRule_WhenExceptionOccurs() {
        // Arrange
        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            s3ReplicationRuleManager.removeReplicationRule(mockSourceS3Client, workflow));

        verify(mockSourceS3Client, never()).deleteBucketReplication(any(DeleteBucketReplicationRequest.class));
        verify(mockSourceS3Client, never()).putBucketReplication(any(PutBucketReplicationRequest.class));
    }

    @Test
    void testRemoveReplicationRule_WhenNoConfigFound() {
        // Arrange
        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenThrow(S3Exception.builder()
                .statusCode(404)
                .build());

        // Act & Assert
        assertDoesNotThrow(() -> s3ReplicationRuleManager.removeReplicationRule(mockSourceS3Client, workflow));
        verify(mockSourceS3Client, never()).deleteBucketReplication(any(DeleteBucketReplicationRequest.class));
        verify(mockSourceS3Client, never()).putBucketReplication(any(PutBucketReplicationRequest.class));
    }

    @Test
    void testRemoveReplicationRule_ThrowsS3Exception() {
        // Arrange
        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenThrow(S3Exception.builder()
                .statusCode(500)
                .build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            s3ReplicationRuleManager.removeReplicationRule(mockSourceS3Client, workflow));
    }

    @Test
    void testSetupLiveReplication_SuccessWithKMSEncryption_crossAccount() {
        // Arrange
        workflow.setDestAccountNumber("012345678901");

        // Mock bucket encryption configuration that matches validateKMSEncryption() method
        GetBucketEncryptionResponse mockEncryptionResponse = getBucketKMSEncryptionResponse();

        // Mock getBucketEncryption for both source and destination buckets
        when(mockSourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(mockEncryptionResponse);
        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(mockEncryptionResponse);

        GetBucketReplicationResponse mockReplicationResponse = GetBucketReplicationResponse.builder()
            .replicationConfiguration(ReplicationConfiguration.builder()
                .rules(Collections.emptyList())
                .build())
            .build();

        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenReturn(mockReplicationResponse);

        when(mockSourceS3Client.putBucketReplication(any(PutBucketReplicationRequest.class)))
            .thenReturn(PutBucketReplicationResponse.builder().build());

        // Act
        s3ReplicationRuleManager.setupLiveReplication(workflow, mockSourceS3Client, mockDestS3Client, BOPS_ROLE);

        // Assert
        verify(mockSourceS3Client).putBucketReplication(replicationRequestCaptor.capture());

        PutBucketReplicationRequest capturedRequest = replicationRequestCaptor.getValue();
        ReplicationConfiguration replicationConfig = capturedRequest.replicationConfiguration();

        assertEquals("source-bucket", capturedRequest.bucket());
        assertEquals("arn:aws:iam::123456789012:role/bops-role", replicationConfig.role());
        assertFalse(replicationConfig.rules().isEmpty());

        ReplicationRule rule = replicationConfig.rules().get(0);
        assertNotNull(rule.destination());
        assertEquals("target-bucket", Arn.fromString(rule.destination().bucket()).resourceAsString());

        // Additional verification for encryption configuration
        assertNotNull(rule.destination().encryptionConfiguration(),
            "Encryption configuration should be present when KMS is enabled");
        assertEquals(KMS_KEY_ID, rule.destination().encryptionConfiguration().replicaKmsKeyID(),
            "KMS key ID should match the configured value");
    }

    private static GetBucketEncryptionResponse getBucketKMSEncryptionResponse() {
        return GetBucketEncryptionResponse.builder()
            .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                .rules(Collections.singletonList(
                    ServerSideEncryptionRule.builder()
                        .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder()
                            .sseAlgorithm(ServerSideEncryption.AWS_KMS.toString())
                            .kmsMasterKeyID(KMS_KEY_ID)
                            .build())
                        .build()))
                .build())
            .build();
    }

    // Test: S3Exception thrown during replication setup
    @Test
    void testSetupLiveReplication_S3Exception() {
        // Arrange: Mock AwsErrorDetails and S3Exception
        AwsErrorDetails mockAwsErrorDetails = mock(AwsErrorDetails.class);
        when(mockAwsErrorDetails.errorMessage()).thenReturn("S3 error occurred");

        S3Exception mockS3Exception = mock(S3Exception.class);
        when(mockS3Exception.awsErrorDetails()).thenReturn(mockAwsErrorDetails);


        // Mock getBucketEncryption for both source and destination buckets
        when(mockSourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(getBucketKMSEncryptionResponse());
        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(getBucketKMSEncryptionResponse());

        // Mock existing rules to simulate no existing rules
        GetBucketReplicationResponse mockReplicationResponse = GetBucketReplicationResponse.builder()
            .replicationConfiguration(ReplicationConfiguration.builder().rules(Collections.emptyList()).build())
            .build();
        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenReturn(mockReplicationResponse);

        // Mock S3Client to throw S3Exception
        when(mockSourceS3Client.putBucketReplication(any(PutBucketReplicationRequest.class)))
            .thenThrow(mockS3Exception);

        // Act & Assert: Expect RuntimeException to be thrown
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            s3ReplicationRuleManager.setupLiveReplication(workflow, mockSourceS3Client, mockDestS3Client, BOPS_ROLE));

        assertEquals("S3 error occurred", thrown.getMessage());

        // Verify putBucketReplication was called once
        verify(mockSourceS3Client, times(1)).getBucketReplication(any(GetBucketReplicationRequest.class)); // Check for existing rules
        verify(mockSourceS3Client, times(1)).putBucketReplication(any(PutBucketReplicationRequest.class));
    }

    @Test
    void testSetupLiveReplication_SuccessWithExistingRules() {
        // Arrange: Mock existing rules
        ReplicationRule existingRule = ReplicationRule.builder()
            .id(S3ReplicationUtils.generateReplicationRuleId(DEST_BUCKET_ARN, DEST_REGION2))
            .priority(1)
            .status(ReplicationRuleStatus.ENABLED)
            .build();

        GetBucketReplicationResponse mockReplicationResponse = GetBucketReplicationResponse.builder()
            .replicationConfiguration(ReplicationConfiguration.builder().rules(existingRule).build())
            .build();
        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class))).thenReturn(mockReplicationResponse);

        // Mock getBucketEncryption for both source and destination buckets
        when(mockSourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(getBucketKMSEncryptionResponse());
        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(getBucketKMSEncryptionResponse());

        // Act
        assertDoesNotThrow(() -> s3ReplicationRuleManager.setupLiveReplication(workflow, mockSourceS3Client, mockDestS3Client, BOPS_ROLE));

        // Assert
        verify(mockSourceS3Client, times(1)).putBucketReplication(replicationRequestCaptor.capture());

        PutBucketReplicationRequest capturedRequest = replicationRequestCaptor.getValue();
        assertEquals(2, capturedRequest.replicationConfiguration().rules().size()); // New rule added
    }

    @Test
    void testSetupLiveReplication_Success_S3AExistingRule() {
        // Arrange: Mock S3A existing rule
        ReplicationRule existingRule = ReplicationRule.builder()
            .id(S3ReplicationUtils.generateReplicationRuleId(DEST_BUCKET_ARN, DEST_REGION1))
            .priority(1)
            .status(ReplicationRuleStatus.ENABLED)
            .build();

        GetBucketReplicationResponse mockReplicationResponse = GetBucketReplicationResponse.builder()
            .replicationConfiguration(ReplicationConfiguration.builder().rules(existingRule).build())
            .build();
        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenReturn(mockReplicationResponse);

        // Act
        assertDoesNotThrow(() -> s3ReplicationRuleManager.setupLiveReplication(
            workflow, mockSourceS3Client, mockDestS3Client, BOPS_ROLE));

        // Assert
        verify(mockSourceS3Client, never()).putBucketReplication(any(PutBucketReplicationRequest.class));
    }

    @Test
    void testSetupLiveReplication_NoExistingRules() {
        // Arrange: Simulate no existing replication rules
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
            .errorCode("ReplicationConfigurationNotFoundError")
            .errorMessage("No replication configuration found")
            .build();
        AwsServiceException serviceException = S3Exception.builder()
            .awsErrorDetails(errorDetails)
            .build();

        when(mockSourceS3Client.getBucketReplication(any(GetBucketReplicationRequest.class)))
            .thenThrow(serviceException);

        when(mockSourceS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(getBucketKMSEncryptionResponse());

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
            .thenReturn(getBucketKMSEncryptionResponse());

        when(mockSourceS3Client.putBucketReplication(any(PutBucketReplicationRequest.class)))
            .thenReturn(PutBucketReplicationResponse.builder().build());

        // Act: Call the public method
        assertDoesNotThrow(() -> s3ReplicationRuleManager.setupLiveReplication(workflow, mockSourceS3Client,mockDestS3Client, BOPS_ROLE));

        // Assert: Verify interactions and ensure the new rule was added
        verify(mockSourceS3Client, times(1)).getBucketReplication(any(GetBucketReplicationRequest.class));
        verify(mockSourceS3Client, times(1)).putBucketReplication(any(PutBucketReplicationRequest.class));
    }
}
