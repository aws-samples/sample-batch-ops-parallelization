package com.amazon.bopspar.service.resources.monitor;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.model.BopsJobDetails;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.replication.S3ReplicationUtils;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.DescribeJobRequest;
import software.amazon.awssdk.services.s3control.model.DescribeJobResponse;
import software.amazon.awssdk.services.s3control.model.JobDescriptor;
import software.amazon.awssdk.services.s3control.model.JobReport;
import software.amazon.awssdk.services.s3control.model.JobStatus;
import software.amazon.awssdk.services.s3control.model.JobTimers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.CRR_BYTESPENDING_THRESHOLD_PERIOD;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.CRR_BYTESPENDING_THRESHOLD_VALUE;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.JOB_COMPLETION_REPORT_CONSOLE_URL_FORMAT;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.S3_BYTES_PENDING_REPLICATION;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.S3_REPLICATION_LATENCY;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.STOP_TRAFFIC_NOTIFICATION_ID;

/**
* S3A Monitor. Library with AWS S3/Cloudwatch SDK methods to help collect BOPS and CRR metrics.
*
*/
@Log4j2
@Builder
@NoArgsConstructor
public class S3Monitor {

    enum BOPSStatus {
        BOPS_RUNNING,
        BOPS_FAILED,
        BOPS_FINISHED,
        BOPS_STATUS_ERROR
    }

    enum CRRStatus {
        CRR_RUNNING,
        CRR_FAILED,
        CRR_FINISHED,
        CRR_STATUS_ERROR
    }

    private static final EnumSet<JobStatus> TERMINAL_JOB_STATUSES = EnumSet.of(
        JobStatus.FAILED,
        JobStatus.CANCELLED,
        JobStatus.SUSPENDED,
        JobStatus.UNKNOWN_TO_SDK_VERSION
    );

    /**
     * Helps derive the final workflow status.
     * If BOPS is FINISHED and Notification is NOT STOP_SOURCE_TRAFFIC_ACK then WorkflowStatus is set to WAITING
     * else if  BOPS is FINISHED and Notification is STOP_SOURCE_TRAFFIC_ACK: WorkflowStatus is set to RUNNING
     * else if CRR is FINISHED AND Notification is STOP_SOURCE_TRAFFIC_ACK, WorkflowStatus is set to FINISHED
     * @param notificationId Notification ID
     * @param bopsStatus S3A Status of BOPS job
     * @param crrStatus S3A Status of the CRR metrics
     * @param workflowStatus Workflow Status
     * @return status of a workflow.
     */
    public static String deriveWorkflowStatus(final String notificationId,
                                              final String bopsStatus,
                                              final String crrStatus,
                                              final String workflowStatus) {
        // Check for failed state
        if (isFailedState(bopsStatus, workflowStatus)) {
            return WorkflowStatus.FAILED.name();
        }

        // Check for workflow stopping states
        if (isWorkflowStopping(workflowStatus)) {
            if (isBopsFinishedOrFailed(bopsStatus)) {
                return WorkflowStatus.STOPPED.name();
            }
            if (isBopsRunning(bopsStatus)) {
                return WorkflowStatus.STOPPING.name();
            }
        }

        // Check for finished state
        if (isWorkflowFinished(bopsStatus, notificationId, crrStatus)) {
            return WorkflowStatus.FINISHED.name();
        }

        // Check for waiting state
        if (isBopsFinishedWaitingForTrafficStop(bopsStatus, notificationId)) {
            return WorkflowStatus.WAITING.name();
        }

        // Default state
        return WorkflowStatus.RUNNING.name();
    }

    private static boolean isFailedState(final String bopsStatus, final String workflowStatus) {
        return BOPSStatus.BOPS_FAILED.name().equals(bopsStatus)
                && !WorkflowStatus.STOPPING.name().equals(workflowStatus);
    }

    private static boolean isWorkflowStopping(final String workflowStatus) {
        return WorkflowStatus.STOPPING.name().equals(workflowStatus);
    }

    private static boolean isBopsFinishedOrFailed(final String bopsStatus) {
        return BOPSStatus.BOPS_FINISHED.name().equals(bopsStatus)
                || BOPSStatus.BOPS_FAILED.name().equals(bopsStatus);
    }

    private static boolean isBopsRunning(final String bopsStatus) {
        return BOPSStatus.BOPS_RUNNING.name().equals(bopsStatus);
    }

    private static boolean isWorkflowFinished(final String bopsStatus,
                                              final String notificationId,
                                              final String crrStatus) {
        return BOPSStatus.BOPS_FINISHED.name().equals(bopsStatus)
                && STOP_TRAFFIC_NOTIFICATION_ID.equals(notificationId)
                && CRRStatus.CRR_FINISHED.name().equals(crrStatus);
    }

    private static boolean isBopsFinishedWaitingForTrafficStop(final String bopsStatus, final String notificationId) {
        return BOPSStatus.BOPS_FINISHED.name().equals(bopsStatus)
                && !STOP_TRAFFIC_NOTIFICATION_ID.equals(notificationId);
    }

    /**
     * Get S3 BOPS Job status.
     * @param s3ControlClient The SDK S3 Control client
     * @param accountId The AWS source account ID
     * @param jobId The Batch Ops Replicate Job ID
     */
    public static BopsJobDetails calculateBopsJobDetails(final WorkFlowModel workFlowModel,
                                                         final S3ControlClient s3ControlClient,
                                                         final String accountId,
                                                         final String jobId) {
        BOPSStatus bopsStatus = BOPSStatus.BOPS_RUNNING;
        BopsJobDetails.BopsJobDetailsBuilder bopsJobDetailsBuilder = BopsJobDetails.builder();
        try {
            DescribeJobRequest describeJobRequest = DescribeJobRequest.builder()
                    .accountId(accountId)
                    .jobId(jobId)
                    .build();
            DescribeJobResponse describeJobResponse = s3ControlClient.describeJob(describeJobRequest);
            JobDescriptor jobDescriptor = describeJobResponse.job();
            if (jobDescriptor.creationTime() != null) {
                bopsJobDetailsBuilder.creationTime(jobDescriptor.creationTime().toEpochMilli());
            }
            log.info("Job Response: {}", describeJobResponse);

            Long numTasksFailed = describeJobResponse.job().progressSummary().numberOfTasksFailed();
            Long numTasksSuccess = describeJobResponse.job().progressSummary().numberOfTasksSucceeded();
            Long numTotalTasks = describeJobResponse.job().progressSummary().totalNumberOfTasks();

            bopsJobDetailsBuilder.sdkJobStatus(describeJobResponse.job().status().toString());
            bopsJobDetailsBuilder.numOfTasksFailed(numTasksFailed);
            bopsJobDetailsBuilder.numOfTasksSucceeded(numTasksSuccess);
            bopsJobDetailsBuilder.numOfTotalTasks(numTotalTasks);
            Long currentTasksProcessed = numTasksFailed + numTasksSuccess;
            Double currentPercentCompleted = 0.0;
            if (numTotalTasks > 0) {
                currentPercentCompleted = ((double) currentTasksProcessed / numTotalTasks) * 100.0;
            }
            bopsJobDetailsBuilder.percentProgress(currentPercentCompleted);
            // 2025-March: We'll be using the BOPS-SDK job status to report back
            if (isFailingStatus(describeJobResponse.job().status())) {
                bopsStatus = BOPSStatus.BOPS_FAILED;
                if (jobDescriptor.terminationDate() != null) {
                    bopsJobDetailsBuilder.terminationTime(jobDescriptor.terminationDate().toEpochMilli());
                }
            } else if (describeJobResponse.job().status() == JobStatus.COMPLETE) {
                bopsStatus = BOPSStatus.BOPS_FINISHED;
                if (jobDescriptor.terminationDate() != null) {
                    bopsJobDetailsBuilder.terminationTime(jobDescriptor.terminationDate().toEpochMilli());
                }
            }
            bopsJobDetailsBuilder.jobCompletionReportUrl(
                    getS3JobCompletionReportUrl(describeJobResponse, workFlowModel.getSourceRegion()));
            bopsJobDetailsBuilder.elapsedTimeInActiveSeconds(getElapsedTimeInActiveSeconds(describeJobResponse));
        } catch (S3Exception exception) {
            log.error("Error getting status for workflowId: {}, namespaceId: {}, "
                            + "accountId: {}, jobId: {}, Exception: {}",
                    workFlowModel.getWorkflowName(),
                    workFlowModel.getNamespaceID(),
                    accountId,
                    jobId,
                    exception);
            bopsStatus = BOPSStatus.BOPS_STATUS_ERROR;
        }
        bopsJobDetailsBuilder.jobStatus(bopsStatus.name());
        return bopsJobDetailsBuilder.build();
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
    private static double getCRRMetrics(final CloudWatchClient cloudwatchClient,
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

        // To break out metrics by specific dimensions not natively supported by Workflow
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
            log.info("Getting Metric request: {}", metricsRequest.toString());
            GetMetricStatisticsResponse metricsResponse = cloudwatchClient
                    .getMetricStatistics(metricsRequest);
            if (metricsResponse != null) {
                log.info("Metric Response: {}", metricsResponse.toString());
                metricValue = maxDatapointFinder(metricsResponse.datapoints());
                //metricValue = metricsResponse.datapoints().isEmpty() ? 0 : metricsResponse
                //        .datapoints().get(0).maximum();
            }
        } catch (CloudWatchException exception) {
            log.error("Error getting Cloudwatch metric for bucketName: {}, metricName: {} - exception: {}",
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
    public static S3MonitorDetails calculateCRRStatus(final CloudWatchClient cloudwatchClient,
                                                      final String srcBucketName,
                                                      final String destBucketArn,
                                                      final String destBucketRegion,
                                                      final String destBucketName) {
        CRRStatus crrStatus = CRRStatus.CRR_RUNNING;
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
            crrStatus = CRRStatus.CRR_STATUS_ERROR;
        } else if (bytesPendingValue == (double)CRR_BYTESPENDING_THRESHOLD_VALUE ) {
            crrStatus = CRRStatus.CRR_FINISHED;
        }
        monitorDetails.setCrrStatus(crrStatus.name());
        return monitorDetails;

    }

    private static boolean isFailingStatus(final JobStatus jobStatus) {
        return TERMINAL_JOB_STATUSES.contains(jobStatus);
    }

    private static String getS3JobCompletionReportUrl(final DescribeJobResponse jobResponse, final String region) {
        final JobReport jobReport = jobResponse.job().report();
        if (jobReport == null
            || !jobReport.enabled()
            || jobReport.bucket() == null
            || jobReport.prefix() == null) {
            return null;
        }
        final String bucketName = Arn.fromString(jobReport.bucket()).resourceAsString();
        final String fullPrefix = String.format("%s/job-%s/", jobReport.prefix(), jobResponse.job().jobId());
        final String encodedPrefix = URLEncoder.encode(fullPrefix, StandardCharsets.UTF_8);
        return String.format(JOB_COMPLETION_REPORT_CONSOLE_URL_FORMAT,
                region, bucketName, region, encodedPrefix);
    }

    private static Long getElapsedTimeInActiveSeconds(final DescribeJobResponse jobResponse) {
        final JobTimers jobTimers = jobResponse.job().progressSummary().timers();
        if (jobTimers == null
                || jobTimers.elapsedTimeInActiveSeconds() == null) {
            return 0L;
        }
        return jobTimers.elapsedTimeInActiveSeconds();
    }
}
