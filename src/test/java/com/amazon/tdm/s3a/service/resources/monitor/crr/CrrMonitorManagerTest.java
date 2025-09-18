package com.amazon.tdm.s3a.service.resources.monitor.crr;

import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.model.MonitoringDetails;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.CRR_BYTESPENDING_THRESHOLD_VALUE;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.STOP_TRAFFIC_NOTIFICATION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrrMonitorManagerTest {

    @Mock
    private CloudWatchClient cloudWatchClient;

    @Mock
    private WorkFlowModel workflowModel;

    private CrrMonitorManager crrMonitorManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(workflowModel.getSourceBucketARN()).thenReturn("arn:aws:s3:::source-bucket");
        when(workflowModel.getDestBucketARN()).thenReturn("arn:aws:s3:::dest-bucket");
        when(workflowModel.getDestRegion()).thenReturn("eu-west-1");
        crrMonitorManager = new CrrMonitorManager();
    }

    @Test
    void testCalculateCrrMonitoringDetails_BopsFailedStatus() {
        // Arrange
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_FAILED.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        mockCloudWatchMetrics(100.0, 1000.0);

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setStatus(WorkflowStatus.FAILED.name());
    }

    @Test
    void testCalculateCrrMonitoringDetails_BopsFinishedNoNotification() {
        // Arrange
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn(null);
        mockCloudWatchMetrics(100.0, 1000.0);

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setStatus(WorkflowStatus.WAITING.name());
        verify(workflowModel).setMonitoringDetails(any(MonitoringDetails.class));
    }

    @Test
    void testCalculateCrrMonitoringDetails_BopsFinishedWithStopTraffic() {
        // Arrange
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn(STOP_TRAFFIC_NOTIFICATION_ID);
        mockCloudWatchMetricsForFinished();

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setStatus(WorkflowStatus.RUNNING.name());
    }

    @Test
    void testCalculateCrrMonitoringDetails_RunningStatus() {
        // Arrange
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        mockCloudWatchMetrics(100.0, 1000.0);

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setStatus(WorkflowStatus.RUNNING.name());
    }

    @Test
    void testCalculateCrrMonitoringDetails_CrrStatusError() {
        // Arrange
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenThrow(CloudWatchException.class);

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getCrrStatus().equals(MonitorConstants.CRRStatus.CRR_STATUS_ERROR.name()) &&
                        details.getCrrLatency() == null &&
                        details.getCrrBytesPendingReplication() == null
        ));
    }

    @Test
    void testCalculateCrrMonitoringDetails_CrrStatusFinished() {
        // Arrange
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn(STOP_TRAFFIC_NOTIFICATION_ID);

        // Mock first call for metrics reporting
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(GetMetricStatisticsResponse.builder()
                        .datapoints(Collections.singletonList(
                                Datapoint.builder()
                                        .maximum((double)CRR_BYTESPENDING_THRESHOLD_VALUE)
                                        .timestamp(Instant.now())
                                        .build()
                        ))
                        .build());

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getCrrStatus().equals(MonitorConstants.CRRStatus.CRR_FINISHED.name())
        ));
        verify(workflowModel).setStatus(WorkflowStatus.FINISHED.name());
    }

    @Test
    void testCalculateCrrMonitoringDetails_NoDataPoints() {
        // Arrange
        MonitoringDetails initialMonitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(initialMonitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn(null);

        // Mock all three getCRRMetrics calls to return empty datapoints
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(GetMetricStatisticsResponse.builder()
                        .datapoints(Collections.emptyList())
                        .build());

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name()) &&
                        details.getCrrStatus().equals(MonitorConstants.CRRStatus.CRR_FINISHED.name()) &&
                        details.getCrrLatency() == 0L &&
                        details.getCrrBytesPendingReplication() == 0L &&
                        details.getLastCRRCheckTimestamp() != null
        ));
        verify(workflowModel).setStatus(WorkflowStatus.RUNNING.name());
    }

    @Test
    void testCalculateCrrMonitoringDetails_BytesPendingThreshold() {
        // Arrange
        MonitoringDetails initialMonitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(initialMonitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn(STOP_TRAFFIC_NOTIFICATION_ID);

        // Mock to return threshold value for BytesPendingReplication
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(GetMetricStatisticsResponse.builder()
                        .datapoints(Collections.singletonList(
                                Datapoint.builder()
                                        .maximum((double)CRR_BYTESPENDING_THRESHOLD_VALUE)
                                        .timestamp(Instant.now())
                                        .build()
                        ))
                        .build());

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_FINISHED.name()) &&
                        details.getCrrStatus().equals(MonitorConstants.CRRStatus.CRR_FINISHED.name())
        ));
        verify(workflowModel).setStatus(WorkflowStatus.FINISHED.name());
    }

    @Test
    void testCalculateCrrMonitoringDetails_MultipleDatapoints() {
        // Arrange
        MonitoringDetails initialMonitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(initialMonitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn(null);

        // Mock with multiple datapoints to test maxDatapointFinder
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(GetMetricStatisticsResponse.builder()
                        .datapoints(Arrays.asList(
                                Datapoint.builder()
                                        .maximum(50.0)
                                        .timestamp(Instant.now().minusSeconds(60))
                                        .build(),
                                Datapoint.builder()
                                        .maximum(100.0)
                                        .timestamp(Instant.now())
                                        .build()
                        ))
                        .build());

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name()) &&
                        details.getCrrLatency() == 100L &&
                        details.getCrrStatus().equals(MonitorConstants.CRRStatus.CRR_RUNNING.name())
        ));
    }

    @Test
    void testCalculateCrrMonitoringDetails_WaitingToRunningTransition() {
        // Arrange
        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                .crrStatus(MonitorConstants.CRRStatus.CRR_FINISHED.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(monitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn("");
        mockCloudWatchMetrics(100.0, 1000.0);

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getCrrStatus().equals(MonitorConstants.CRRStatus.CRR_RUNNING.name())
        ));
        verify(workflowModel).setStatus(WorkflowStatus.WAITING.name());
    }

    @Test
    void testCalculateCrrMonitoringDetails_NullMetricsResponse() {
        // Arrange
        MonitoringDetails initialMonitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .build();
        when(workflowModel.getMonitoringDetails()).thenReturn(initialMonitoringDetails);
        when(workflowModel.getAckedNotification()).thenReturn(null);

        // Mock to return null response
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(null);

        // Act
        crrMonitorManager.calculateCrrMonitoringDetails(cloudWatchClient, workflowModel);

        // Assert
        verify(workflowModel).setMonitoringDetails(argThat(details ->
                details.getBopsJobStatus().equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name()) &&
                        details.getCrrStatus().equals(MonitorConstants.CRRStatus.CRR_STATUS_ERROR.name()) &&
                        details.getCrrLatency() == null &&
                        details.getCrrBytesPendingReplication() == null
        ));
        verify(workflowModel).setStatus(WorkflowStatus.RUNNING.name());
    }

    private void mockCloudWatchMetrics(double latency, double bytesPending) {
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(GetMetricStatisticsResponse.builder()
                        .datapoints(Arrays.asList(
                                Datapoint.builder()
                                        .maximum(latency)
                                        .timestamp(Instant.now())
                                        .build(),
                                Datapoint.builder()
                                        .maximum(bytesPending)
                                        .timestamp(Instant.now())
                                        .build()
                        ))
                        .build());
    }

    private void mockCloudWatchMetricsForFinished() {
        when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(GetMetricStatisticsResponse.builder()
                        .datapoints(Arrays.asList(
                                Datapoint.builder()
                                        .maximum(100.0)
                                        .timestamp(Instant.now())
                                        .build(),
                                Datapoint.builder()
                                        .maximum((double)CRR_BYTESPENDING_THRESHOLD_VALUE)
                                        .timestamp(Instant.now())
                                        .build()
                        ))
                        .build());
    }
}