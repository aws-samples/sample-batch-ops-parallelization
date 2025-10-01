package com.amazon.bopspar.service.resources.monitor;

import com.amazon.bopspar.persistence.model.BopsJobDetails;
import com.amazon.bopspar.persistence.model.MonitoringDetails;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.ComparisonOperator;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmResponse;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.s3control.S3ControlClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.ALARM_PREFIX;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.BOPS_PROGRESS_PCT_METRIC;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.BOPS_TASKS_FAILED_METRIC;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.BOPS_TASKS_SUCCEEDED_METRIC;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.BOPS_TASKS_TOTAL_METRIC;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.CRR_BYTES_PENDING_METRIC;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.CRR_LATENCY_METRIC;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.METRICS_NAMESPACE;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.NAMESPACE_ID_DIMENSION;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.SOP_URL;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.TICKET_ARN_PREFIX;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.TICKET_CATEGORY;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.TICKET_ITEM;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.TICKET_RESOLVER;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.TICKET_SEVERITY;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.TICKET_TYPE;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.WORKFLOW_NAME_DIMENSION;
import static com.amazon.bopspar.service.resources.monitor.MonitorConstants.WORKFLOW_STATUS_FAILED;

/**
 * S3A Monitor MAnager.
 * Coordinates interactions between Lambda and respective S3 Library components.
 *
 */
@Log4j2
@Builder
public class S3MonitorManager {
    private final WorkFlowModel workflowModel;

    enum WorkflowStatus {
        RUNNING,
        FAILED,
        FINISHED,
        WAITING,
        UNKNOWN
    }

    /**
     * Method used to build metric datum for S3A.
     * Dimensions for metric are workflowName and namespaceID
     * for uniqueness in creating dashboard.
     *
     */
    private MetricDatum buildMetricDatum(final String metricName,
                                         final StandardUnit unit,
                                         final double value) {
        return MetricDatum.builder()
                .metricName(metricName)
                .timestamp(Instant.now())
                .unit(unit)
                .value(value)
                .dimensions(Dimension.builder()
                                .name(NAMESPACE_ID_DIMENSION)
                                .value(workflowModel.getNamespaceID())
                                .build(),
                        Dimension.builder()
                                .name(WORKFLOW_NAME_DIMENSION)
                                .value(workflowModel.getWorkflowName())
                                .build())
                .build();
    }

    private boolean alarmExists(final CloudWatchClient s3aCloudWatchClient, final String alarmName) {
        try {
            DescribeAlarmsRequest request = DescribeAlarmsRequest.builder()
                .alarmNames(alarmName)
                .build();

            DescribeAlarmsResponse response = s3aCloudWatchClient.describeAlarms(request);
            List<MetricAlarm> alarms = response.metricAlarms();

            return !alarms.isEmpty();

        } catch (Exception e) {
            log.error("Could not list alarm! {}", e);            
            return false;
        }
    }
    
    /**
     * Create CW Alarm for failed bucket migrations.
     * 
     * @param s3aCloudWatchClient CW client for operations in the S3A account.     
     */
    public void createCloudWatchAlarm(final CloudWatchClient s3aCloudWatchClient) {  
        String alarmDesc = buildAlarmDescription();
        String alarmName = buildAlarmName();
        String ticketArn = buildTicketArn(alarmName);
        
        if (alarmExists(s3aCloudWatchClient, alarmName)) {
            log.info("Alarm {} already exists! - skipping creation...", alarmName);
            return;
        }
        try {
            PutMetricAlarmRequest request = PutMetricAlarmRequest.builder()
                .alarmName(alarmName)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .evaluationPeriods(1)
                .metricName(WORKFLOW_STATUS_FAILED)
                .namespace(METRICS_NAMESPACE)
                .period(60) // 1 minute
                .statistic(Statistic.MAXIMUM)
                .threshold(0.0)
                .actionsEnabled(true)
                .alarmDescription(alarmDesc)
                .unit(StandardUnit.COUNT)
                .dimensions(Dimension.builder()
                                .name(NAMESPACE_ID_DIMENSION)
                                .value(workflowModel.getNamespaceID())
                                .build(),
                        Dimension.builder()
                                .name(WORKFLOW_NAME_DIMENSION)
                                .value(workflowModel.getWorkflowName())
                                .build())
                .alarmActions(ticketArn)
                .build();

            PutMetricAlarmResponse response = s3aCloudWatchClient.putMetricAlarm(request);
            log.info("Successfully created alarm with status code: {}", response.sdkHttpResponse().statusCode());

        } catch (Exception e) {
            log.error("Failed to set alarm! {}", e);            
        }
    }

    /**
     * Publish custom S3A metrics to cloudwatch.
     * 
     * @param s3aCloudWatchClient CW client for operations in the S3A account.
     * @return True if successful.
     */
    public boolean publishCWMetrics(final CloudWatchClient s3aCloudWatchClient) {
        final MonitoringDetails s3MonitorDetails = workflowModel.getMonitoringDetails();
        try {
            log.info("Publishing metrics for BOPS Job, workflowName: {}, namespaceId: {}",
                    workflowModel.getWorkflowName(),
                    workflowModel.getNamespaceID());

            List<MetricDatum> metricDatums = new ArrayList<>();

            if (workflowModel.getStatus().equals(WorkflowStatus.FAILED.name())) {
                metricDatums.add(buildMetricDatum(WORKFLOW_STATUS_FAILED, StandardUnit.COUNT,1.0));
            } else {
                metricDatums.add(buildMetricDatum(WORKFLOW_STATUS_FAILED, StandardUnit.COUNT,0.0));
            }
            metricDatums.add(buildMetricDatum(CRR_BYTES_PENDING_METRIC,
                    StandardUnit.BYTES, (double) s3MonitorDetails.getCrrBytesPendingReplication()));

            metricDatums.add(buildMetricDatum(CRR_LATENCY_METRIC,
                    StandardUnit.SECONDS, (double) s3MonitorDetails.getCrrLatency()));

            metricDatums.add(buildMetricDatum(BOPS_TASKS_TOTAL_METRIC,
                    StandardUnit.COUNT, (double) s3MonitorDetails.getBopsNumOfTotalTasks()));

            metricDatums.add(buildMetricDatum(BOPS_TASKS_FAILED_METRIC,
                    StandardUnit.COUNT, (double) s3MonitorDetails.getBopsNumOfTasksFailed()));

            metricDatums.add(buildMetricDatum(BOPS_TASKS_SUCCEEDED_METRIC,
                    StandardUnit.COUNT, (double) s3MonitorDetails.getBopsNumOfTasksSucceeded()));

            metricDatums.add(buildMetricDatum(BOPS_PROGRESS_PCT_METRIC,
                    StandardUnit.PERCENT, s3MonitorDetails.getBopsPercentProgress()));

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(METRICS_NAMESPACE)
                    .metricData(metricDatums)
                    .build();
            s3aCloudWatchClient.putMetricData(request);
            log.info("Published CW metrics: {}", metricDatums);
        } catch (Exception e) {
            log.error("Publishing CW metrics failed: {}", e);
            return false;
        }
        return true;
    }

    /**
     * Calculate individual BOPS job details.
     * @param s3ControlClient s3ControlClient
     */
    public void getIndividualBopsJobDetails(final S3ControlClient s3ControlClient) {
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        String sourceAccount = workflowModel.getSourceAccountNumber();

        // For each BOPS job ID, calculate and store its details
        for (String jobId : workflowModel.getBopsJobIds()) {
            BopsJobDetails details = S3Monitor.calculateBopsJobDetails(
                    workflowModel,
                    s3ControlClient,
                    sourceAccount,
                    jobId
            );
            jobDetailsMap.put(jobId, details);
        }

        workflowModel.setBopsJobDetails(jobDetailsMap);
    }

    /**
     * Calculate aggregate monitoring details.
     * This function is used to calculate the aggregate monitoring details
     * for the entire workflow. It is called after all the individual BOPS job
     * details have been calculated.
     */
    public void calculateAggregateMonitoringDetails() {
        Long numTotalTasks = 0L;
        Long numTasksSuccess = 0L;
        Long numTasksFailed = 0L;
        Long earliestCreationTime = Long.MAX_VALUE;
        Long latestTerminationTime = 0L;
        Set<S3Monitor.BOPSStatus> activeStatuses = new HashSet<>();

        for (Map.Entry<String, BopsJobDetails> entry : workflowModel.getBopsJobDetails().entrySet()) {
            BopsJobDetails bopsJobDetails = entry.getValue();

            if (bopsJobDetails.getCreationTime() != null) {
                earliestCreationTime = Math.min(earliestCreationTime, bopsJobDetails.getCreationTime());
            }
            if (bopsJobDetails.getTerminationTime() != null) {
                latestTerminationTime = Math.max(latestTerminationTime, bopsJobDetails.getTerminationTime());
            }

            // Aggregate counts
            numTotalTasks += (bopsJobDetails.getNumOfTotalTasks() != null
                    ? bopsJobDetails.getNumOfTotalTasks() : 0L);
            numTasksSuccess += (bopsJobDetails.getNumOfTasksSucceeded() != null
                    ? bopsJobDetails.getNumOfTasksSucceeded() : 0L);
            numTasksFailed += (bopsJobDetails.getNumOfTasksFailed() != null
                    ? bopsJobDetails.getNumOfTasksFailed() : 0L);

            activeStatuses.add(S3Monitor.BOPSStatus.valueOf(bopsJobDetails.getJobStatus()));
        }

        // Determine overall status after checking all jobs
        S3Monitor.BOPSStatus jobStatus = determineOverallBopsStatus(activeStatuses);

        if (earliestCreationTime == Long.MAX_VALUE) {
            earliestCreationTime = 0L;
        }
        if (jobStatus == S3Monitor.BOPSStatus.BOPS_RUNNING) {
            latestTerminationTime = 0L;
        }

        Double percentProgress = numTotalTasks > 0
                ? ((double) (numTasksSuccess + numTasksFailed) / numTotalTasks) * 100.0 : 0.0;

        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(String.valueOf(jobStatus))
                .bopsNumOfTasksFailed(numTasksFailed)
                .bopsNumOfTasksSucceeded(numTasksSuccess)
                .bopsNumOfTotalTasks(numTotalTasks)
                .bopsPercentProgress(percentProgress)
                .creationTime(earliestCreationTime)
                .terminationTime(latestTerminationTime)
                .build();
        workflowModel.setMonitoringDetails(monitoringDetails);
        workflowModel.setBopsJobDuration(getMigrationTimeInMinutes(earliestCreationTime, latestTerminationTime));
        workflowModel.setState(String.valueOf(jobStatus));
    }

    /**
     * Calculate CRR monitoring details.
     * This function is used to calculate the CRR monitoring details
     * for the entire workflow.
     * @param cloudWatchClient cloudwatchClient
     */
    public void calculateCrrMonitoringDetails(final CloudWatchClient cloudWatchClient) {
        final String sourceBucketName = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();
        final String destBucketName = Arn.fromString(workflowModel.getDestBucketARN()).resourceAsString();
        final String jobStatus = workflowModel.getMonitoringDetails().getBopsJobStatus();

        final S3MonitorDetails crrDetails = S3Monitor.calculateCRRStatus(
                cloudWatchClient, sourceBucketName, workflowModel.getDestBucketARN(),
                workflowModel.getDestRegion(), destBucketName);
        String crrStatus = crrDetails.getCrrStatus();
        String ackNotificationId = (workflowModel.getAckedNotification() == null) ? "" :
                workflowModel.getAckedNotification();

        // Derive workflow Status and State
        String workflowStatus = S3Monitor.deriveWorkflowStatus(ackNotificationId,
                jobStatus,
                crrStatus,
                workflowModel.getStatus());
        if (workflowStatus.equals(WorkflowStatus.WAITING.name())) {
            crrStatus = S3Monitor.CRRStatus.CRR_RUNNING.name();
            workflowModel.setBackfillCompletedAt(Instant.now().toString());
        }
        if (!jobStatus.equals(S3Monitor.BOPSStatus.BOPS_RUNNING.name())) {
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

    private String getMigrationTimeInMinutes(final Long creationTime, final Long terminationTime) {
        if (creationTime == null || creationTime == 0L) {
            return "0";
        }

        long endTime = (terminationTime != null && terminationTime != 0L)
                ? terminationTime : System.currentTimeMillis();
        long activeTime = Math.max(0, (endTime - creationTime) / 60000);

        return String.valueOf(activeTime);
    }

    private S3Monitor.BOPSStatus determineOverallBopsStatus(final Set<S3Monitor.BOPSStatus> activeStatuses) {
        if (activeStatuses.contains(S3Monitor.BOPSStatus.BOPS_RUNNING)) {
            return S3Monitor.BOPSStatus.BOPS_RUNNING;
        } else if (activeStatuses.contains(S3Monitor.BOPSStatus.BOPS_FAILED)) {
            return S3Monitor.BOPSStatus.BOPS_FAILED;
        } else if (activeStatuses.contains(S3Monitor.BOPSStatus.BOPS_STATUS_ERROR)) {
            return S3Monitor.BOPSStatus.BOPS_STATUS_ERROR;
        } else {
            return S3Monitor.BOPSStatus.BOPS_FINISHED;
        }
    }

    private String buildAlarmName() {
        return String.format("%s-%s-%s",
                ALARM_PREFIX,
                workflowModel.getNamespaceID(),
                workflowModel.getWorkflowName());
    }

    private String buildAlarmDescription() {
        final String sourceBucketName = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();
        return String.format("S3A Migration failure for namespaceID: %s, workflowName: %s, bucket: %s, "
                        + "for more details see: %s",
                workflowModel.getNamespaceID(),
                workflowModel.getWorkflowName(),
                sourceBucketName,
                SOP_URL);
    }

    private String buildTicketArn(final String alarmName) {
        // Reference: https://w.amazon.com/bin/view/CloudWatchAlarms/Internal/CloudWatchAlarmsSIMTicketing/
        return String.format("%s:%s:%s:%s:%s:%s:%s", TICKET_ARN_PREFIX,
                TICKET_SEVERITY, // Severity
                TICKET_CATEGORY, // Category
                TICKET_TYPE, // Type
                TICKET_ITEM, // Item
                TICKET_RESOLVER, // Resolver
                alarmName); // De-dupe string
    }
}