package com.amazon.tdm.s3a.service.resources.replication.bucket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import com.amazon.tdm.s3a.model.AWSServiceException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3BucketTest {

    private static final String BUCKET_ARN = "arn:aws:s3:::test-bucket";
    private static final String BUCKET_NAME = "test-bucket";
    private static final String KMS_KEY_ID = "arn:aws:kms:region:account:key/test-key";

    @Mock
    private S3Client s3Client;

    private S3Bucket s3Bucket;

    @BeforeEach
    void setUp() {
        s3Bucket = S3Bucket.builder()
                .arn(BUCKET_ARN)
                .s3Client(s3Client)
                .build();
    }

    @Test
    void testGetName() {
        assertEquals(BUCKET_NAME, s3Bucket.getName());
    }

    @Test
    void testGetEncryptionType_WhenNoEncryption() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.emptyList())
                                .build()
                )
                .build();
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(response);

        assertEquals(EncryptionType.NONE, s3Bucket.getEncryptionType());
    }

    @Test
    void testGetEncryptionType_WhenKMSWithKeyId() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                                .kmsMasterKeyID(KMS_KEY_ID)
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(response);

        assertEquals(EncryptionType.SSE_KMS, s3Bucket.getEncryptionType());
    }

    @Test
    void testGetEncryptionType_WhenKMSWithoutKeyId() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(response);

        assertEquals(EncryptionType.UNKNOWN, s3Bucket.getEncryptionType());
    }

    @Test
    void testGetEncryptionType_WhenSSE_S3() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
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
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(response);

        assertEquals(EncryptionType.SSE_S3, s3Bucket.getEncryptionType());
    }

    @Test
    void testGetEncryptionType_WhenException() {
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // Act & Assert: Expect RuntimeException to be thrown
        assertThrows(AWSServiceException.class, () ->
            s3Bucket.getEncryptionType());

    }

    @Test
    void testGetKmsKeyId_WhenKMSWithKeyId() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.singletonList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm(ServerSideEncryption.AWS_KMS)
                                                                .kmsMasterKeyID(KMS_KEY_ID)
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(response);

        Optional<String> result = s3Bucket.getKmsKeyId();
        assertTrue(result.isPresent());
        assertEquals(KMS_KEY_ID, result.get());
    }

    @Test
    void testGetKmsKeyId_WhenNoKMSEncryption() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
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
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(response);

        assertTrue(s3Bucket.getKmsKeyId().isEmpty());
    }

    @Test
    void testGetKmsKeyId_WhenException() {
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenThrow(new RuntimeException("Test exception"));

        assertTrue(s3Bucket.getKmsKeyId().isEmpty());
    }

    @Test
    void testGetKmsKeyId_whenServerSideEncryptionConfigurationIsNull() {
        ServerSideEncryptionConfiguration serverSideEncryptionConfiguration = null;
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(serverSideEncryptionConfiguration)
                .build();
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(response);

        Optional<String> result = s3Bucket.getKmsKeyId();

        assertTrue(result.isEmpty());
    }


    @Test
    void testGetKmsKeyId_whenEncryptionRulesAreEmpty() {
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Collections.emptyList())
                                .build()
                )
                .build();
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(response);

        Optional<String> result = s3Bucket.getKmsKeyId();

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetEncryptionType_whenMultipleRulesWithNoMatch() {
        // Arrange
        GetBucketEncryptionResponse response = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(Arrays.asList(
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm("UNSUPPORTED_ALGORITHM_1")
                                                                .build()
                                                )
                                                .build(),
                                        ServerSideEncryptionRule.builder()
                                                .applyServerSideEncryptionByDefault(
                                                        ServerSideEncryptionByDefault.builder()
                                                                .sseAlgorithm("UNSUPPORTED_ALGORITHM_2")
                                                                .build()
                                                )
                                                .build()
                                ))
                                .build()
                )
                .build();
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(response);

        // Act
        EncryptionType result = s3Bucket.getEncryptionType();

        // Assert
        assertEquals(EncryptionType.NONE, result);
    }

    /**
     * Test case to verify the behavior of the isBucketExists() method when the bucket exists.
     */
    @Test
    void testExists_WhenBucketIsBucketExists() {
        // Given
        HeadBucketResponse response = HeadBucketResponse.builder().build();
        when(s3Client.headBucket(any(Consumer.class))).thenReturn(response);

        // When
        boolean result = s3Bucket.isBucketExists();

        // Then
        assertTrue(result);
    }

    /**
     * Test case to verify the behavior of the isBucketExists() method when the bucket does not exist.
     */
    @Test
    void testIsBucketExists_WhenBucketDoesNotExist() {
        // Given
        // Given
        S3Exception notFoundException = mock(S3Exception.class);
        when(notFoundException.statusCode()).thenReturn(404);

        when(s3Client.headBucket(any(Consumer.class))).thenThrow(notFoundException);

        boolean result = s3Bucket.isBucketExists();

        // Then
        assertFalse(result);
    }

    /**
     * Test case to verify the behavior of the isBucketExists() method when an access denied exception occurs.
     */
    @Test
    void testIsBucketExists_WhenAccessIsForbidden() {
        // Given
        S3Exception forbiddenException = mock(S3Exception.class);
        when(forbiddenException.statusCode()).thenReturn(403);

        when(s3Client.headBucket(any(Consumer.class))).thenThrow(forbiddenException);

        // When
        boolean result = s3Bucket.isBucketExists();

        // Then
        assertFalse(result);
    }

    /**
     * Test case to verify the behavior of the isBucketExists() method when an unexpected error occurs.
     */
    @Test
    void testIsBucketExists_WhenUnexpectedError() {
        // Given
        S3Exception forbiddenException = mock(S3Exception.class);
        when(forbiddenException.statusCode()).thenReturn(500);

        when(s3Client.headBucket(any(Consumer.class))).thenThrow(forbiddenException);

        // When
        boolean result = s3Bucket.isBucketExists();

        // Then
        assertFalse(result);
    }

}