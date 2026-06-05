package com.amazon.bopspar.service.resources.monitor;

import com.amazon.bopspar.persistence.model.BopsJobDetails;
import com.amazon.bopspar.persistence.model.MonitoringDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.monitor.S3MonitorManager.WorkflowStatus;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.s3control.S3ControlClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class S3MonitorManagerTest {
    @Mock
    private S3ControlClient s3ControlClient;

    @Mock
    private CloudWatchClient cloudwatchClient;

    private WorkFlowModel workflowModel;

    private S3MonitorManager s3MonitorManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        workflowModel = WorkFlowModel.builder()
                .workflowName("test-workflow")
                .namespaceID("test-namespace")
                .sourceAccountNumber("123456789")
                .sourceBucketARN("arn:aws:s3:::test-bucket")
                .destBucketARN("arn:aws:s3:::test-bucket2")
                .bopsJobIds(Collections.singletonList("test-bops-job-id"))
                .sourceRoleARN("test-role")
                .sourceRegion("us-east-1")
                .destRoleARN("test-role")
                .destRegion("us-east-1")
                .status(WorkflowStatus.RUNNING.name())
                .build();
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus("RUNNING")
                .build();
        workflowModel.setMonitoringDetails(monitoringDetails);
        s3MonitorManager = S3MonitorManager.builder().workflowModel(workflowModel).build();
    }


    @Test
    void testMonitorWorkflowStatusValues() {
        Assertions.assertEquals(WorkflowStatus.RUNNING, WorkflowStatus.valueOf("RUNNING"));
        Assertions.assertEquals(WorkflowStatus.FAILED, WorkflowStatus.valueOf("FAILED"));
        Assertions.assertEquals(WorkflowStatus.UNKNOWN, WorkflowStatus.valueOf("UNKNOWN"));
        Assertions.assertEquals(WorkflowStatus.WAITING, WorkflowStatus.valueOf("WAITING"));
        Assertions.assertEquals(WorkflowStatus.FINISHED, WorkflowStatus.valueOf("FINISHED"));

    }

    @Test
    void testPublishCWMetricsSuccess() {
        MonitoringDetails s3MonitorDetails = MonitoringDetails.builder()
                .crrBytesPendingReplication(1000L)
                .crrLatency(60L)
                .bopsNumOfTotalTasks(100L)
                .bopsNumOfTasksFailed(10L)
                .bopsNumOfTasksSucceeded(90L)
                .bopsPercentProgress(90.0)
                .build();
        S3MonitorManager lMonitorManager = new S3MonitorManager(
            WorkFlowModel.builder()
                    .sourceBucketARN("arn:aws:s3:::source-bucket")
                    .destBucketARN("arn:aws:s3:::dest-bucket")
                    .monitoringDetails(s3MonitorDetails)
                    .status("FAILED")
                    .build());

        when(cloudwatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        boolean result = lMonitorManager.publishCWMetrics(cloudwatchClient);

        // Assert
        Assertions.assertTrue(result);
        Mockito.verify(cloudwatchClient, Mockito.times(1))
            .putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testPublishCWMetrics_WorkflowRunning() {
        MonitoringDetails s3MonitorDetails = MonitoringDetails.builder()
                .crrBytesPendingReplication(1000L)
                .crrLatency(60L)
                .bopsNumOfTotalTasks(100L)
                .bopsNumOfTasksFailed(10L)
                .bopsNumOfTasksSucceeded(90L)
                .bopsPercentProgress(90.0)
                .build();
        S3MonitorManager lMonitorManager = new S3MonitorManager(
                WorkFlowModel.builder()
                        .sourceBucketARN("arn:aws:s3:::source-bucket")
                        .destBucketARN("arn:aws:s3:::dest-bucket")
                        .monitoringDetails(s3MonitorDetails)
                        .status(String.valueOf(WorkflowStatus.RUNNING))
                        .build());

        when(cloudwatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(PutMetricDataResponse.builder().build());

        // Act
        boolean result = lMonitorManager.publishCWMetrics(cloudwatchClient);

        // Assert
        Assertions.assertTrue(result);
        Mockito.verify(cloudwatchClient, Mockito.times(1))
                .putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testPublishCWMetrics_ThrowsException() {
        MonitoringDetails s3MonitorDetails = MonitoringDetails.builder()
                .crrBytesPendingReplication(1000L)
                .crrLatency(60L)
                .bopsNumOfTotalTasks(100L)
                .bopsNumOfTasksFailed(10L)
                .bopsNumOfTasksSucceeded(90L)
                .bopsPercentProgress(90.0)
                .build();
        S3MonitorManager lMonitorManager = new S3MonitorManager(
                WorkFlowModel.builder()
                        .sourceBucketARN("arn:aws:s3:::source-bucket")
                        .destBucketARN("arn:aws:s3:::dest-bucket")
                        .monitoringDetails(s3MonitorDetails)
                        .status(String.valueOf(WorkflowStatus.RUNNING))
                        .build());

        when(cloudwatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(CloudWatchException.class);

        // Act
        boolean result = lMonitorManager.publishCWMetrics(cloudwatchClient);

        // Assert
        Assertions.assertFalse(result);
        Mockito.verify(cloudwatchClient, Mockito.times(1))
                .putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void createCloudWatchAlarm_NewAlarm_Exception() {
        String alarmName = "S3AFailedAlarm-source-bucket" ;        
        DescribeAlarmsRequest request = DescribeAlarmsRequest.builder()
                        .alarmNames(alarmName)
                        .build();
        when(cloudwatchClient.describeAlarms(request)).thenThrow(CloudWatchException.class); 

        when(cloudwatchClient.putMetricAlarm(any(PutMetricAlarmRequest.class)))
            .thenThrow(new RuntimeException("Test exception"));
        s3MonitorManager.createCloudWatchAlarm(cloudwatchClient);

        // Assert
        Mockito.verify(cloudwatchClient).putMetricAlarm(any(PutMetricAlarmRequest.class));
    }

    @Test
    void calculateAggregateMonitoringDetails_Running_Success() {
        workflowModel.setBopsJobIds(List.of("job123", "job456"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .jobStatus("BOPS_RUNNING")
                        .sdkJobStatus("Complete")
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(100L)
                        .numOfTotalTasks(100L)
                        .percentProgress(100.0)
                        .creationTime(10000000L)
                        .terminationTime(0L)
                        .build(),
                "job456", BopsJobDetails.builder()
                        .jobStatus("BOPS_RUNNING")
                        .sdkJobStatus("Complete")
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(100L)
                        .numOfTotalTasks(100L)
                        .percentProgress(100.0)
                        .creationTime(10000000L)
                        .terminationTime(0L)
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals("BOPS_RUNNING", monitoringDetails.getBopsJobStatus());
        assertEquals(0L, monitoringDetails.getBopsNumOfTasksFailed());
        assertEquals(200L, monitoringDetails.getBopsNumOfTasksSucceeded());
        assertEquals(200L, monitoringDetails.getBopsNumOfTotalTasks());
        assertEquals(100.0, monitoringDetails.getBopsPercentProgress());
        assertEquals(10000000L, monitoringDetails.getCreationTime());
        assertEquals(0L, monitoringDetails.getTerminationTime());
    }

    @Test
    void calculateAggregateMonitoringDetails_RunningAndFailed_Success() {
        workflowModel.setBopsJobIds(List.of("job123", "job456"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .jobStatus("BOPS_RUNNING")
                        .sdkJobStatus("Complete")
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(100L)
                        .numOfTotalTasks(100L)
                        .percentProgress(100.0)
                        .creationTime(10000000L)
                        .terminationTime(100000000000L)
                        .build(),
                "job456", BopsJobDetails.builder()
                        .jobStatus("BOPS_FAILED")
                        .sdkJobStatus("Failed")
                        .numOfTasksFailed(50L)
                        .numOfTasksSucceeded(50L)
                        .numOfTotalTasks(100L)
                        .percentProgress(100.0)
                        .creationTime(11000000L)
                        .terminationTime(110000000000L)
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals("BOPS_RUNNING", monitoringDetails.getBopsJobStatus());
        assertEquals(50L, monitoringDetails.getBopsNumOfTasksFailed());
        assertEquals(150L, monitoringDetails.getBopsNumOfTasksSucceeded());
        assertEquals(200L, monitoringDetails.getBopsNumOfTotalTasks());
        assertEquals(100.0, monitoringDetails.getBopsPercentProgress());
        assertEquals(10000000L, monitoringDetails.getCreationTime());
        assertEquals(0L, monitoringDetails.getTerminationTime());
    }

    @Test
    void calculateAggregateMonitoringDetails_SingleJob_Success() {
        workflowModel.setBopsJobIds(List.of("job123"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .jobStatus("BOPS_FINISHED")
                        .sdkJobStatus("Complete")
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(100L)
                        .numOfTotalTasks(100L)
                        .percentProgress(100.0)
                        .creationTime(10000000L)
                        .terminationTime(100000000000L)
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals("BOPS_FINISHED", monitoringDetails.getBopsJobStatus());
        assertEquals(0L, monitoringDetails.getBopsNumOfTasksFailed());
        assertEquals(100L, monitoringDetails.getBopsNumOfTasksSucceeded());
        assertEquals(100L, monitoringDetails.getBopsNumOfTotalTasks());
        assertEquals(100.0, monitoringDetails.getBopsPercentProgress());
        assertEquals(10000000L, monitoringDetails.getCreationTime());
        assertEquals(100000000000L, monitoringDetails.getTerminationTime());
        assertEquals(String.valueOf(1666500L), workflowModel.getBopsJobDuration());
    }

    @Test
    void calculateAggregateMonitoringDetails_TaskMetrics() {
        workflowModel.setBopsJobIds(List.of("job123", "job456", "job789"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .numOfTasksFailed(10L)
                        .numOfTasksSucceeded(100L)
                        .numOfTotalTasks(110L)
                        .jobStatus(String.valueOf(S3Monitor.BOPSStatus.BOPS_RUNNING))
                        .build(),
                "job456", BopsJobDetails.builder()
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .jobStatus(String.valueOf(S3Monitor.BOPSStatus.BOPS_RUNNING))
                        .build(),
                "job789", BopsJobDetails.builder()
                        .numOfTasksFailed(10000L)
                        .numOfTasksSucceeded(0L)
                        .numOfTotalTasks(10000L)
                        .jobStatus(String.valueOf(S3Monitor.BOPSStatus.BOPS_RUNNING))
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals(10010L, monitoringDetails.getBopsNumOfTasksFailed());
        assertEquals(1100L, monitoringDetails.getBopsNumOfTasksSucceeded());
        assertEquals(11110L, monitoringDetails.getBopsNumOfTotalTasks());
    }

    @Test
    void calculateAggregateMonitoringDetails_StatusMetrics_Running() {
        workflowModel.setBopsJobIds(List.of("job123", "job456", "job789"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_RUNNING.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build(),
                "job456", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FAILED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build(),
                "job789", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_STATUS_ERROR.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals(S3Monitor.BOPSStatus.BOPS_RUNNING.name(), monitoringDetails.getBopsJobStatus());
    }

    @Test
    void calculateAggregateMonitoringDetails_StatusMetrics_Failed() {
        workflowModel.setBopsJobIds(List.of("job123", "job456", "job789"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FINISHED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build(),
                "job456", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FINISHED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build(),
                "job789", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FAILED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals(S3Monitor.BOPSStatus.BOPS_FAILED.name(), monitoringDetails.getBopsJobStatus());
    }

    @Test
    void calculateAggregateMonitoringDetails_StatusMetrics_StatusError() {
        workflowModel.setBopsJobIds(List.of("job123", "job456", "job789"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FINISHED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build(),
                "job456", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FINISHED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(1000L)
                        .numOfTotalTasks(1000L)
                        .build(),
                "job789", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_STATUS_ERROR.name())
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals(S3Monitor.BOPSStatus.BOPS_STATUS_ERROR.name(), monitoringDetails.getBopsJobStatus());
    }

    @Test
    void calculateAggregateMonitoringDetails_PercentProgress_ZeroTotalTasks() {
        workflowModel.setBopsJobIds(List.of("job123", "job456"));
        workflowModel.setBopsJobDetails(Map.of(
                "job123", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FINISHED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(0L)
                        .numOfTotalTasks(0L)
                        .build(),
                "job456", BopsJobDetails.builder()
                        .jobStatus(S3Monitor.BOPSStatus.BOPS_FINISHED.name())
                        .numOfTasksFailed(0L)
                        .numOfTasksSucceeded(0L)
                        .numOfTotalTasks(0L)
                        .build()
        ));

        assertDoesNotThrow(() -> s3MonitorManager.calculateAggregateMonitoringDetails());

        MonitoringDetails monitoringDetails = workflowModel.getMonitoringDetails();
        assertEquals(S3Monitor.BOPSStatus.BOPS_FINISHED.name(), monitoringDetails.getBopsJobStatus());
        assertEquals(0.0, monitoringDetails.getBopsPercentProgress());
    }

    @Test
    void testGetIndividualBopsJobDetails_Success() {
        try (MockedStatic<S3Monitor> s3MonitorMock = mockStatic(S3Monitor.class)) {
            workflowModel.setBopsJobIds(List.of("job123", "job456"));
            BopsJobDetails expectedDetailsJob123 = BopsJobDetails.builder()
                    .jobStatus("BOPS_FINISHED")
                    .sdkJobStatus("Complete")
                    .numOfTasksFailed(0L)
                    .numOfTasksSucceeded(100L)
                    .numOfTotalTasks(100L)
                    .percentProgress(100.0)
                    .creationTime(10000000L)
                    .terminationTime(100000000000L)
                    .build();
            BopsJobDetails expectedDetailsJob456 = BopsJobDetails.builder()
                    .jobStatus("BOPS_FAILED")
                    .sdkJobStatus("Failed")
                    .numOfTasksFailed(100L)
                    .numOfTasksSucceeded(10L)
                    .numOfTotalTasks(110L)
                    .percentProgress(100.0)
                    .creationTime(10L)
                    .terminationTime(10000L)
                    .build();
            s3MonitorMock.when(() -> S3Monitor.calculateBopsJobDetails(eq(workflowModel), eq(s3ControlClient), eq(workflowModel.getSourceAccountNumber()), eq("job123")))
                            .thenReturn(expectedDetailsJob123);
            s3MonitorMock.when(() -> S3Monitor.calculateBopsJobDetails(eq(workflowModel), eq(s3ControlClient), eq(workflowModel.getSourceAccountNumber()), eq("job456")))
                    .thenReturn(expectedDetailsJob456);

            assertDoesNotThrow(() -> s3MonitorManager.getIndividualBopsJobDetails(s3ControlClient));

            s3MonitorMock.verify(() -> S3Monitor.calculateBopsJobDetails(eq(workflowModel), eq(s3ControlClient), eq(workflowModel.getSourceAccountNumber()), eq("job123")), times(1));
            s3MonitorMock.verify(() -> S3Monitor.calculateBopsJobDetails(eq(workflowModel), eq(s3ControlClient), eq(workflowModel.getSourceAccountNumber()), eq("job456")), times(1));

            Map<String, BopsJobDetails> expectedMap = Map.of(
                    "job123", expectedDetailsJob123,
                    "job456", expectedDetailsJob456
            );
            assertEquals(expectedMap, workflowModel.getBopsJobDetails());
        }
    }

    @Test
    void testCalculateCrrMonitoringDetails_Success() {
        try (MockedStatic<S3Monitor> s3MonitorMocked = mockStatic(S3Monitor.class)) {
            S3MonitorDetails expectedDetails = S3MonitorDetails.builder()
                    .crrReplicationLatency(10L)
                    .crrBytesPendingReplication(100L)
                    .lastCRRCheckTimestamp(1000L)
                    .crrStatus(S3Monitor.CRRStatus.CRR_FINISHED.name())
                    .build();

            s3MonitorMocked.when(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")))
                            .thenReturn(expectedDetails);
            s3MonitorMocked.when(() -> S3Monitor.deriveWorkflowStatus(anyString(), anyString(), anyString(), anyString()))
                            .thenReturn(WorkflowStatus.RUNNING.name());

            assertDoesNotThrow(() -> s3MonitorManager.calculateCrrMonitoringDetails(cloudwatchClient));

            MonitoringDetails actualMonitoringDetails = workflowModel.getMonitoringDetails();
            assertEquals(10L, actualMonitoringDetails.getCrrLatency());
            assertEquals(100L, actualMonitoringDetails.getCrrBytesPendingReplication());
            assertEquals(1000L, actualMonitoringDetails.getLastCRRCheckTimestamp());
            assertEquals(S3Monitor.CRRStatus.CRR_FINISHED.name(), actualMonitoringDetails.getCrrStatus());
            assertEquals(WorkflowStatus.RUNNING.name(), workflowModel.getStatus());

            s3MonitorMocked.verify(() -> S3Monitor.deriveWorkflowStatus(anyString(), anyString(), anyString(), anyString()), times(1));
            s3MonitorMocked.verify(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")), times(1));
        }
    }

    @Test
    void testCalculateCrrMonitoringDetails_WithAckedNotification() {
        try (MockedStatic<S3Monitor> s3MonitorMocked = mockStatic(S3Monitor.class)) {
            workflowModel.setAckedNotification("STOP_SOURCE_TRAFFIC_ACK");
            S3MonitorDetails expectedDetails = S3MonitorDetails.builder()
                    .crrReplicationLatency(10L)
                    .crrBytesPendingReplication(100L)
                    .lastCRRCheckTimestamp(1000L)
                    .crrStatus(S3Monitor.CRRStatus.CRR_FINISHED.name())
                    .build();

            s3MonitorMocked.when(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")))
                    .thenReturn(expectedDetails);
            s3MonitorMocked.when(() -> S3Monitor.deriveWorkflowStatus(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(WorkflowStatus.FINISHED.name());

            assertDoesNotThrow(() -> s3MonitorManager.calculateCrrMonitoringDetails(cloudwatchClient));

            MonitoringDetails actualMonitoringDetails = workflowModel.getMonitoringDetails();
            assertEquals(10L, actualMonitoringDetails.getCrrLatency());
            assertEquals(100L, actualMonitoringDetails.getCrrBytesPendingReplication());
            assertEquals(1000L, actualMonitoringDetails.getLastCRRCheckTimestamp());
            assertEquals(S3Monitor.CRRStatus.CRR_FINISHED.name(), actualMonitoringDetails.getCrrStatus());
            assertEquals(WorkflowStatus.FINISHED.name(), workflowModel.getStatus());

            s3MonitorMocked.verify(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")), times(1));
            s3MonitorMocked.verify(() -> S3Monitor.deriveWorkflowStatus(eq("STOP_SOURCE_TRAFFIC_ACK"), anyString(), anyString(), anyString()), times(1));
        }
    }

    @Test
    void testCalculateCrrMonitoringDetails_WorkflowWaiting() {
        try (MockedStatic<S3Monitor> s3MonitorMocked = mockStatic(S3Monitor.class)) {
            S3MonitorDetails expectedDetails = S3MonitorDetails.builder()
                    .crrReplicationLatency(10L)
                    .crrBytesPendingReplication(100L)
                    .lastCRRCheckTimestamp(1000L)
                    .crrStatus(S3Monitor.CRRStatus.CRR_FINISHED.name())
                    .build();

            s3MonitorMocked.when(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")))
                    .thenReturn(expectedDetails);
            s3MonitorMocked.when(() -> S3Monitor.deriveWorkflowStatus(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(WorkflowStatus.WAITING.name());

            assertDoesNotThrow(() -> s3MonitorManager.calculateCrrMonitoringDetails(cloudwatchClient));

            MonitoringDetails actualMonitoringDetails = workflowModel.getMonitoringDetails();
            assertEquals(S3Monitor.CRRStatus.CRR_RUNNING.name(), actualMonitoringDetails.getCrrStatus());
            assertEquals(WorkflowStatus.WAITING.name(), workflowModel.getStatus());

            s3MonitorMocked.verify(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")), times(1));
            s3MonitorMocked.verify(() -> S3Monitor.deriveWorkflowStatus(anyString(), anyString(), anyString(), anyString()), times(1));
        }
    }

    @Test
    void testCalculateCrrMonitoringDetails_WorkflowStateIsCrrStatus() {
        try (MockedStatic<S3Monitor> s3MonitorMocked = mockStatic(S3Monitor.class)) {
            workflowModel.setMonitoringDetails(MonitoringDetails.builder()
                            .bopsJobStatus(S3Monitor.BOPSStatus.BOPS_FINISHED.name())
                            .build());
            S3MonitorDetails expectedDetails = S3MonitorDetails.builder()
                    .crrReplicationLatency(10L)
                    .crrBytesPendingReplication(100L)
                    .lastCRRCheckTimestamp(1000L)
                    .crrStatus(S3Monitor.CRRStatus.CRR_RUNNING.name())
                    .build();

            s3MonitorMocked.when(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")))
                    .thenReturn(expectedDetails);
            s3MonitorMocked.when(() -> S3Monitor.deriveWorkflowStatus(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(WorkflowStatus.WAITING.name());

            assertDoesNotThrow(() -> s3MonitorManager.calculateCrrMonitoringDetails(cloudwatchClient));

            MonitoringDetails actualMonitoringDetails = workflowModel.getMonitoringDetails();
            assertEquals(S3Monitor.CRRStatus.CRR_RUNNING.name(), actualMonitoringDetails.getCrrStatus());
            assertEquals(WorkflowStatus.WAITING.name(), workflowModel.getStatus());
            assertEquals(S3Monitor.CRRStatus.CRR_RUNNING.name(), workflowModel.getState());

            s3MonitorMocked.verify(() -> S3Monitor.calculateCRRStatus(eq(cloudwatchClient), eq("test-bucket"), eq(workflowModel.getDestBucketARN()), eq(workflowModel.getDestRegion()), eq("test-bucket2")), times(1));
            s3MonitorMocked.verify(() -> S3Monitor.deriveWorkflowStatus(anyString(), anyString(), anyString(), anyString()), times(1));
        }
    }
}