package com.amazon.tdm.s3a.service.resources.monitor.cloudwatch;

import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.model.MonitoringDetails;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.ALARM_PREFIX;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_PROGRESS_PCT_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_TASKS_FAILED_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_TASKS_SUCCEEDED_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.BOPS_TASKS_TOTAL_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.CRR_BYTES_PENDING_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.CRR_LATENCY_METRIC;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.METRICS_NAMESPACE;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.NAMESPACE_ID_DIMENSION;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.SOP_URL;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.TICKET_ARN_PREFIX;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.TICKET_CATEGORY;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.TICKET_ITEM;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.TICKET_RESOLVER;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.TICKET_SEVERITY;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.TICKET_TYPE;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.WORKFLOW_NAME_DIMENSION;
import static com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants.WORKFLOW_STATUS_FAILED;

/**
 * CloudWatch Manager class used for metrics and alarms.
 */
public class CloudWatchManager {
    private static final Logger LOGGER = LogManager.getLogger(CloudWatchManager.class);

    /**
     * Create CW Alarm for failed bucket migrations.
     *
     * @param s3aCloudWatchClient CW client for operations in the S3A account.
     */
    public void createCloudWatchAlarm(final CloudWatchClient s3aCloudWatchClient, final WorkFlowModel workflowModel) {
        String alarmDesc = buildAlarmDescription(workflowModel);
        String alarmName = buildAlarmName(workflowModel);
        String ticketArn = buildTicketArn(alarmName);

        if (alarmExists(s3aCloudWatchClient, alarmName)) {
            LOGGER.info("Alarm {} already exists! - skipping creation...", alarmName);
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
            LOGGER.info("Successfully created alarm with status code: {}", response.sdkHttpResponse().statusCode());

        } catch (Exception e) {
            LOGGER.error("Failed to set alarm! {}", e);
        }
    }

    /**
     * Publish custom S3A metrics to cloudwatch.
     *
     * @param s3aCloudWatchClient CW client for operations in the S3A account.
     * @return True if successful.
     */
    public boolean publishCWMetrics(final CloudWatchClient s3aCloudWatchClient, final WorkFlowModel workflowModel) {
        final MonitoringDetails s3MonitorDetails = workflowModel.getMonitoringDetails();
        try {
            LOGGER.info("Publishing metrics for BOPS Job, workflowName: {}, namespaceId: {}",
                    workflowModel.getWorkflowName(),
                    workflowModel.getNamespaceID());

            List<MetricDatum> metricDatums = new ArrayList<>();

            if (workflowModel.getStatus().equals(WorkflowStatus.FAILED.name())) {
                metricDatums.add(buildMetricDatum(WORKFLOW_STATUS_FAILED, StandardUnit.COUNT,1.0, workflowModel));
            } else {
                metricDatums.add(buildMetricDatum(WORKFLOW_STATUS_FAILED, StandardUnit.COUNT,0.0, workflowModel));
            }
            metricDatums.add(buildMetricDatum(CRR_BYTES_PENDING_METRIC,
                    StandardUnit.BYTES, (double) s3MonitorDetails.getCrrBytesPendingReplication(), workflowModel));

            metricDatums.add(buildMetricDatum(CRR_LATENCY_METRIC,
                    StandardUnit.SECONDS, (double) s3MonitorDetails.getCrrLatency(), workflowModel));

            metricDatums.add(buildMetricDatum(BOPS_TASKS_TOTAL_METRIC,
                    StandardUnit.COUNT, (double) s3MonitorDetails.getBopsNumOfTotalTasks(), workflowModel));

            metricDatums.add(buildMetricDatum(BOPS_TASKS_FAILED_METRIC,
                    StandardUnit.COUNT, (double) s3MonitorDetails.getBopsNumOfTasksFailed(), workflowModel));

            metricDatums.add(buildMetricDatum(BOPS_TASKS_SUCCEEDED_METRIC,
                    StandardUnit.COUNT, (double) s3MonitorDetails.getBopsNumOfTasksSucceeded(), workflowModel));

            metricDatums.add(buildMetricDatum(BOPS_PROGRESS_PCT_METRIC,
                    StandardUnit.PERCENT, s3MonitorDetails.getBopsPercentProgress(), workflowModel));

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(METRICS_NAMESPACE)
                    .metricData(metricDatums)
                    .build();
            s3aCloudWatchClient.putMetricData(request);
            LOGGER.info("Published CW metrics: {}", metricDatums);
        } catch (Exception e) {
            LOGGER.error("Publishing CW metrics failed: {}", e);
            return false;
        }
        return true;
    }

    /**
     * Method used to build metric datum for S3A.
     * Dimensions for metric are workflowName and namespaceID
     * for uniqueness in creating dashboard.
     *
     */
    private MetricDatum buildMetricDatum(final String metricName,
                                         final StandardUnit unit,
                                         final double value,
                                         final WorkFlowModel workflowModel) {
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
            LOGGER.error("Could not list alarm! {}", e);
            return false;
        }
    }

    private String buildAlarmName(final WorkFlowModel workflowModel) {
        return String.format("%s-%s-%s",
                ALARM_PREFIX,
                workflowModel.getNamespaceID(),
                workflowModel.getWorkflowName());
    }

    private String buildAlarmDescription(final WorkFlowModel workflowModel) {
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
