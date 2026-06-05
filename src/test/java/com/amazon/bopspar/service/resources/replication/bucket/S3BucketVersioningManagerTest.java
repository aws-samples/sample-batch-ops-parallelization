package com.amazon.bopspar.service.resources.replication.bucket;

import com.amazon.bopspar.persistence.model.WorkFlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3BucketVersioningManagerTest {
    private static final String SOURCE_ROLE_ARN = "arn:aws:iam::123456789012:role/source-role";
    private static final String SOURCE_BUCKET_ARN = "arn:aws:s3:::source-bucket";
    private static final String DEST_REGION1 = "dest-region1";
    private static final String DEST_BUCKET_ARN = "arn:aws:s3:::target-bucket";
    private static final String SOURCE_ACCOUNT_NUMBER = "123456789012";
    private static final String DEST_ACCOUNT_NUMBER = "123456789012";

    @Mock
    private S3Client mockSourceS3Client;
    private WorkFlowModel workflow;
    private S3BucketVersioningManager s3BucketVersioningManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        s3BucketVersioningManager = new S3BucketVersioningManager();

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
    public void testEnableBucketVersioning() {
        // Arrange
        PutBucketVersioningResponse response = mock(PutBucketVersioningResponse.class);
        when(mockSourceS3Client.putBucketVersioning(any(PutBucketVersioningRequest.class))).thenReturn(response);

        // Act
        s3BucketVersioningManager.enableBucketVersioning(workflow, mockSourceS3Client);

        // Assert
        verify(mockSourceS3Client, times(1)).putBucketVersioning(any(PutBucketVersioningRequest.class));
    }

    @Test
    public void testEnableBucketVersioningThrowsS3Exception() {
        // Arrange: Mock AwsErrorDetails and S3Exception
        AwsErrorDetails mockAwsErrorDetails = mock(AwsErrorDetails.class);
        when(mockAwsErrorDetails.errorMessage()).thenReturn("S3 error occurred");
        S3Exception mockS3Exception = mock(S3Exception.class);
        when(mockS3Exception.awsErrorDetails()).thenReturn(mockAwsErrorDetails);

        // Mock S3Client to throw S3Exception
        when(mockSourceS3Client.putBucketVersioning(any(PutBucketVersioningRequest.class)))
            .thenThrow(mockS3Exception);

        // Act & Assert: Expect RuntimeException to be thrown
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            s3BucketVersioningManager.enableBucketVersioning(workflow, mockSourceS3Client));
        assertEquals("S3 error occurred", thrown.getMessage());
    }
}
