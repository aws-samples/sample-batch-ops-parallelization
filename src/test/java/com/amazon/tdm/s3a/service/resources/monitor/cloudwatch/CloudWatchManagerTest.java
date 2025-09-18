package com.amazon.tdm.s3a.service.resources.monitor.cloudwatch;

import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.model.MonitoringDetails;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.util.ArrayList;
import java.util.List;

import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_PROGRESS_PCT_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_TASKS_FAILED_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_TASKS_SUCCEEDED_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_TASKS_TOTAL_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.CRR_BYTES_PENDING_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.CRR_LATENCY_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.NAMESPACE_ID_DIMENSION;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.WORKFLOW_NAME_DIMENSION;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.WORKFLOW_STATUS_FAILED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudWatchManagerTest {

    @Mock
    private CloudWatchClient cloudWatchClient;

    @Mock
    private WorkFlowModel workflowModel;

    private CloudWatchManager cloudWatchManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(workflowModel.getNamespaceID()).thenReturn("test-namespace");
        when(workflowModel.getWorkflowName()).thenReturn("test-workflow");
        when(workflowModel.getSourceBucketARN()).thenReturn("arn:aws:s3:::test-bucket");
        cloudWatchManager = new CloudWatchManager();
    }

    @Test
    void testCreateCloudWatchAlarm_NewAlarm() {
        // This will test buildAlarmName, buildAlarmDescription, buildTicketArn, and alarmExists private methods
        when(cloudWatchClient.describeAlarms(any(DescribeAlarmsRequest.class)))
                .thenReturn(DescribeAlarmsResponse.builder().metricAlarms(new ArrayList<>()).build());
        when(cloudWatchClient.putMetricAlarm(any(PutMetricAlarmRequest.class)))
                .thenReturn(PutMetricAlarmResponse.builder()
                        .build());

        cloudWatchManager.createCloudWatchAlarm(cloudWatchClient, workflowModel);

        verify(cloudWatchClient).putMetricAlarm(argThat((PutMetricAlarmRequest request) ->
                request.alarmName().equals("S3AFailedAlarm-test-namespace-test-workflow") &&
                        request.alarmDescription().contains("test-bucket") &&
                        request.alarmActions().get(0).contains("arn:aws:cloudwatch") &&
                        request.dimensions().size() == 2
        ));
    }

    @Test
    void testCreateCloudWatchAlarm_ExistingAlarm() {
        // Tests alarmExists private method when alarm exists
        when(cloudWatchClient.describeAlarms(any(DescribeAlarmsRequest.class)))
                .thenReturn(DescribeAlarmsResponse.builder()
                        .metricAlarms(MetricAlarm.builder().build())
                        .build());

        cloudWatchManager.createCloudWatchAlarm(cloudWatchClient, workflowModel);

        verify(cloudWatchClient, never()).putMetricAlarm(any(PutMetricAlarmRequest.class));
    }

    @Test
    void testCreateCloudWatchAlarm_DescribeAlarmsException() {
        // Tests alarmExists private method exception handling
        when(cloudWatchClient.describeAlarms(any(DescribeAlarmsRequest.class)))
                .thenThrow(new RuntimeException("Test exception"));

        cloudWatchManager.createCloudWatchAlarm(cloudWatchClient, workflowModel);

        verify(cloudWatchClient).putMetricAlarm(any(PutMetricAlarmRequest.class));
    }

    @Test
    void testPublishCWMetrics_WorkflowFailed() {
        // Tests buildMetricDatum private method with failed status
        setupWorkflowModelForMetrics(WorkflowStatus.FAILED);
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(PutMetricDataResponse.builder().build());

        boolean result = cloudWatchManager.publishCWMetrics(cloudWatchClient, workflowModel);

        assertTrue(result);
        verify(cloudWatchClient).putMetricData(argThat((PutMetricDataRequest request) -> {
            List<MetricDatum> metrics = request.metricData();
            return metrics.stream().anyMatch(datum ->
                    datum.metricName().equals(WORKFLOW_STATUS_FAILED) &&
                            datum.value() == 1.0 &&
                            datum.dimensions().size() == 2 &&
                            datum.dimensions().stream().anyMatch(d ->
                                    d.name().equals(NAMESPACE_ID_DIMENSION) &&
                                            d.value().equals("test-namespace")
                            )
            );
        }));
    }

    @Test
    void testPublishCWMetrics_WorkflowRunning() {
        // Tests buildMetricDatum private method with non-failed status
        setupWorkflowModelForMetrics(WorkflowStatus.RUNNING);
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(PutMetricDataResponse.builder().build());

        boolean result = cloudWatchManager.publishCWMetrics(cloudWatchClient, workflowModel);

        assertTrue(result);
        verify(cloudWatchClient).putMetricData(argThat((PutMetricDataRequest request) -> {
            List<MetricDatum> metrics = request.metricData();
            return metrics.stream().allMatch(datum ->
                    datum.dimensions().stream().anyMatch(d ->
                            d.name().equals(WORKFLOW_NAME_DIMENSION) &&
                                    d.value().equals("test-workflow")
                    )
            );
        }));
    }

    @Test
    void testPublishCWMetrics_AllMetricsIncluded() {
        setupWorkflowModelForMetrics(WorkflowStatus.RUNNING);
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(PutMetricDataResponse.builder().build());

        boolean result = cloudWatchManager.publishCWMetrics(cloudWatchClient, workflowModel);

        assertTrue(result);
        verify(cloudWatchClient).putMetricData(argThat((PutMetricDataRequest request) -> {
            List<MetricDatum> metrics = request.metricData();
            return metrics.size() == 7 && // Verifies all metrics are included
                    metrics.stream().anyMatch(m -> m.metricName().equals(CRR_BYTES_PENDING_METRIC)) &&
                    metrics.stream().anyMatch(m -> m.metricName().equals(CRR_LATENCY_METRIC)) &&
                    metrics.stream().anyMatch(m -> m.metricName().equals(BOPS_TASKS_TOTAL_METRIC)) &&
                    metrics.stream().anyMatch(m -> m.metricName().equals(BOPS_TASKS_FAILED_METRIC)) &&
                    metrics.stream().anyMatch(m -> m.metricName().equals(BOPS_TASKS_SUCCEEDED_METRIC)) &&
                    metrics.stream().anyMatch(m -> m.metricName().equals(BOPS_PROGRESS_PCT_METRIC)) &&
                    metrics.stream().anyMatch(m -> m.metricName().equals(WORKFLOW_STATUS_FAILED));
        }));
    }

    @Test
    void testPublishCWMetrics_ExceptionHandling() {
        setupWorkflowModelForMetrics(WorkflowStatus.RUNNING);
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(new RuntimeException("Test exception"));

        boolean result = cloudWatchManager.publishCWMetrics(cloudWatchClient, workflowModel);

        assertFalse(result);
    }

    @Test
    void testCreateCloudWatchAlarm_Success() {
        // Arrange
        when(workflowModel.getNamespaceID()).thenReturn("namespace-1");
        when(workflowModel.getWorkflowName()).thenReturn("workflow-1");
        when(workflowModel.getSourceBucketARN()).thenReturn("arn:aws:s3:::source-bucket");

        // Mock the describeAlarms to return empty list (alarm doesn't exist)
        when(cloudWatchClient.describeAlarms(any(DescribeAlarmsRequest.class)))
                .thenReturn(DescribeAlarmsResponse.builder()
                        .metricAlarms(new ArrayList<>())
                        .build());

        // Mock successful alarm creation with explicit cast
        PutMetricAlarmResponse mockResponse = (PutMetricAlarmResponse) PutMetricAlarmResponse.builder()
                .sdkHttpResponse(SdkHttpResponse.builder()
                        .statusCode(200)
                        .build())
                .build();
        when(cloudWatchClient.putMetricAlarm(any(PutMetricAlarmRequest.class)))
                .thenReturn(mockResponse);

        // Act
        cloudWatchManager.createCloudWatchAlarm(cloudWatchClient, workflowModel);

        // Verify
        verify(cloudWatchClient).putMetricAlarm(any(PutMetricAlarmRequest.class));
    }


    @Test
    void testCreateCloudWatchAlarm_ThrowsException() {
        // Arrange
        when(workflowModel.getNamespaceID()).thenReturn("namespace-1");
        when(workflowModel.getWorkflowName()).thenReturn("workflow-1");
        when(workflowModel.getSourceBucketARN()).thenReturn("arn:aws:s3:::source-bucket");

        // Mock the describeAlarms to return empty list (alarm doesn't exist)
        when(cloudWatchClient.describeAlarms(any(DescribeAlarmsRequest.class)))
                .thenReturn(DescribeAlarmsResponse.builder()
                        .metricAlarms(new ArrayList<>())
                        .build());

        // Mock exception during alarm creation
        when(cloudWatchClient.putMetricAlarm(any(PutMetricAlarmRequest.class)))
                .thenThrow(new RuntimeException("Failed to create alarm"));

        // Act
        cloudWatchManager.createCloudWatchAlarm(cloudWatchClient, workflowModel);

        // Verify
        verify(cloudWatchClient).putMetricAlarm(any(PutMetricAlarmRequest.class));
    }

    private void setupWorkflowModelForMetrics(WorkflowStatus status) {
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .crrBytesPendingReplication(1000L)
                .crrLatency(60L)
                .bopsNumOfTotalTasks(100L)
                .bopsNumOfTasksFailed(10L)
                .bopsNumOfTasksSucceeded(90L)
                .bopsPercentProgress(90.0)
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        when(workflowModel.getStatus()).thenReturn(status.name());
    }
}