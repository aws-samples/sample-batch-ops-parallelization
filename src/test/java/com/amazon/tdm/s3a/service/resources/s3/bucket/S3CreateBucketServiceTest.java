package com.amazon.tdm.s3a.service.resources.s3.bucket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;


class S3CreateBucketServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3CreateBucketImpl createBucketImpl;

    @InjectMocks
    private S3CreateBucketService s3CreateBucketService;

    @Mock
    private S3Client s3SourceClient;

    @Mock
    private S3Client s3DestClient;

    @Mock
    private WorkFlowModel workflow;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateS3Bucket_Success() {
        // Arrange
        String roleARN = "arn:aws:iam::123456789012:role/MyRole";
        String region = "us-west-1";
        String destBucketName = "my-bucket";
        CreateBucketRequest mockCreateBucketRequest = mock(CreateBucketRequest.class);

        when(createBucketImpl.buildCreateBucketConfig(roleARN, region, destBucketName))
                .thenReturn(mockCreateBucketRequest);

        // Act
        s3CreateBucketService.createS3Bucket(s3Client, roleARN, region, destBucketName);

        // Assert
        verify(s3Client).createBucket(mockCreateBucketRequest);
        verify(createBucketImpl).buildCreateBucketConfig(roleARN, region, destBucketName);
    }

    @Test
    void testCreateS3Bucket_Failure() {
        // Arrange
        String roleARN = "arn:aws:iam::123456789012:role/MyRole";
        String region = "us-west-1";
        String destBucketName = "my-bucket";
        CreateBucketRequest mockCreateBucketRequest = mock(CreateBucketRequest.class);

        when(createBucketImpl.buildCreateBucketConfig(roleARN, region, destBucketName))
                .thenReturn(mockCreateBucketRequest);

        doThrow(new RuntimeException("Bucket creation failed")).when(s3Client).createBucket(mockCreateBucketRequest);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                s3CreateBucketService.createS3Bucket(s3Client, roleARN, region, destBucketName)
        );
        assertEquals("Bucket creation failed", exception.getMessage());
    }

    @Test
    void testCheckBucketExists_BucketExists() {
        // Arrange
        String destBucketName = "existing-bucket";

        // No exception is thrown, so the bucket exists
        // Act
        boolean result = s3CreateBucketService.checkBucketExists(s3Client, destBucketName);

        // Assert
        assertTrue(result);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void testCheckBucketExists_BucketDoesNotExist() {
        // Arrange
        String destBucketName = "non-existing-bucket";
        // Mock the S3Exception for 404 Not Found
        S3Exception notFoundException =  mock(S3Exception.class);
        when(notFoundException.statusCode()).thenReturn(404);

        doThrow(notFoundException).when(s3Client).headBucket(any(HeadBucketRequest.class));

        // Act
        boolean result = s3CreateBucketService.checkBucketExists(s3Client, destBucketName);

        // Assert
        assertFalse(result);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void testCheckBucketExists_AccessDenied() {
        // Arrange
        String destBucketName = "forbidden-bucket";
        // Mock the S3Exception for 403 Forbidden
        S3Exception forbiddenException = mock(S3Exception.class);
        when(forbiddenException.statusCode()).thenReturn(403);

        doThrow(forbiddenException).when(s3Client).headBucket(any(HeadBucketRequest.class));

        // Act & Assert
        S3Exception exception = assertThrows(S3Exception.class, () ->
                s3CreateBucketService.checkBucketExists(s3Client, destBucketName)
        );
        assertEquals(403, exception.statusCode());
    }

    @Test
    void testEnableBucketVersioning_success() {
        // Arrange
        String sourceBucketArn = "arn:aws:s3:::source-bucket";
        String destBucketArn = "arn:aws:s3:::dest-bucket";
        String workflowName = "test-workflow";

        when(workflow.getSourceBucketARN()).thenReturn(sourceBucketArn);
        when(workflow.getDestBucketARN()).thenReturn(destBucketArn);
        when(workflow.getWorkflowName()).thenReturn(workflowName);

        // Act
        s3CreateBucketService.enableBucketVersioning(workflow, s3SourceClient, s3DestClient);

        // Assert
        verify(s3SourceClient).putBucketVersioning(any(PutBucketVersioningRequest.class));
        verify(s3DestClient).putBucketVersioning(any(PutBucketVersioningRequest.class));
    }



    @Test
    void testGenerateBucketName_NullPrefix() {
        String bucketName = s3CreateBucketService.generateBucketName(null);

        assertNotNull(bucketName);
        assertEquals(10, bucketName.length());
        assertTrue(bucketName.matches("^[a-z0-9]+$"));
    }

    @Test
    void testGenerateBucketName_EmptyPrefix() {
        String bucketName = s3CreateBucketService.generateBucketName("");

        assertNotNull(bucketName);
        assertEquals(10, bucketName.length());
        assertTrue(bucketName.matches("^[a-z0-9]+$"));
    }

    @Test
    void testGenerateBucketName_ValidPrefix() {
        String prefix = "test";
        String bucketName = s3CreateBucketService.generateBucketName(prefix);

        assertTrue(bucketName.startsWith(prefix + "-"));
        assertEquals(15, bucketName.length()); // prefix(4) + hyphen(1) + UUID(10)
        assertTrue(bucketName.matches("^test-[a-z0-9]+$"));
    }

    @Test
    void testGenerateBucketName_PrefixWithUppercase() {
        String prefix = "TEST";
        String bucketName = s3CreateBucketService.generateBucketName(prefix);

        assertTrue(bucketName.startsWith("test-"));
        assertTrue(bucketName.matches("^test-[a-z0-9]+$"));
    }

    @Test
    void testGenerateBucketName_PrefixWithTrailingHyphen() {
        String prefix = "test-";
        String bucketName = s3CreateBucketService.generateBucketName(prefix);

        assertTrue(bucketName.startsWith("test-"));
        assertFalse(bucketName.contains("--"));
    }

    @Test
    void testGenerateBucketName_PrefixWithValidSpecialCharacters() {
        String prefix = "test.bucket-name";
        String bucketName = s3CreateBucketService.generateBucketName(prefix);

        assertTrue(bucketName.startsWith(prefix + "-"));
        assertTrue(bucketName.matches("^test\\.bucket-name-[a-z0-9]+$"));
    }

    @Test
    void testGenerateBucketName_TwoCallsProduceDifferentResults() {
        String firstBucket = s3CreateBucketService.generateBucketName("test");
        String secondBucket = s3CreateBucketService.generateBucketName("test");

        assertNotEquals(firstBucket, secondBucket);
    }

    @Test
    void testGenerateBucketName_InvalidCharactersInPrefix() {
        String prefix = "test_bucket$";

        assertThrows(IllegalArgumentException.class, () ->
                s3CreateBucketService.generateBucketName(prefix)
        );
    }

    @Test
    void testGenerateBucketName_PrefixTooLong() {
        String prefix = "a".repeat(54); // 63 (max) - 10 (UUID) - 1 (hyphen) + 2 = too long

        assertThrows(IllegalArgumentException.class, () ->
                s3CreateBucketService.generateBucketName(prefix)
        );
    }

    @Test
    void testGenerateBucketName_MaximumValidPrefixLength() {
        String prefix = "a".repeat(52); // 63 (max) - 10 (UUID) - 1 (hyphen)
        String bucketName = s3CreateBucketService.generateBucketName(prefix);

        assertTrue(bucketName.startsWith(prefix + "-"));
        assertEquals(63, bucketName.length());
    }
}
