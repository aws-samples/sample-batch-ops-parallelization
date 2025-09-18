package com.amazon.tdm.s3a.service.resources.replication.bops;

import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.model.RuntimeConfig;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.CreateJobRequest;
import software.amazon.awssdk.services.s3control.model.CreateJobResponse;
import software.amazon.awssdk.services.s3control.model.RequestedJobStatus;
import software.amazon.awssdk.services.s3control.model.S3ControlException;
import software.amazon.awssdk.services.s3control.model.UpdateJobStatusRequest;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3BatchOperationsManagerTest {
    private static final String SOURCE_ROLE_ARN = "arn:aws:iam::123456789012:role/source-role";
    private static final String SOURCE_BUCKET_ARN = "arn:aws:s3:::source-bucket";
    private static final String DEST_REGION1 = "dest-region1";
    private static final String DEST_BUCKET_ARN = "arn:aws:s3:::target-bucket";
    private static final String SOURCE_ACCOUNT_NUMBER = "123456789012";
    private static final String DEST_ACCOUNT_NUMBER = "123456789012";
    private static final String BOPS_ROLE = "arn:aws:iam::123456789012:role/bops-role";
    private static final String JOB_ID_1 = "job-1";
    private static final String JOB_ID_2 = "job-2";

    @Mock
    private S3ControlClient s3ControlClient;

    @Mock
    private S3Client mockSourceS3Client;

    @Mock
    private S3Uri s3Uri;

    @Mock
    private S3Utilities s3Utilities;

    @Mock
    private WorkflowRepository workflowRepository;

    private WorkFlowModel workflow;

    @InjectMocks
    private S3BatchOperationsManager s3BatchOperationsManager;

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
    public void testSetupBOPSJobWithManifest() {
        // Arrange
        workflow.setRuntimeConfig(RuntimeConfig.builder().manifestLocation("s://test-location/manifests").build());
        workflow.setWorkflowName("A".repeat(100));
        workflow.setSourceBucketARN("B".repeat(100));
        workflow.setDestBucketARN("C".repeat(100));
        CreateJobResponse createJobResponse = mock(CreateJobResponse.class);
        when(s3ControlClient.createJob(any(CreateJobRequest.class))).thenReturn(createJobResponse);
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(S3Object.builder()
                .key("test-manifest.csv")
                .eTag("\"dummy-etag\"")
                .build())
            .isTruncated(false)
            .build();
        when(mockSourceS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        HeadObjectResponse headObjResponse = HeadObjectResponse.builder()
            .eTag("\\")
            .build();
        when(mockSourceS3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headObjResponse);

        when(mockSourceS3Client.utilities()).thenReturn(s3Utilities);
        when(s3Utilities.parseUri(URI.create(workflow.getRuntimeConfig().getManifestLocation()))).thenReturn(s3Uri);
        when(s3Uri.bucket()).thenReturn(Optional.of("test-location"));
        when(s3Uri.key()).thenReturn(Optional.of("manifests"));

        // Act
        s3BatchOperationsManager.setupBOPSJob(workflow, mockSourceS3Client,
            s3ControlClient, workflowRepository, BOPS_ROLE);

        // Assert
        verify(s3ControlClient, times(1)).createJob(any(CreateJobRequest.class));
    }

    @Test
    public void testSetupBOPSJobWithManifest_longDescription() {
        // Arrange
        workflow.setRuntimeConfig(RuntimeConfig.builder().manifestLocation("s://test-location/manifests").build());
        CreateJobResponse createJobResponse = mock(CreateJobResponse.class);
        when(s3ControlClient.createJob(any(CreateJobRequest.class))).thenReturn(createJobResponse);
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(S3Object.builder()
                .key("test-manifest.csv")
                .eTag("\"dummy-etag\"")
                .build())
            .isTruncated(false)
            .build();
        when(mockSourceS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        HeadObjectResponse headObjResponse = HeadObjectResponse.builder()
            .eTag("\\")
            .build();
        when(mockSourceS3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headObjResponse);

        when(mockSourceS3Client.utilities()).thenReturn(s3Utilities);
        when(s3Utilities.parseUri(URI.create(workflow.getRuntimeConfig().getManifestLocation()))).thenReturn(s3Uri);
        when(s3Uri.bucket()).thenReturn(Optional.of("test-location"));
        when(s3Uri.key()).thenReturn(Optional.of("manifests"));

        // Act
        s3BatchOperationsManager.setupBOPSJob(workflow, mockSourceS3Client,
            s3ControlClient, workflowRepository, BOPS_ROLE);

        // Assert
        verify(s3ControlClient, times(1)).createJob(any(CreateJobRequest.class));
    }

    @Test
    public void testSetupBOPSJobWithManifestThrowsS3ControlException() {
        // Arrange: Mock AwsErrorDetails and S3Exception
        workflow.setRuntimeConfig(RuntimeConfig.builder().manifestLocation("s://test-location/manifests").build());
        AwsErrorDetails mockAwsErrorDetails = mock(AwsErrorDetails.class);
        when(mockAwsErrorDetails.errorMessage()).thenReturn("S3 error occurred");
        S3ControlException mockS3Exception = mock(S3ControlException.class);
        when(mockS3Exception.awsErrorDetails()).thenReturn(mockAwsErrorDetails);
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(S3Object.builder()
                .key("test-manifest.csv")
                .eTag("\"dummy-etag\"")
                .build())
            .isTruncated(false)
            .build();
        when(mockSourceS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        HeadObjectResponse headObjResponse = HeadObjectResponse.builder()
            .eTag("\\")
            .build();
        when(mockSourceS3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headObjResponse);
        when(mockSourceS3Client.utilities()).thenReturn(s3Utilities);
        when(s3Utilities.parseUri(URI.create(workflow.getRuntimeConfig().getManifestLocation()))).thenReturn(s3Uri);
        when(s3Uri.bucket()).thenReturn(Optional.of("test-location"));
        when(s3Uri.key()).thenReturn(Optional.of("manifests"));
        // Mock S3Client to throw S3Exception
        when(s3ControlClient.createJob(any(CreateJobRequest.class)))
            .thenThrow(mockS3Exception);
        // Act & Assert: Expect RuntimeException to be thrown
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            s3BatchOperationsManager.setupBOPSJob(workflow, mockSourceS3Client,
                s3ControlClient, workflowRepository, BOPS_ROLE));

        assertTrue(thrown.getMessage().contains("S3 error occurred"));
    }

    @Test
    public void testSetupBOPSJobWithManifestThrowsNoCSVFound() {
        // Arrange: Mock AwsErrorDetails and S3Exception
        workflow.setRuntimeConfig(RuntimeConfig.builder().manifestLocation("s://test-location/manifests").build());
        AwsErrorDetails mockAwsErrorDetails = mock(AwsErrorDetails.class);
        when(mockAwsErrorDetails.errorMessage()).thenReturn("S3 error occurred");
        S3ControlException mockS3Exception = mock(S3ControlException.class);
        when(mockS3Exception.awsErrorDetails()).thenReturn(mockAwsErrorDetails);
        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
            .contents(S3Object.builder()
                .key("test-manifest.txt")
                .build())
            .isTruncated(false)
            .build();
        when(mockSourceS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

        HeadObjectResponse headObjResponse = HeadObjectResponse.builder()
            .eTag("\\")
            .build();
        when(mockSourceS3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headObjResponse);
        when(mockSourceS3Client.utilities()).thenReturn(s3Utilities);
        when(s3Utilities.parseUri(URI.create(workflow.getRuntimeConfig().getManifestLocation()))).thenReturn(s3Uri);
        when(s3Uri.bucket()).thenReturn(Optional.of("test-location"));
        when(s3Uri.key()).thenReturn(Optional.of("manifests"));
        // Mock S3Client to throw S3Exception
        when(s3ControlClient.createJob(any(CreateJobRequest.class)))
            .thenThrow(mockS3Exception);
        // Act & Assert: Expect RuntimeException to be thrown
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            s3BatchOperationsManager.setupBOPSJob(workflow, mockSourceS3Client,
                s3ControlClient, workflowRepository, BOPS_ROLE));

        assertEquals("No CSV files found in manifest location", thrown.getMessage());
    }

    @Test
    public void testSetupBOPSJob() {
        // Arrange
        CreateJobResponse createJobResponse = mock(CreateJobResponse.class);
        when(s3ControlClient.createJob(any(CreateJobRequest.class))).thenReturn(createJobResponse);

        // Act
        s3BatchOperationsManager.setupBOPSJob(workflow, s3ControlClient, workflowRepository, BOPS_ROLE);

        // Assert
        verify(s3ControlClient, times(1)).createJob(any(CreateJobRequest.class));
    }

    @Test
    public void testSetupBOPSJob_longDescription() {
        // Arrange
        workflow.setWorkflowName("A".repeat(100));
        workflow.setSourceBucketARN("B".repeat(100));
        workflow.setDestBucketARN("C".repeat(100));
        CreateJobResponse createJobResponse = mock(CreateJobResponse.class);
        when(s3ControlClient.createJob(any(CreateJobRequest.class))).thenReturn(createJobResponse);

        // Act
        s3BatchOperationsManager.setupBOPSJob(workflow, s3ControlClient, workflowRepository, BOPS_ROLE);

        // Assert
        verify(s3ControlClient, times(1)).createJob(any(CreateJobRequest.class));
    }

    @Test
    public void testSetupBOPSJobThrowsS3ControlException() {
        // Arrange: Mock AwsErrorDetails and S3Exception
        AwsErrorDetails mockAwsErrorDetails = mock(AwsErrorDetails.class);
        when(mockAwsErrorDetails.errorMessage()).thenReturn("S3 error occurred");
        S3ControlException mockS3Exception = mock(S3ControlException.class);
        when(mockS3Exception.awsErrorDetails()).thenReturn(mockAwsErrorDetails);

        // Mock S3Client to throw S3Exception
        when(s3ControlClient.createJob(any(CreateJobRequest.class)))
            .thenThrow(mockS3Exception);
        // Act & Assert: Expect RuntimeException to be thrown
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            s3BatchOperationsManager.setupBOPSJob(workflow, s3ControlClient, workflowRepository, BOPS_ROLE));

        assertEquals("S3 error occurred", thrown.getMessage());
    }

    @Test
    void testBatchUpdateJobStatus_WhenEmptyJobList() {
        // Arrange
        workflow.setBopsJobIds(Collections.emptyList());

        // Act
        s3BatchOperationsManager.batchUpdateJobStatus(s3ControlClient, workflow, RequestedJobStatus.CANCELLED);

        // Assert
        verify(s3ControlClient, never()).updateJobStatus(any(UpdateJobStatusRequest.class));
    }

    @Test
    void testBatchUpdateJobStatus_SuccessfulUpdate() {
        // Arrange
        List<String> jobIds = Arrays.asList(JOB_ID_1, JOB_ID_2);
        workflow.setBopsJobIds(jobIds);

        // Act
        s3BatchOperationsManager.batchUpdateJobStatus(s3ControlClient, workflow, RequestedJobStatus.CANCELLED);

        // Assert
        verify(s3ControlClient, times(2)).updateJobStatus(any(UpdateJobStatusRequest.class));
    }

    @Test
    void testBatchUpdateJobStatus_WhenJobAlreadyCompleted() {
        // Arrange
        List<String> jobIds = Arrays.asList(JOB_ID_1, JOB_ID_2);
        workflow.setBopsJobIds(jobIds);

        when(s3ControlClient.updateJobStatus(any(UpdateJobStatusRequest.class)))
            .thenThrow(S3ControlException.builder()
                .statusCode(400)
                .message("Requested job status forbidden")
                .build());

        // Act
        s3BatchOperationsManager.batchUpdateJobStatus(s3ControlClient, workflow, RequestedJobStatus.CANCELLED);

        // Assert
        verify(s3ControlClient, times(2)).updateJobStatus(any(UpdateJobStatusRequest.class));
    }

    @Test
    void testBatchUpdateJobStatus_ThrowsS3ControlException() {
        // Arrange
        List<String> jobIds = Arrays.asList(JOB_ID_1, JOB_ID_2);
        workflow.setBopsJobIds(jobIds);

        when(s3ControlClient.updateJobStatus(any(UpdateJobStatusRequest.class)))
            .thenThrow(S3ControlException.builder()
                .statusCode(400)
                .message("Some other exception")
                .build());

        // Act
        assertThrows(RuntimeException.class, () -> s3BatchOperationsManager.batchUpdateJobStatus(s3ControlClient, workflow, RequestedJobStatus.CANCELLED));

        // Assert
        verify(s3ControlClient, times(1)).updateJobStatus(any(UpdateJobStatusRequest.class));
    }

    @Test
    void testBatchUpdateJobStatus_WhenUnexpectedError() {
        // Arrange
        List<String> jobIds = Collections.singletonList(JOB_ID_1);
        workflow.setBopsJobIds(jobIds);

        when(s3ControlClient.updateJobStatus(any(UpdateJobStatusRequest.class)))
            .thenThrow(S3ControlException.builder()
                .statusCode(500)
                .message("Internal server error")
                .build());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            s3BatchOperationsManager.batchUpdateJobStatus(s3ControlClient, workflow, RequestedJobStatus.CANCELLED));
    }

    @Test
    void testBatchUpdateJobStatus_VerifyRequestParameters() {
        // Arrange
        List<String> jobIds = Collections.singletonList(JOB_ID_1);
        workflow.setBopsJobIds(jobIds);

        ArgumentCaptor<UpdateJobStatusRequest> requestCaptor = ArgumentCaptor.forClass(UpdateJobStatusRequest.class);

        // Act
        s3BatchOperationsManager.batchUpdateJobStatus(s3ControlClient, workflow, RequestedJobStatus.CANCELLED);

        // Assert
        verify(s3ControlClient).updateJobStatus(requestCaptor.capture());
        UpdateJobStatusRequest capturedRequest = requestCaptor.getValue();
        assertEquals(SOURCE_ACCOUNT_NUMBER, capturedRequest.accountId());
        assertEquals(JOB_ID_1, capturedRequest.jobId());
        assertEquals(RequestedJobStatus.CANCELLED, capturedRequest.requestedJobStatus());
    }

    @Test
    void testBatchUpdateJobStatus_PartialSuccess() {
        // Arrange
        List<String> jobIds = Arrays.asList(JOB_ID_1, JOB_ID_2);
        workflow.setBopsJobIds(jobIds);

        when(s3ControlClient.updateJobStatus(any(UpdateJobStatusRequest.class)))
            .thenReturn(null)  // First call succeeds
            .thenThrow(S3ControlException.builder()
                .statusCode(400)
                .message("Requested job status forbidden")
                .build());  // Second call fails with already completed

        // Act
        s3BatchOperationsManager.batchUpdateJobStatus(s3ControlClient, workflow, RequestedJobStatus.CANCELLED);

        // Assert
        verify(s3ControlClient, times(2)).updateJobStatus(any(UpdateJobStatusRequest.class));
    }
}
