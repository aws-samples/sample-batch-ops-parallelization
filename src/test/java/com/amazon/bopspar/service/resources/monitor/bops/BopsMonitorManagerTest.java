package com.amazon.bopspar.service.resources.monitor.bops;

import com.amazon.bopspar.persistence.model.BopsJobDetails;
import com.amazon.bopspar.persistence.model.MonitoringDetails;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.monitor.MonitorConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.DescribeJobRequest;
import software.amazon.awssdk.services.s3control.model.DescribeJobResponse;
import software.amazon.awssdk.services.s3control.model.JobDescriptor;
import software.amazon.awssdk.services.s3control.model.JobProgressSummary;
import software.amazon.awssdk.services.s3control.model.JobStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BopsMonitorManagerTest {

    @Mock
    private S3ControlClient s3ControlClient;

    @Mock
    private WorkFlowModel workflowModel;

    private BopsMonitorManager bopsMonitorManager;

    @BeforeEach
    void setUp() {
        bopsMonitorManager = new BopsMonitorManager();
    }

    @Test
    void testGetIndividualBopsJobDetails_Error() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenThrow(S3Exception.builder().message("Error").build());

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(any());
    }

    @Test
    void testCalculateAggregateMonitoringDetails_Running() {
        // Arrange
        BopsJobDetails job1 = BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .numOfTotalTasks(10L)
                .numOfTasksSucceeded(5L)
                .numOfTasksFailed(0L)
                .creationTime(1000L)
                .build();

        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        jobDetailsMap.put("job-1", job1);

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name()) &&
                        details.getBopsPercentProgress() == 50.0
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_MultipleJobs() {
        // Arrange
        when(workflowModel.getBopsJobIds()).thenReturn(List.of("job1", "job2"));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary1 = JobProgressSummary.builder()
                .numberOfTasksFailed(0L)
                .numberOfTasksSucceeded(50L)
                .totalNumberOfTasks(100L)
                .build();

        JobProgressSummary progressSummary2 = JobProgressSummary.builder()
                .numberOfTasksFailed(10L)
                .numberOfTasksSucceeded(40L)
                .totalNumberOfTasks(100L)
                .build();

        JobDescriptor jobDescriptor1 = JobDescriptor.builder()
                .jobId("job1")
                .status(JobStatus.COMPLETE)
                .progressSummary(progressSummary1)
                .creationTime(Instant.now())
                .terminationDate(Instant.now())
                .build();

        JobDescriptor jobDescriptor2 = JobDescriptor.builder()
                .jobId("job2")
                .status(JobStatus.FAILED)
                .progressSummary(progressSummary2)
                .creationTime(Instant.now())
                .terminationDate(Instant.now())
                .build();

        DescribeJobResponse response1 = DescribeJobResponse.builder()
                .job(jobDescriptor1)
                .build();

        DescribeJobResponse response2 = DescribeJobResponse.builder()
                .job(jobDescriptor2)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.size() == 2 &&
                        map.containsKey("job1") &&
                        map.containsKey("job2")
        ));
    }

    @Test
    void testCalculateAggregateMonitoringDetails_AllFailed() {
        // Arrange
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        jobDetailsMap.put("job1", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .numOfTotalTasks(100L)
                .numOfTasksSucceeded(50L)
                .numOfTasksFailed(50L)
                .creationTime(1000L)
                .terminationTime(2000L)
                .build());
        jobDetailsMap.put("job2", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .numOfTotalTasks(100L)
                .numOfTasksSucceeded(25L)
                .numOfTasksFailed(75L)
                .creationTime(1500L)
                .terminationTime(2500L)
                .build());

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FAILED.name()) &&
                        details.getBopsNumOfTasksFailed() == 125L &&
                        details.getBopsNumOfTasksSucceeded() == 75L &&
                        details.getBopsNumOfTotalTasks() == 200L &&
                        details.getBopsPercentProgress() == 100.0 &&
                        details.getCreationTime() == 1000L &&
                        details.getTerminationTime() == 2500L
        ));
    }

    @Test
    void testCalculateAggregateMonitoringDetails_EmptyJobDetails() {
        // Arrange
        when(workflowModel.getBopsJobDetails()).thenReturn(new HashMap<>());

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FINISHED.name()) &&
                        details.getBopsNumOfTasksFailed() == 0L &&
                        details.getBopsNumOfTasksSucceeded() == 0L &&
                        details.getBopsNumOfTotalTasks() == 0L &&
                        details.getBopsPercentProgress() == 0.0 &&
                        details.getCreationTime() == 0L &&
                        details.getTerminationTime() == 0L
        ));
    }

    @Test
    void testCalculateAggregateMonitoringDetails_NullFieldHandling() {
        // Arrange
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        jobDetailsMap.put("job1", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .sdkJobStatus(JobStatus.ACTIVE.toString())
                .numOfTotalTasks(null)
                .numOfTasksSucceeded(null)
                .numOfTasksFailed(null)
                .creationTime(null)
                .terminationTime(null)
                .build());

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name()) &&
                        details.getBopsNumOfTasksFailed() == 0L &&
                        details.getBopsNumOfTasksSucceeded() == 0L &&
                        details.getBopsNumOfTotalTasks() == 0L &&
                        details.getBopsPercentProgress() == 0.0 &&
                        details.getCreationTime() == 0L &&
                        details.getTerminationTime() == 0L
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_ErrorHandling() {
        // Arrange
        when(workflowModel.getBopsJobIds()).thenReturn(List.of("job1"));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");
        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Service unavailable")
                        .statusCode(503)
                        .build());

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.size() == 1 &&
                        map.get("job1").getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_STATUS_ERROR.name())
        ));
    }

    @Test
    void testCalculateAggregateMonitoringDetails_StatusPrioritization() {
        // Arrange
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        // Mix of different statuses to test prioritization
        jobDetailsMap.put("job1", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .build());
        jobDetailsMap.put("job2", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .build());
        jobDetailsMap.put("job3", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .build());
        jobDetailsMap.put("job4", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_STATUS_ERROR.name())
                .build());

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                // BOPS_RUNNING should take precedence
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
        ));
    }

    @Test
    void testCalculateAggregateMonitoringDetails_StatusErrorCase() {
        // Arrange
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        jobDetailsMap.put("job1", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .numOfTotalTasks(100L)
                .numOfTasksSucceeded(100L)
                .numOfTasksFailed(0L)
                .creationTime(1000L)
                .terminationTime(2000L)
                .build());
        jobDetailsMap.put("job2", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_STATUS_ERROR.name())
                .numOfTotalTasks(100L)
                .numOfTasksSucceeded(50L)
                .numOfTasksFailed(0L)
                .creationTime(1500L)
                .terminationTime(null)
                .build());

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_STATUS_ERROR.name()) &&
                        details.getBopsNumOfTasksFailed() == 0L &&
                        details.getBopsNumOfTasksSucceeded() == 150L &&
                        details.getBopsNumOfTotalTasks() == 200L &&
                        details.getBopsPercentProgress() == 75.0 &&
                        details.getCreationTime() == 1000L &&
                        details.getTerminationTime() == 2000L
        ));
    }

    @Test
    void testCalculateAggregateMonitoringDetails_AllFailingStatuses() {
        // Arrange
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        // Test all failing status variations
        jobDetailsMap.put("job1", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .sdkJobStatus(JobStatus.FAILED.toString())
                .build());
        jobDetailsMap.put("job2", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .sdkJobStatus(JobStatus.FAILING.toString())
                .build());
        jobDetailsMap.put("job3", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .sdkJobStatus(JobStatus.CANCELLED.toString())
                .build());
        jobDetailsMap.put("job4", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .sdkJobStatus(JobStatus.CANCELLING.toString())
                .build());
        jobDetailsMap.put("job5", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .sdkJobStatus(JobStatus.SUSPENDED.toString())
                .build());
        jobDetailsMap.put("job6", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .sdkJobStatus(JobStatus.UNKNOWN_TO_SDK_VERSION.toString())
                .build());

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
        ));
    }

    @Test
    void testCalculateAggregateMonitoringDetails_NullCreationTime() {
        // Arrange
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        jobDetailsMap.put("job1", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .creationTime(null)
                .terminationTime(2000L)
                .build());

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDuration("0");
    }

    @Test
    void testCalculateAggregateMonitoringDetails_ActiveJobDuration() {
        // Arrange
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        long creationTime = currentTime - 120000; // 2 minutes ago

        jobDetailsMap.put("job1", BopsJobDetails.builder()
                .jobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .creationTime(creationTime)
                .build());

        when(workflowModel.getBopsJobDetails()).thenReturn(jobDetailsMap);

        // Act
        bopsMonitorManager.calculateAggregateMonitoringDetails(workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDuration(argThat(duration ->
                Integer.parseInt(duration) >= 2 // At least 2 minutes
        ));
    }

    @Test
    void testCalculateBopsJobDetails_ZeroTotalTasks() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.COMPLETE)
                .progressSummary(JobProgressSummary.builder()
                        .totalNumberOfTasks(0L)
                        .numberOfTasksSucceeded(0L)
                        .numberOfTasksFailed(0L)
                        .build())
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getPercentProgress() == 0.0
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_FailedTasksNoTerminationDate() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .numberOfTasksFailed(1L)
                .numberOfTasksSucceeded(9L)
                .totalNumberOfTasks(10L)
                .build();

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.COMPLETE)
                .progressSummary(progressSummary)
                .creationTime(Instant.now())
                // No terminationDate set
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FAILED.name()) &&
                        map.get(jobId).getTerminationTime() == null
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_FailingStatusNoTerminationDate() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .numberOfTasksFailed(0L)
                .numberOfTasksSucceeded(5L)
                .totalNumberOfTasks(10L)
                .build();

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.FAILING)
                .progressSummary(progressSummary)
                .creationTime(Instant.now())
                // No terminationDate set
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FAILED.name()) &&
                        map.get(jobId).getTerminationTime() == null
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_RunningState() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .numberOfTasksFailed(0L)
                .numberOfTasksSucceeded(5L)
                .totalNumberOfTasks(10L)
                .build();

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.ACTIVE)
                .progressSummary(progressSummary)
                .creationTime(Instant.now())
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_CompleteWithNoFailedTasks() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .numberOfTasksFailed(0L)  // Explicitly set to 0
                .numberOfTasksSucceeded(10L)
                .totalNumberOfTasks(10L)
                .build();

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.COMPLETE)  // Explicitly set to COMPLETE
                .progressSummary(progressSummary)
                .creationTime(Instant.now())
                .terminationDate(Instant.now())
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FINISHED.name()) &&
                        map.get(jobId).getNumOfTasksFailed() == 0L &&
                        map.get(jobId).getSdkJobStatus().equals(JobStatus.COMPLETE.toString())
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_CompleteWithFailedTasks() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .numberOfTasksFailed(1L)  // Has failed tasks
                .numberOfTasksSucceeded(9L)
                .totalNumberOfTasks(10L)
                .build();

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.COMPLETE)  // Status is COMPLETE
                .progressSummary(progressSummary)
                .creationTime(Instant.now())
                .terminationDate(Instant.now())
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FAILED.name()) &&
                        map.get(jobId).getNumOfTasksFailed() == 1L &&
                        map.get(jobId).getSdkJobStatus().equals(JobStatus.COMPLETE.toString())
        ));
    }

    @Test
    void testGetIndividualBopsJobDetails_NegativeFailedTasks() {
        // Arrange
        String jobId = "job-123";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .numberOfTasksFailed(-1L)  // Negative number of failed tasks
                .numberOfTasksSucceeded(10L)
                .totalNumberOfTasks(10L)
                .build();

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.COMPLETE)
                .progressSummary(progressSummary)
                .creationTime(Instant.now())
                .terminationDate(Instant.now())
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name()) &&
                        map.get(jobId).getNumOfTasksFailed() == -1L &&
                        map.get(jobId).getSdkJobStatus().equals(JobStatus.COMPLETE.toString())
        ));
    }
    @Test
    void testGetIndividualBopsJobDetails_AllFailingStatuses() {
        List<JobStatus> failingStatuses = List.of(
                JobStatus.FAILED,
                JobStatus.FAILING,
                JobStatus.CANCELLED,
                JobStatus.CANCELLING,
                JobStatus.SUSPENDED,
                JobStatus.UNKNOWN_TO_SDK_VERSION
        );

        for (JobStatus status : failingStatuses) {
            // Arrange
            String jobId = "job-" + status.toString();
            when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
            when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

            JobProgressSummary progressSummary = JobProgressSummary.builder()
                    .numberOfTasksFailed(0L)
                    .numberOfTasksSucceeded(5L)
                    .totalNumberOfTasks(10L)
                    .build();

            JobDescriptor jobDescriptor = JobDescriptor.builder()
                    .jobId(jobId)
                    .status(status)
                    .progressSummary(progressSummary)
                    .creationTime(Instant.now())
                    .build();

            DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                    .job(jobDescriptor)
                    .build();

            when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                    .thenReturn(describeJobResponse);

            // Act
            bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

            // Assert
            verify(workflowModel).setBopsJobDetails(argThat(map ->
                    map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FAILED.name()) &&
                            map.get(jobId).getSdkJobStatus().equals(status.toString())
            ));

            // Reset mocks for the next iteration
            reset(workflowModel, s3ControlClient);
        }
    }

    @Test
    void testGetIndividualBopsJobDetails_NonFailingStatus() {
        // Arrange
        String jobId = "job-active";
        when(workflowModel.getBopsJobIds()).thenReturn(List.of(jobId));
        when(workflowModel.getSourceAccountNumber()).thenReturn("123456789012");

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .numberOfTasksFailed(0L)
                .numberOfTasksSucceeded(5L)
                .totalNumberOfTasks(10L)
                .build();

        JobDescriptor jobDescriptor = JobDescriptor.builder()
                .jobId(jobId)
                .status(JobStatus.ACTIVE)  // Non-failing status
                .progressSummary(progressSummary)
                .creationTime(Instant.now())
                .build();

        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(jobDescriptor)
                .build();

        when(s3ControlClient.describeJob(any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        bopsMonitorManager.getIndividualBopsJobDetails(s3ControlClient, workflowModel);

        // Assert
        verify(workflowModel).setBopsJobDetails(argThat(map ->
                map.get(jobId).getJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name()) &&
                        map.get(jobId).getSdkJobStatus().equals(JobStatus.ACTIVE.toString())
        ));
    }
}