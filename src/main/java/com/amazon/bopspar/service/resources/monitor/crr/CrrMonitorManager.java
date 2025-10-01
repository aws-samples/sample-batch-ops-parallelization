package com.amazon.bopspar.service.resources.monitor.crr;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.model.MonitoringDetails;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.monitor.MonitorConstants;
import com.amazon.bopspar.service.resources.monitor.S3MonitorDetails;
import com.amazon.bopspar.service.resources.replication.S3ReplicationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.CRR_BYTESPENDING_THRESHOLD_PERIOD;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.CRR_BYTESPENDING_THRESHOLD_VALUE;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.S3_BYTES_PENDING_REPLICATION;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.S3_REPLICATION_LATENCY;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.STOP_TRAFFIC_NOTIFICATION_ID;

/**
 * CRR Monitor Manager class used to monitor CRR.
 */
public class CrrMonitorManager {
    private static final Logger LOGGER = LogManager.getLogger(CrrMonitorManager.class);

    /**
     * Calculate CRR monitoring details.
     * This function is used to calculate the CRR monitoring details
     * for the entire workflow.
     * @param cloudWatchClient cloudwatchClient
     */
    public void calculateCrrMonitoringDetails(final CloudWatchClient cloudWatchClient,
                                              final WorkFlowModel workflowModel) {
        final String sourceBucketName = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();
        final String destBucketName = Arn.fromString(workflowModel.getDestBucketARN()).resourceAsString();
        final String jobStatus = workflowModel.getMonitoringDetails().getBopsJobStatus();

        final S3MonitorDetails crrDetails = calculateCRRStatus(cloudWatchClient, sourceBucketName,
                workflowModel.getDestBucketARN(), workflowModel.getDestRegion(), destBucketName);
        String crrStatus = crrDetails.getCrrStatus();
        String ackNotificationId = (workflowModel.getAckedNotification() == null) ? "" :
                workflowModel.getAckedNotification();

        // Derive workflow Status and State
        String workflowStatus = deriveWorkflowStatus(ackNotificationId,
                jobStatus,
                crrStatus);
        if (workflowStatus.equals(WorkflowStatus.WAITING.name())) {
            crrStatus = MonitorConstants.CRRStatus.CRR_RUNNING.name();
        }
        if (!jobStatus.equals(MonitorConstants.BOPSStatus.BOPS_RUNNING.name())) {
            workflowModel.setState(crrStatus);
        }

        MonitoringDetails currentMonitoringDetails = workflowModel.getMonitoringDetails();
        MonitoringDetails updatedMonitoringDetails = currentMonitoringDetails.toBuilder()
                .crrStatus(crrStatus)
                .crrLatency(crrDetails.getCrrReplicationLatency())
                .crrBytesPendingReplication(crrDetails.getCrrBytesPendingReplication())
                .lastCRRCheckTimestamp(crrDetails.getLastCRRCheckTimestamp())
                .build();

        workflowModel.setMonitoringDetails(updatedMonitoringDetails);
        workflowModel.setStatus(workflowStatus);
    }


    /**
     * Get S3 CRR Metrics.
     * @param cloudwatchClient AWS SDK Cloudwatch client
     * @param srcBucketName bucket which originates the replication rule ID
     * @param destBucketName bucket for which we need replication metrics
     * @param metricName Name of the desired Cloudwatch metrics
     * @param startTime start time of the first datapoint to return (in ISO 8601 UTC format)
     * @param period Granularity in seconds of the returned datapoints
     * @return The value of the desired metric
     */
    private double getCRRMetrics(final CloudWatchClient cloudwatchClient,
                                        final String srcBucketName,
                                        final String destBucketArn,
                                        final String destBucketRegion,
                                        final String destBucketName,
                                        final String metricName,
                                        final Instant startTime,
                                        final int period) {
        double metricValue = -1.0;
        String replicationRuleId = S3ReplicationUtils.generateReplicationRuleId(destBucketArn, destBucketRegion);
        Dimension srcBucketDim = Dimension.builder().name("SourceBucket").value(srcBucketName).build();
        Dimension destBucketDim = Dimension.builder().name("DestinationBucket").value(destBucketName).build();
        Dimension ruleDim = Dimension.builder().name("RuleId").value(replicationRuleId).build();
        Collection<Dimension> dims = new ArrayList<>(List.of(srcBucketDim, destBucketDim, ruleDim));

        // To break out metrics by specific dimensions not natively supported by ORCA
        GetMetricStatisticsRequest metricsRequest = GetMetricStatisticsRequest.builder()
                .metricName(metricName)
                .namespace("AWS/S3")
                .dimensions(dims)
                .startTime(startTime)
                .endTime(Instant.ofEpochMilli(System.currentTimeMillis()))
                .period(period)
                .statisticsWithStrings("Maximum")
                .build();
        try {
            LOGGER.info("Getting Metric request: {}", metricsRequest.toString());
            GetMetricStatisticsResponse metricsResponse = cloudwatchClient
                    .getMetricStatistics(metricsRequest);
            if (metricsResponse != null) {
                LOGGER.info("Metric Response: {}", metricsResponse.toString());
                metricValue = maxDatapointFinder(metricsResponse.datapoints());
                //metricValue = metricsResponse.datapoints().isEmpty() ? 0 : metricsResponse
                //        .datapoints().get(0).maximum();
            }
        } catch (CloudWatchException exception) {
            LOGGER.error("Error getting Cloudwatch metric for bucketName: {}, metricName: {} - exception: {}",
                    destBucketName, metricName, exception);
        }
        return metricValue;
    }


    /**
     * Calculates the S3 Cross-Region Replication Status.
     * @param cloudwatchClient AWS SDK Cloudwatch client
     * @param srcBucketName Source Bucket Name
     * @param destBucketName Destination Bucket Name*
     * @return The status for CRR
     */
    private S3MonitorDetails calculateCRRStatus(final CloudWatchClient cloudwatchClient,
                                                final String srcBucketName,
                                                final String destBucketArn,
                                                final String destBucketRegion,
                                                final String destBucketName) {
        MonitorConstants.CRRStatus crrStatus = MonitorConstants.CRRStatus.CRR_RUNNING;
        //First get the CRR metrics for reporting
        S3MonitorDetails monitorDetails = new S3MonitorDetails();
        Instant startTime = Instant.ofEpochMilli(System.currentTimeMillis() - (5 * 60 * 1000)); // 5 mins ago
        int period = 60; // Get last minute's datapoints
        double bytesPendingValue = 0;
        double replicationLatencyValue = getCRRMetrics(cloudwatchClient, srcBucketName, destBucketArn,
                destBucketRegion, destBucketName, S3_REPLICATION_LATENCY, startTime, period);
        if (replicationLatencyValue >= 0) {
            bytesPendingValue = getCRRMetrics(cloudwatchClient, srcBucketName, destBucketArn,
                    destBucketRegion, destBucketName, S3_BYTES_PENDING_REPLICATION, startTime, period);
            monitorDetails.setCrrReplicationLatency((long) replicationLatencyValue);
            monitorDetails.setCrrBytesPendingReplication((long) bytesPendingValue);
            monitorDetails.setLastCRRCheckTimestamp(System.currentTimeMillis() / 1000);

        }
        //Second: Get CRR metrics and decide if CRR is done.
        startTime = Instant.ofEpochMilli(System.currentTimeMillis() - CRR_BYTESPENDING_THRESHOLD_PERIOD);
        bytesPendingValue = getCRRMetrics(cloudwatchClient, srcBucketName, destBucketArn,
                destBucketRegion, destBucketName, S3_BYTES_PENDING_REPLICATION, startTime, period);
        if (bytesPendingValue < 0 ) {
            crrStatus = MonitorConstants.CRRStatus.CRR_STATUS_ERROR;
        } else if (bytesPendingValue == (double)CRR_BYTESPENDING_THRESHOLD_VALUE ) {
            crrStatus = MonitorConstants.CRRStatus.CRR_FINISHED;
        }
        monitorDetails.setCrrStatus(crrStatus.name());
        return monitorDetails;

    }

    /**
     * Helps derive the final workflow status.
     * If BOPS is FINISHED and Notification is NOT STOP_SOURCE_TRAFFIC_ACK then WorkflowStatus is set to WAITING
     * else if  BOPS is FINISHED and Notification is STOP_SOURCE_TRAFFIC_ACK: WorkflowStatus is set to RUNNING
     * else if CRR is FINISHED AND Notification is STOP_SOURCE_TRAFFIC_ACK, WorkflowStatus is set to FINISHED
     * @param notificationId Notification ID
     * @param bopsStatus S3A Status of BOPS job
     * @param crrStatus S3A Status of the CRR metrics
     * @return status of a workflow.
     */
    private String deriveWorkflowStatus(final String notificationId,
                                              final String bopsStatus,
                                              final String crrStatus) {
        if (bopsStatus.equals(MonitorConstants.BOPSStatus.BOPS_FAILED.name())) {
            return WorkflowStatus.FAILED.name();
        } else if (bopsStatus.equals(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                && !notificationId.equals(STOP_TRAFFIC_NOTIFICATION_ID)) {
            return WorkflowStatus.WAITING.name();
        } else if (bopsStatus.equals(MonitorConstants.BOPSStatus.BOPS_FINISHED.name())
                && notificationId.equals(STOP_TRAFFIC_NOTIFICATION_ID)
                && crrStatus.equals(MonitorConstants.CRRStatus.CRR_FINISHED.name())) {
            return WorkflowStatus.FINISHED.name();
        }

        return WorkflowStatus.RUNNING.name();
    }

    // Helper method to find the max datapoint in a list
    private static double maxDatapointFinder(final List<Datapoint> datapoints) {
        Optional<Datapoint> maxDatapoint = datapoints.stream()
                .max(Comparator.comparingDouble(Datapoint::maximum));

        if (maxDatapoint.isPresent()) {
            return maxDatapoint.get().maximum();
        } else {
            return 0.0;
        }
    }
}
