package com.amazon.bopspar.service.resources.s3.replication;

import com.amazon.bopspar.model.AWSServiceException;
import com.amazon.bopspar.service.resources.replication.S3PostReplicationService;
import com.amazon.bopspar.service.resources.s3.configuration.S3ConfigurationService;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.Captor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

class S3PostReplicationServiceTest {

    @Mock
    private S3Client s3SourceClient;

    @Mock
    private S3Client s3DestClient;


    @Mock
    private WorkFlowModel workflowModel;

    @Mock
    private KmsClient kmsClient;

    @InjectMocks
    private S3PostReplicationService postReplicationService;

    @InjectMocks
    private S3ConfigurationService s3ConfigurationService;

    @Captor
    private ArgumentCaptor<PutBucketReplicationRequest> replicationRequestCaptor;

    private static final String BOPS_ROLE = "arn:aws:iam::123456789012:role/bops-role";
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
    void testReEnableBucketLifecycleRules_Success() throws JsonProcessingException {
        GetBucketLifecycleConfigurationResponse lifecycleConfiguration = GetBucketLifecycleConfigurationResponse.builder()
                .rules(Collections.singletonList(LifecycleRule.builder().build()))
                .build();
        Map<String, String> config = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        config.put("bucketLifecycle", mapper.writeValueAsString(lifecycleConfiguration.toBuilder()));
        workflowModel.setSourceBucketConfig(config);

        when(s3SourceClient.putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class)))
                .thenReturn(PutBucketLifecycleConfigurationResponse.builder().build());

        assertDoesNotThrow(() -> postReplicationService.reEnableBucketLifecycleRules(s3SourceClient, "test-bucket", workflowModel));

        verify(s3SourceClient, times(1)).putBucketLifecycleConfiguration(
                PutBucketLifecycleConfigurationRequest.builder()
                        .bucket("test-bucket")
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(lifecycleConfiguration.rules())
                                .build())
                        .build());
    }

    @Test
    void testReEnableBucketLifecycleRules_NoLifecycleRules_Success() throws JsonProcessingException {
        GetBucketLifecycleConfigurationResponse lifecycleConfiguration = GetBucketLifecycleConfigurationResponse.builder()
                .build();
        Map<String, String> config = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        config.put("bucketLifecycle", mapper.writeValueAsString(lifecycleConfiguration.toBuilder()));
        workflowModel.setSourceBucketConfig(config);

        when(s3SourceClient.putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class)))
                .thenReturn(PutBucketLifecycleConfigurationResponse.builder().build());

        assertDoesNotThrow(() -> postReplicationService.reEnableBucketLifecycleRules(s3SourceClient, "test-bucket", workflowModel));

        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testReEnableBucketLifecycleRules_ThrowsRuntimeExceptionOnJsonProcessingFailure() {
        Map<String, String> config = new HashMap<>();
        config.put("bucketLifecycle", "{INVALID_JSON}");
        workflowModel.setSourceBucketConfig(config);

        when(s3SourceClient.putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class)))
                .thenReturn(PutBucketLifecycleConfigurationResponse.builder().build());

        assertThrows(RuntimeException.class, () -> postReplicationService.reEnableBucketLifecycleRules(s3SourceClient, "test-bucket", workflowModel));

        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testReEnableBucketLifecycleRules_WhenNoBucketLifecycleKeyFound_Success() {
        Map<String, String> config = new HashMap<>();
        workflowModel.setSourceBucketConfig(config);

        assertDoesNotThrow(() -> postReplicationService.reEnableBucketLifecycleRules(s3SourceClient, "test-bucket", workflowModel));

        verify(s3SourceClient, never()).putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class));
    }

    @Test
    void testReEnableBucketLifecycleRules_ThrowsAwsServiceException() throws JsonProcessingException {
        GetBucketLifecycleConfigurationResponse lifecycleConfiguration = GetBucketLifecycleConfigurationResponse.builder()
                .rules(Collections.singletonList(LifecycleRule.builder().build()))
                .build();
        Map<String, String> config = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        config.put("bucketLifecycle", mapper.writeValueAsString(lifecycleConfiguration.toBuilder()));
        workflowModel.setSourceBucketConfig(config);

        when(s3SourceClient.putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class)))
                .thenThrow(AwsServiceException.class);

        assertThrows(AwsServiceException.class, () -> postReplicationService.reEnableBucketLifecycleRules(s3SourceClient, "test-bucket", workflowModel));

        verify(s3SourceClient, times(1)).putBucketLifecycleConfiguration(
                PutBucketLifecycleConfigurationRequest.builder()
                        .bucket("test-bucket")
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(lifecycleConfiguration.rules())
                                .build())
                        .build());
    }

    @Test
    void testReEnableBucketLifecycleRules_ThrowsS3Exception() throws JsonProcessingException {
        GetBucketLifecycleConfigurationResponse lifecycleConfiguration = GetBucketLifecycleConfigurationResponse.builder()
                .rules(Collections.singletonList(LifecycleRule.builder().build()))
                .build();
        Map<String, String> config = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        config.put("bucketLifecycle", mapper.writeValueAsString(lifecycleConfiguration.toBuilder()));
        workflowModel.setSourceBucketConfig(config);

        when(s3SourceClient.putBucketLifecycleConfiguration(any(PutBucketLifecycleConfigurationRequest.class)))
                .thenThrow(S3Exception.class);

        assertThrows(AWSServiceException.class, () -> postReplicationService.reEnableBucketLifecycleRules(s3SourceClient, "test-bucket", workflowModel));

        verify(s3SourceClient, times(1)).putBucketLifecycleConfiguration(
                PutBucketLifecycleConfigurationRequest.builder()
                        .bucket("test-bucket")
                        .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                                .rules(lifecycleConfiguration.rules())
                                .build())
                        .build());
    }

    @Test
    void testResetKmsKeyPolicy_Success_Kms_encryptionIfBucketIsKMSEncrypted() {
        // Arrange
        Map<String, String> bucketConfig = new HashMap<>();
        bucketConfig.put("bucketEncryption", buildValidBucketKmsEncryption());
        bucketConfig.put("bucketKmsKeyPolicy", buildValidKmsKeyPolicy());
        workflowModel.setDestBucketConfig(bucketConfig);

        // Act
        postReplicationService.resetKmsKeyPolicyIfBucketIsKMSEncrypted(kmsClient, workflowModel.getDestBucketConfig());

        // Assert
        verify(kmsClient).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    @Test
    void testResetKmsKeyPolicy_NullPolicy_IfBucketIsKMSEncrypted_NoAction() {
        // Arrange
        Map<String, String> bucketConfig = new HashMap<>();
        bucketConfig.put("bucketEncryption", buildValidBucketKmsEncryption());
        bucketConfig.put("bucketKmsKeyPolicy", null);
        workflowModel.setDestBucketConfig(bucketConfig);

        // Act
        postReplicationService.resetKmsKeyPolicyIfBucketIsKMSEncrypted(kmsClient,
                workflowModel.getDestBucketConfig());

        // Assert
        verify(kmsClient, never()).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    @Test
    void testResetKmsKeyPolicy_EmptyPolicy_IfBucketIsKMSEncrypted_NoAction() {
        // Arrange
        Map<String, String> bucketConfig = new HashMap<>();
        bucketConfig.put("bucketEncryption", buildValidBucketKmsEncryption());
        bucketConfig.put("bucketKmsKeyPolicy", "  ");
        workflowModel.setDestBucketConfig(bucketConfig);

        // Act
        postReplicationService.resetKmsKeyPolicyIfBucketIsKMSEncrypted(kmsClient,
                workflowModel.getDestBucketConfig());

        // Assert
        verify(kmsClient, never()).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    @Test
    void testResetKmsKeyPolicy_IfBucketIsKMSEncrypted_InvalidEncryptionJson_ThrowsException() {
        // Arrange
        Map<String, String> bucketConfig = new HashMap<>();
        bucketConfig.put("bucketEncryption", "invalid-json");
        bucketConfig.put("bucketKmsKeyPolicy", buildValidKmsKeyPolicy());
        workflowModel.setDestBucketConfig(bucketConfig);

        // Act & Assert
        assertThrows(AWSServiceException.class, () ->
                postReplicationService.resetKmsKeyPolicyIfBucketIsKMSEncrypted(kmsClient,
                        workflowModel.getDestBucketConfig())
        );
    }

    @Test
    void testResetKmsKeyPolicy_KmsClientError_ThrowsExceptionIfBucketIsKMSEncrypted() {
        // Arrange
        Map<String, String> bucketConfig = new HashMap<>();
        bucketConfig.put("bucketEncryption", buildValidBucketKmsEncryption());
        bucketConfig.put("bucketKmsKeyPolicy", buildValidKmsKeyPolicy());
        workflowModel.setDestBucketConfig(bucketConfig);

        doThrow(KmsException.class)
                .when(kmsClient)
                .putKeyPolicy(any(PutKeyPolicyRequest.class));

        // Act & Assert
        assertThrows(AWSServiceException.class, () ->
                postReplicationService.resetKmsKeyPolicyIfBucketIsKMSEncrypted(kmsClient,
                        workflowModel.getDestBucketConfig())
        );
    }

    private String buildValidBucketKmsEncryption() {
        return """
            {
            "serverSideEncryptionConfiguration": {
                "rules": [{
                    "applyServerSideEncryptionByDefault": {
                        "sseAlgorithm": "aws:kms",
                        "kmsMasterKeyID": "arn:aws:kms:region:account:key/key-id"
                    },
                    "bucketKeyEnabled": true
                }]
            }
        }""";
    }

    @Test
    void testWhenEncryptionResponseIsNull_thenReturnEmpty() {
        ServerSideEncryptionConfiguration serverSideEncryptionConfiguration= null;
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(serverSideEncryptionConfiguration)
                .build();

        Optional<String> result = s3ConfigurationService.getKmsKeyId(response);

        assertTrue(result.isEmpty());
    }

    @Test
    void testWhenRulesAreEmpty_thenReturnEmpty() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.emptyList())
                                .build()
                )
                .build();

        Optional<String> result = s3ConfigurationService.getKmsKeyId(response);

        assertTrue(result.isEmpty());
    }

    @Test
    void testWhenKmsEncryptionWithKeyId_thenReturnKeyId() {
        String expectedKeyId = "arn:aws:kms:region:123456789012:key/test-key";
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm("aws:kms")
                                                                .kmsMasterKeyID(expectedKeyId)
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();

        Optional<String> result = s3ConfigurationService.getKmsKeyId(response);

        assertTrue(result.isPresent());
        assertEquals(expectedKeyId, result.get());
    }

    @Test
    void testWhenNonKmsEncryption_thenReturnEmpty() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm("AES256")
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();

        Optional<String> result = s3ConfigurationService.getKmsKeyId(response);

        assertTrue(result.isEmpty());
    }


    @Test
    void testWhenKmsEncryptionWithoutKeyId_thenReturnEmpty() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm("aws:kms")
                                                                .kmsMasterKeyID(null)
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();

        Optional<String> result = s3ConfigurationService.getKmsKeyId(response);

        assertTrue(result.isEmpty());
    }

    @Test
    void testWhenExceptionOccurs_thenThrowAWSServiceException() {
        GetBucketEncryptionResponse response = null;

        assertThrows(AWSServiceException.class, () -> {
            s3ConfigurationService.getKmsKeyId(response);
        });
    }

    @Test
    void testGetKmsKeyIdWithDifferentEncryptionTypes() {
        // Test AWS_KMS
        GetBucketEncryptionResponse kmsResponse = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                                .kmsMasterKeyID("key-1")
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();

        assertTrue(s3ConfigurationService.getKmsKeyId(kmsResponse).isPresent());

        // Test AWS_KMS_DSSE
        GetBucketEncryptionResponse kmsDsseResponse = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS_DSSE)
                                                                .kmsMasterKeyID("key-2")
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();

        assertTrue(s3ConfigurationService.getKmsKeyId(kmsDsseResponse).isPresent());

        // Test AES256 (non-KMS)
        GetBucketEncryptionResponse aesResponse = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm(ServerSideEncryption.AES256)
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();

        assertTrue(s3ConfigurationService.getKmsKeyId(aesResponse).isEmpty());
    }



    private String buildValidKmsKeyPolicy() {
        return """
            {
                "policy": "{\\"Version\\":\\"2012-10-17\\",\\"Statement\\":[{\\"Sid\\":\\"Enable IAM User Permissions\\",\\"Effect\\":\\"Allow\\",\\"Principal\\":{\\"AWS\\":\\"arn:aws:iam::account:root\\"},\\"Action\\":\\"kms:*\\",\\"Resource\\":\\"*\\"}]}",
                "policyName": "default"
            }
            """;
    }

    @Test
    void testSingleStatementPolicy_shouldDeleteEntirePolicy() {
        // Arrange
        String singleStatementPolicy = """
            {
                "Version": "2012-10-17",
                "Statement": [{
                    "Sid": "S3ASourceCrossAccountAccess",
                    "Effect": "Allow",
                    "Principal": {"AWS": "arn:aws:iam::123456789012:role/s3a-bops-permissions"},
                    "Action": "s3:*",
                    "Resource": "arn:aws:s3:::test-bucket/*"
                }]
            }""";

        when(s3DestClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy(singleStatementPolicy).build());

        // Act
        postReplicationService.resetBucketPolicy(s3DestClient, "test-bucket", workflowModel);

        // Assert
        verify(s3DestClient).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3DestClient, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void testTwoStatementsPolicy_shouldDeleteEntirePolicyTwoStatements() {
        // Arrange
        String twoStatementsPolicy = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "S3ASourceCrossAccountAccess",
                        "Effect": "Allow",
                        "Principal": {"AWS": "arn:aws:iam::123456789012:role/s3a-bops-permissions"},
                        "Action": "s3:*",
                        "Resource": "arn:aws:s3:::test-bucket/*"
                    },
                    {
                        "Sid": "S3ASourceCrossAccountAccess",
                        "Effect": "Allow",
                        "Principal": {"AWS": "arn:aws:iam::123456789012:role/s3a-bops-permissions"},
                        "Action": "s3:GetObject",
                        "Resource": "arn:aws:s3:::test-bucket/*"
                    }
                ]
            }""";

        when(s3DestClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy(twoStatementsPolicy).build());

        // Act
        postReplicationService.resetBucketPolicy(s3DestClient, "test-bucket", workflowModel);

        // Assert
        verify(s3DestClient).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3DestClient, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void testMultipleStatements_shouldRemoveSpecificStatement() {
        // Arrange
        String multipleStatementsPolicy = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "S3ASourceCrossAccountAccess",
                        "Effect": "Allow",
                        "Principal": {"AWS": "arn:aws:iam::123456789012:role/s3a-bops-permissions"},
                        "Action": "s3:*",
                        "Resource": "arn:aws:s3:::test-bucket/*"
                    },
                    {
                        "Sid": "OtherStatement",
                        "Effect": "Allow",
                        "Principal": {"AWS": "arn:aws:iam::09876543212:root"},
                        "Action": "s3:GetObject",
                        "Resource": "arn:aws:s3:::test-bucket/*"
                    }
                ]
            }""";

        when(s3DestClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy(multipleStatementsPolicy).build());

        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);

        // Act
        postReplicationService.resetBucketPolicy(s3DestClient, "test-bucket", workflowModel);

        // Assert
        verify(s3DestClient, never()).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3DestClient).putBucketPolicy(policyCaptor.capture());

    }

    @Test
    void testGetPolicyFails_shouldThrowRuntimeException() {
        // Arrange
        when(s3DestClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenThrow(S3Exception.builder().message("Get policy failed").build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                postReplicationService.resetBucketPolicy(s3DestClient, "test-bucket", workflowModel));
    }

    @Test
    void testInvalidPolicyJson_shouldThrowRuntimeException() {
        // Arrange
        when(s3DestClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy("invalid-json").build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                postReplicationService.resetBucketPolicy(s3DestClient, "test-bucket", workflowModel));
    }

    @Test
    void testPutPolicyFails_shouldThrowRuntimeException() {
        // Arrange
        String multipleStatementsPolicy = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "S3ASourceCrossAccountAccess",
                        "Effect": "Allow",
                        "Principal": {"AWS": "arn:aws:iam::123456789012:role/s3a-bops-permissions"},
                        "Action": "s3:*",
                        "Resource": "arn:aws:s3:::test-bucket/*"
                    },
                    {
                        "Sid": "OtherStatement",
                        "Effect": "Allow",
                        "Principal": {"AWS": "arn:aws:iam::09876543212:root"},
                        "Action": "s3:GetObject",
                        "Resource": "arn:aws:s3:::test-bucket/*"
                    }
                ]
            }""";

        when(s3DestClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder().policy(multipleStatementsPolicy).build());
        when(s3DestClient.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenThrow(S3Exception.builder().message("Put policy failed").build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                postReplicationService.resetBucketPolicy(s3DestClient, "test-bucket", workflowModel));
    }

    @Test
    void testRemoveStatement_whenStatementHasNoSid() {
        String policyWithNoSid = """
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {"AWS": "arn:aws:iam::123456789012:root"},
                    "Action": "s3:*",
                    "Resource": "arn:aws:s3:::test-bucket/*"
                }
            ]
        }""";

        when(s3DestClient.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder()
                        .policy(policyWithNoSid)
                        .build());

        // Act
        postReplicationService.resetBucketPolicy(s3DestClient, "test-bucket", workflowModel);

        // Assert
        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3DestClient, never()).deleteBucketPolicy(any(DeleteBucketPolicyRequest.class));
        verify(s3DestClient).putBucketPolicy(policyCaptor.capture());
    }


}