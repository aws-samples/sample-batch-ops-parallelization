package com.amazon.tdm.s3a.service.resources.monitor;

import com.amazon.tdm.s3a.persistence.model.BopsJobDetails;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;

import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.DescribeJobRequest;
import software.amazon.awssdk.services.s3control.model.JobDescriptor;
import software.amazon.awssdk.services.s3control.model.DescribeJobResponse;
import software.amazon.awssdk.services.s3control.model.JobReport;
import software.amazon.awssdk.services.s3control.model.JobStatus;
import software.amazon.awssdk.services.s3control.model.JobProgressSummary;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import com.amazon.tdm.s3a.service.resources.monitor.S3Monitor.BOPSStatus;
import com.amazon.tdm.s3a.service.resources.monitor.S3Monitor.CRRStatus;
import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import software.amazon.awssdk.services.s3control.model.JobTimers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;


class S3MonitorTest {
    private static final String ACCOUNT_ID = "123456789";
    private static final String JOB_ID = "job-123";
    private static final String REGION = "eu-west-1";
    private static final String BUCKET_ARN = "arn:aws:s3:::test-bucket";
    private static final String DEST_BUCKET_ARN = "arn:aws:s3:::test-bucket-dest";
    private static final String PREFIX = "reports";
    @Mock
    private S3ControlClient s3ControlClient;

    @Mock
    private CloudWatchClient cloudwatchClient;

    @Mock
    private WorkFlowModel workFlowModel;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        workFlowModel = WorkFlowModel.builder()
                .sourceRegion(REGION)
                .destRegion(REGION)
                .workflowName("test-workflow")
                .namespaceID("test-namespace")
                .sourceAccountNumber(ACCOUNT_ID)
                .sourceBucketARN(BUCKET_ARN)
                .destBucketARN(DEST_BUCKET_ARN)
                .build();
    }

    @Test
    void testBOPSStatusValues() {
        assertEquals(BOPSStatus.BOPS_RUNNING, BOPSStatus.valueOf("BOPS_RUNNING"));
        assertEquals(BOPSStatus.BOPS_FAILED, BOPSStatus.valueOf("BOPS_FAILED"));
        assertEquals(BOPSStatus.BOPS_FINISHED, BOPSStatus.valueOf("BOPS_FINISHED"));
        assertEquals(BOPSStatus.BOPS_STATUS_ERROR, BOPSStatus.valueOf("BOPS_STATUS_ERROR"));

    }

    @Test
    void testCRRStatusValues() {
        assertEquals(CRRStatus.CRR_RUNNING, CRRStatus.valueOf("CRR_RUNNING"));
        assertEquals(CRRStatus.CRR_FAILED, CRRStatus.valueOf("CRR_FAILED"));
        assertEquals(CRRStatus.CRR_FINISHED, CRRStatus.valueOf("CRR_FINISHED"));
        assertEquals(CRRStatus.CRR_STATUS_ERROR, CRRStatus.valueOf("CRR_STATUS_ERROR"));

    }

    @Test
    void testGetBOPSJobStatus_Exception() {
        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenThrow(S3Exception.class);

        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        assertEquals(BOPSStatus.BOPS_STATUS_ERROR.name(), bopsJobDetails.getJobStatus());
        Mockito.verify(s3ControlClient, Mockito.times(1))
                .describeJob(Mockito.any(DescribeJobRequest.class));
    }

    @Test
    void calculateBOPSJobStatus_ShouldReturnBOPSComplete_WhenJobComplete() {
        // Arrange
        Instant currentTimestamp = Instant.now();
        JobDescriptor job = JobDescriptor.builder()
                .status(JobStatus.COMPLETE)
                .creationTime(currentTimestamp)
                .terminationDate(currentTimestamp)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(0L)
                        .numberOfTasksSucceeded(1L)
                        .totalNumberOfTasks(1L)
                        .build())
                .build();
        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();
        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        Assertions.assertEquals(BOPSStatus.BOPS_FINISHED.name(), bopsJobDetails.getJobStatus());
        Assertions.assertEquals(currentTimestamp.toEpochMilli(), bopsJobDetails.getCreationTime());
        Assertions.assertEquals(currentTimestamp.toEpochMilli(), bopsJobDetails.getTerminationTime());
        Mockito.verify(s3ControlClient, Mockito.times(1))
                .describeJob(Mockito.any(DescribeJobRequest.class));
    }

    @Test
    void calculateBOPSJobStatus_ShouldReturnBOPSRunning() {
        // Arrange
        String accountId = "123456789";
        String jobId = "job-id-123";
        Instant currentTimestamp = Instant.now();
        JobDescriptor job = JobDescriptor.builder()
                .status(JobStatus.ACTIVE)
                .creationTime(currentTimestamp)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(0L)
                        .numberOfTasksSucceeded(5L)
                        .totalNumberOfTasks(10L)
                        .build())
                .build();
        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();
        Mockito.when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, accountId, jobId);

        // Assert
        Assertions.assertEquals(BOPSStatus.BOPS_RUNNING.name(), bopsJobDetails.getJobStatus());
        Assertions.assertEquals(currentTimestamp.toEpochMilli(), bopsJobDetails.getCreationTime());
        Mockito.verify(s3ControlClient, Mockito.times(1))
                .describeJob(Mockito.any(DescribeJobRequest.class));
    }

    @Test
    void calculateBOPSJobStatus_ShouldReturnBOPSFailed_WhenJobComplete() {
        // Arrange
        String accountId = "123456789";
        String jobId = "job-id-123";
        Instant currentTimestamp = Instant.now();
        JobDescriptor job = JobDescriptor.builder()
                .status(JobStatus.FAILED)
                .terminationDate(currentTimestamp)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(50L)
                        .numberOfTasksSucceeded(40L)
                        .totalNumberOfTasks(100L)
                        .build())
                .build();
        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();
        Mockito.when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, accountId, jobId);

        // Assert
        Assertions.assertEquals(BOPSStatus.BOPS_FAILED.name(), bopsJobDetails.getJobStatus());
        Assertions.assertEquals(currentTimestamp.toEpochMilli(), bopsJobDetails.getTerminationTime());
        Mockito.verify(s3ControlClient, Mockito.times(1))
                .describeJob(Mockito.any(DescribeJobRequest.class));
    }

    @Test
    void calculateBOPSJobStatus_ShouldReturnBOPSFailed_WhenJobCompleteMissingTS() {
        // Arrange
        String accountId = "123456789";
        String jobId = "job-id-123";
        JobDescriptor job = JobDescriptor.builder()
                .status(JobStatus.FAILED)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(50L)
                        .numberOfTasksSucceeded(40L)
                        .totalNumberOfTasks(100L)
                        .build())
                .build();
        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();
        Mockito.when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, accountId, jobId);

        // Assert
        Assertions.assertEquals(BOPSStatus.BOPS_FAILED.name(), bopsJobDetails.getJobStatus());
        Mockito.verify(s3ControlClient, Mockito.times(1))
                .describeJob(Mockito.any(DescribeJobRequest.class));
    }

    @Test
    void calculateBOPSPctProgress() {
        // Arrange
        String expectedProgress = "36.32";
        JobDescriptor job = JobDescriptor.builder()
                .status(JobStatus.COMPLETE)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(30L)
                        .numberOfTasksSucceeded(55L)
                        .totalNumberOfTasks(234L)
                        .build())
                .build();
        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();
        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert        
        String roundedPct = String.format("%.2f", bopsJobDetails.getPercentProgress());
        assertEquals(expectedProgress, roundedPct);
        Mockito.verify(s3ControlClient, Mockito.times(1))
                .describeJob(Mockito.any(DescribeJobRequest.class));
    }

    @Test
    void calculateBOPSPctProgress_atStart() {
        // Arrange
        String expectedProgress = "0.00";
        JobDescriptor job = JobDescriptor.builder()
                .status(JobStatus.COMPLETE)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(0L)
                        .numberOfTasksSucceeded(0L)
                        .totalNumberOfTasks(0L)
                        .build())
                .build();
        DescribeJobResponse describeJobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();
        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(describeJobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert        
        String roundedPct = String.format("%.2f", bopsJobDetails.getPercentProgress());
        assertEquals(expectedProgress, roundedPct);
        Mockito.verify(s3ControlClient, Mockito.times(1))
                .describeJob(Mockito.any(DescribeJobRequest.class));
    }

    @Test
    void getS3JobCompletionReportUrl_WithValidInput() {
        // Arrange
        JobReport jobReport = JobReport.builder()
                .enabled(true)
                .bucket(BUCKET_ARN)
                .prefix(PREFIX)
                .build();

        JobDescriptor job = JobDescriptor.builder()
                .report(jobReport)
                .jobId(JOB_ID)
                .status(JobStatus.COMPLETE)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(0L)
                        .numberOfTasksSucceeded(0L)
                        .totalNumberOfTasks(0L)
                        .build())
                .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();

        // Expected URL
        String expectedPrefix = URLEncoder.encode(PREFIX + "/job-" + JOB_ID + "/", StandardCharsets.UTF_8);
        String expectedUrl = String.format("https://%s.console.aws.amazon.com/s3/buckets/%s?region=%s&prefix=%s",
                REGION, "test-bucket", REGION, expectedPrefix);

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(jobResponse);

        // Act
    BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertEquals(expectedUrl, bopsJobDetails.getJobCompletionReportUrl());
    }

    @Test
    void getS3JobCompletionReportUrl_WithDisabledReport() {
        // Arrange
        JobReport jobReport = JobReport.builder()
                .enabled(false)
                .bucket("arn:aws:s3:::test-bucket")
                .prefix("reports")
                .build();

        JobDescriptor job = JobDescriptor.builder()
                .report(jobReport)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(0L)
                        .numberOfTasksSucceeded(0L)
                        .totalNumberOfTasks(0L)
                        .build())
                .status(JobStatus.COMPLETE)
                .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(jobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertNull(bopsJobDetails.getJobCompletionReportUrl());
    }

    @Test
    void getS3JobCompletionReportUrl_WithNullBucket() {
        // Arrange
        JobReport jobReport = JobReport.builder()
                .enabled(true)
                .bucket(null)
                .prefix("reports")
                .build();

        JobDescriptor job = JobDescriptor.builder()
                .report(jobReport)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(0L)
                        .numberOfTasksSucceeded(0L)
                        .totalNumberOfTasks(0L)
                        .build())
                .status(JobStatus.COMPLETE)
                .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(jobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertNull(bopsJobDetails.getJobCompletionReportUrl());
    }

    @Test
    void getS3JobCompletionReportUrl_WithNullPrefix() {
        // Arrange
        JobReport jobReport = JobReport.builder()
                .enabled(true)
                .bucket("arn:aws:s3:::test-bucket")
                .prefix(null)
                .build();

        JobDescriptor job = JobDescriptor.builder()
                .report(jobReport)
                .status(JobStatus.COMPLETE)
                .progressSummary(JobProgressSummary.builder()
                        .numberOfTasksFailed(0L)
                        .numberOfTasksSucceeded(0L)
                        .totalNumberOfTasks(0L)
                        .build())
                .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(jobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertNull(bopsJobDetails.getJobCompletionReportUrl());
    }

    @Test
    void getElapsedTimeInActiveSeconds_WithValidTimers() {
        // Arrange
        Long expectedSeconds = 3600L;
        JobTimers jobTimers = JobTimers.builder()
                .elapsedTimeInActiveSeconds(expectedSeconds)
                .build();

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .timers(jobTimers)
                .numberOfTasksFailed(0L)
                .numberOfTasksSucceeded(0L)
                .totalNumberOfTasks(0L)
                .build();

        JobDescriptor job = JobDescriptor.builder()
                .progressSummary(progressSummary)
                .status(JobStatus.COMPLETE)
                .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(jobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertEquals(expectedSeconds, bopsJobDetails.getElapsedTimeInActiveSeconds());
    }

    @Test
    void getElapsedTimeInActiveSeconds_WithNullTimers() {
        // Arrange
        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .timers(JobTimers.builder()
                        .build())
                .numberOfTasksFailed(0L)
                .numberOfTasksSucceeded(0L)
                .totalNumberOfTasks(0L)
                .build();

        JobDescriptor job = JobDescriptor.builder()
                .progressSummary(progressSummary)
                .status(JobStatus.COMPLETE)
                .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(jobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertEquals(0L, bopsJobDetails.getElapsedTimeInActiveSeconds());
    }

    @Test
    void getElapsedTimeInActiveSeconds_WithNullElapsedTime() {
        // Arrange
        JobTimers jobTimers = JobTimers.builder()
                .elapsedTimeInActiveSeconds(null)
                .build();

        JobProgressSummary progressSummary = JobProgressSummary.builder()
                .timers(jobTimers)
                .numberOfTasksFailed(0L)
                .numberOfTasksSucceeded(0L)
                .totalNumberOfTasks(0L)
                .build();

        JobDescriptor job = JobDescriptor.builder()
                .progressSummary(progressSummary)
                .status(JobStatus.COMPLETE)
                .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
                .job(job)
                .build();

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
                .thenReturn(jobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertEquals(0L, bopsJobDetails.getElapsedTimeInActiveSeconds());
    }

    @Test
    void calculateBopsJobDetails_WithTerminationTime() {
        // Arrange
        JobProgressSummary progressSummary = JobProgressSummary.builder()
            .numberOfTasksFailed(0L)
            .numberOfTasksSucceeded(0L)
            .totalNumberOfTasks(0L)
            .build();

        JobDescriptor job = JobDescriptor.builder()
            .progressSummary(progressSummary)
            .status(JobStatus.COMPLETE)
            .terminationDate(Instant.ofEpochMilli(100L))
            .build();

        DescribeJobResponse jobResponse = DescribeJobResponse.builder()
            .job(job)
            .build();

        when(s3ControlClient.describeJob(Mockito.any(DescribeJobRequest.class)))
            .thenReturn(jobResponse);

        // Act
        BopsJobDetails bopsJobDetails = S3Monitor.calculateBopsJobDetails(workFlowModel, s3ControlClient, ACCOUNT_ID, JOB_ID);

        // Assert
        assertEquals(100L, bopsJobDetails.getTerminationTime());
    }

    @Test
    void testGetCRRMetrics_Exception() {
        String bucketName = "test-bucket";
        String destBucketName = "test-target-bucket";

        when(cloudwatchClient.getMetricStatistics(Mockito.any(GetMetricStatisticsRequest.class)))
                .thenThrow(CloudWatchException.class);

        S3MonitorDetails actualResponse = S3Monitor.calculateCRRStatus(cloudwatchClient, bucketName, DEST_BUCKET_ARN, REGION, destBucketName);

        assertEquals(CRRStatus.CRR_STATUS_ERROR.name(), actualResponse.getCrrStatus());
        Mockito.verify(cloudwatchClient, Mockito.times(2))
                .getMetricStatistics(Mockito.any(GetMetricStatisticsRequest.class));
    }

    @Test
    void calculateCRRStatus_CRR_FINISHED() {
        String bucketName = "test-bucket";
        String destBucketName = "test-target-bucket";
        GetMetricStatisticsResponse metricsResponse = GetMetricStatisticsResponse.builder()
                        .datapoints(Datapoint.builder().maximum(0.0).build()).build();
        when(cloudwatchClient.getMetricStatistics(Mockito.any(GetMetricStatisticsRequest.class)))
                .thenReturn(metricsResponse);

        S3MonitorDetails actualResponse = S3Monitor.calculateCRRStatus(cloudwatchClient, bucketName, DEST_BUCKET_ARN, REGION, destBucketName);
        assertEquals(CRRStatus.CRR_FINISHED.name(), actualResponse.getCrrStatus());
    }

    @Test
    public void testWorkflowFinished() {
        String result = S3Monitor.deriveWorkflowStatus(
            "STOP_SOURCE_TRAFFIC_ACK", 
            BOPSStatus.BOPS_FINISHED.name(), 
            CRRStatus.CRR_FINISHED.name(),
                WorkflowStatus.RUNNING.name()
        );
        assertEquals(WorkflowStatus.FINISHED.name(), result);
    }

    @Test
    public void testWorkflowWaiting() {
        String result = S3Monitor.deriveWorkflowStatus(
            "ANY", 
            BOPSStatus.BOPS_FINISHED.name(), 
            CRRStatus.CRR_RUNNING.name(),
                WorkflowStatus.RUNNING.name()
        );
        assertEquals(WorkflowStatus.WAITING.name(), result);
    }

    @Test
    public void testWorkflowRunning() {
        String result = S3Monitor.deriveWorkflowStatus(
            "ANY", 
            BOPSStatus.BOPS_RUNNING.name(), 
            CRRStatus.CRR_RUNNING.name(),
                WorkflowStatus.RUNNING.name()
        );
        assertEquals(WorkflowStatus.RUNNING.name(), result);
    }

    @Test
    public void testWorkflowFAILED() {
        String result = S3Monitor.deriveWorkflowStatus(
            "ANY", 
            BOPSStatus.BOPS_FAILED.name(), 
            CRRStatus.CRR_RUNNING.name(),
                WorkflowStatus.RUNNING.name()
        );
        assertEquals(WorkflowStatus.FAILED.name(), result);
    }

    @Test
    public void testWorkflowStopping_BopsRunning() {
        String result = S3Monitor.deriveWorkflowStatus(
                "ANY",
                BOPSStatus.BOPS_RUNNING.name(),
                CRRStatus.CRR_RUNNING.name(),
                WorkflowStatus.STOPPING.name()
        );
        Assertions.assertEquals(WorkflowStatus.STOPPING.name(), result);
    }

    @Test
    public void testWorkflowStopped_BopsFinished() {
        String result = S3Monitor.deriveWorkflowStatus(
                "ANY",
                BOPSStatus.BOPS_FINISHED.name(),
                CRRStatus.CRR_RUNNING.name(),
                WorkflowStatus.STOPPING.name()
        );
        Assertions.assertEquals(WorkflowStatus.STOPPED.name(), result);
    }

    @Test
    public void testWorkflowStopped_BopsFailed() {
        String result = S3Monitor.deriveWorkflowStatus(
                "ANY",
                BOPSStatus.BOPS_FAILED.name(),
                CRRStatus.CRR_RUNNING.name(),
                WorkflowStatus.STOPPING.name()
        );
        Assertions.assertEquals(WorkflowStatus.STOPPED.name(), result);
    }
}