package com.amazon.tdm.s3a.service.resources.monitor.dashboard;

import com.amazon.tdm.s3a.persistence.model.BopsJobDetails;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.utils.JsonMapperUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.s3control.model.JobStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.DEFAULT_REGION;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.DEFAULT_WIDGET_HEIGHT;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.DEFAULT_WIDGET_WIDTH;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.HEADER_HEIGHT;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.HEADER_WIDTH;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.MAX_TABLE_WIDTH;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.METRICS_NAMESPACE;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.SECTION_HEADER_HEIGHT;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.TABLE_HEIGHT;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.TABLE_WIDTH;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.TIME_SERIES_HEIGHT;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.TIME_SERIES_WIDTH;

/**
 * Utility class to create dashboard body for S3 Accelerator Monitor.
 */
public class S3AcceleratorDashboard {

    /**
     * Creates the dashboard body for the S3 Accelerator Monitor.
     * @param workflowModel Workflow model containing the necessary information
     * @return Dashboard body in JSON format
     */
    public String createDashboardBody(final WorkFlowModel workflowModel) {
        final ObjectNode dashboardNode = JsonMapperUtil.createObjectNode();
        final ArrayNode widgetsNode = dashboardNode.putArray("widgets");

        final Map<JobDisplayStatus, Map<String, BopsJobDetails>> jobsByStatus = getJobDetailsByStatus(workflowModel);

        addHeader(widgetsNode, workflowModel);
        addBOPSSection(widgetsNode, jobsByStatus, workflowModel);
        addReplicationSection(widgetsNode, workflowModel);
        addCompletedJobsSection(widgetsNode, jobsByStatus, workflowModel);

        try {
            return JsonMapperUtil.writeValueAsString(dashboardNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create dashboard JSON", e);
        }
    }

    private void addHeader(final ArrayNode widgetsNode, final WorkFlowModel workflowModel) {
        final String srcBucketName = Arn.fromString(workflowModel.getSourceBucketARN()).resourceAsString();
        final String destBucketName = Arn.fromString(workflowModel.getDestBucketARN()).resourceAsString();

        addTextWidget(widgetsNode, 0, 0, HEADER_WIDTH, HEADER_HEIGHT, String.format(
                "# S3 Accelerator Monitor%n### Source bucket: %s%n### Destination bucket: %s",
                srcBucketName, destBucketName));
    }

    /**
     * Adds the Batch Operations (BOPS) section to the dashboard.
     */
    private void addBOPSSection(final ArrayNode widgetsNode,
                                       final Map<JobDisplayStatus, Map<String, BopsJobDetails>> jobsByStatus,
                                       final WorkFlowModel workflowModel) {
        addTextWidget(widgetsNode, 0, 3, HEADER_WIDTH, SECTION_HEADER_HEIGHT, "## Batch Operations Job Metrics");

        // Running jobs
        addTextWidget(widgetsNode, 0, 4, TABLE_WIDTH, TABLE_HEIGHT,
                createRunningJobsTable(jobsByStatus.get(JobDisplayStatus.RUNNING), workflowModel));

        // Pending jobs
        addTextWidget(widgetsNode, 10, 4, TABLE_WIDTH, TABLE_HEIGHT,
                createPendingJobsTable(jobsByStatus.get(JobDisplayStatus.PENDING), workflowModel));

        // Add BOPS Metrics
        addBOPSMetrics(widgetsNode, workflowModel);
    }

    private void addBOPSMetrics(final ArrayNode widgetsNode, final WorkFlowModel workflowModel) {
        addSingleValueMetricWidget(widgetsNode, "BOPSProgressPct", "Batch Operations Job Progress",
                0, 6, workflowModel);
        addSingleValueMetricWidget(widgetsNode, "BOPSTotalTasks", "Batch Operations Job Total Tasks",
                5, 6, workflowModel);
        addSingleValueMetricWidget(widgetsNode, "BOPSTasksSucceeded", "Batch Operations Job Tasks Succeeded",
                10, 6, workflowModel);
        addSingleValueMetricWidget(widgetsNode, "BOPSFailedTasks", "Batch Operations Job Failed Tasks",
                15, 6, workflowModel);
    }

    /**
     * Adds the Replication section to the dashboard.
     */
    private void addReplicationSection(final ArrayNode widgetsNode, final WorkFlowModel workflowModel) {
        addTextWidget(widgetsNode, 0, 10, HEADER_WIDTH, SECTION_HEADER_HEIGHT, "## S3 Replication Metrics");

        addTimeSeriesWidget(widgetsNode, "CRRBytesPending", "Bytes Pending Replication",
                0, 11, TIME_SERIES_WIDTH, TIME_SERIES_HEIGHT, workflowModel);
        addTimeSeriesWidget(widgetsNode, "CRRLatency", "Replication Latency",
                10, 11, TIME_SERIES_WIDTH, TIME_SERIES_HEIGHT, workflowModel);
    }

    /**
     * Adds the completed jobs section to the dashboard.
     */
    private void addCompletedJobsSection(final ArrayNode widgetsNode,
                                                final Map<JobDisplayStatus, Map<String, BopsJobDetails>> jobsByStatus,
                                                final WorkFlowModel workflowModel) {
        // Completed jobs
        addTextWidget(widgetsNode, 0, 12, MAX_TABLE_WIDTH, TABLE_HEIGHT,
                createCompletedJobsTable(jobsByStatus.get(JobDisplayStatus.COMPLETED), workflowModel));
    }

    /**
     * Adds a text widget to the dashboard.
     * @param x The x-coordinate of the widget
     * @param y The y-coordinate of the widget
     * @param width The width of the widget
     * @param height The height of the widget
     * @param markdown The markdown text to display in the widget
     */
    private void addTextWidget(final ArrayNode widgetsNode,
                                      final int x,
                                      final int y,
                                      final int width,
                                      final int height,
                                      final String markdown) {
        final ObjectNode widget = widgetsNode.addObject();
        widget.put("type", "text")
                .put("x", x)
                .put("y", y)
                .put("width", width)
                .put("height", height);

        widget.putObject("properties")
                .put("markdown", markdown);
    }

    /**
     * Adds a single value metric widget to the dashboard.
     */
    private void addSingleValueMetricWidget(final ArrayNode widgetsNode,
                                                   final String metricName,
                                                   final String title,
                                                   final int x,
                                                   final int y,
                                                   final WorkFlowModel workflowModel) {
        addSingleValueMetricWidget(widgetsNode, metricName, title, x, y,
                DEFAULT_WIDGET_WIDTH, DEFAULT_WIDGET_HEIGHT, workflowModel);
    }

    /**
     * Adds a single value metric widget to the dashboard with custom dimensions.
     * @param widgetsNode Node of dashboard widgets
     * @param metricName Name of metric
     * @param title Title of metric
     * @param x x-coordinate of widget
     * @param y y-coordinate of widget
     * @param width width of widget
     * @param height height of widget
     * @param workflowModel Workflow model containing the necessary information
     */
    private void addSingleValueMetricWidget(final ArrayNode widgetsNode,
                                                   final String metricName,
                                                   final String title,
                                                   final int x,
                                                   final int y,
                                                   final int width,
                                                   final int height,
                                                   final WorkFlowModel workflowModel) {
        final ObjectNode widget = widgetsNode.addObject();
        widget.put("type", "metric")
                .put("x", x)
                .put("y", y)
                .put("width", width)
                .put("height", height);

        final ObjectNode properties = widget.putObject("properties");
        properties.put("sparkline", true)
                .put("view", "singleValue")
                .put("region", DEFAULT_REGION)
                .put("title", title)
                .put("liveData", true)
                .put("period", 60)
                .put("setPeriodToTimeRange", false);

        final ArrayNode metrics = properties.putArray("metrics");
        metrics.addArray()
                .add(METRICS_NAMESPACE)
                .add(metricName)
                .add("namespaceID")
                .add(workflowModel.getNamespaceID())
                .add("workflowName")
                .add(workflowModel.getWorkflowName());
    }

    /**
     * Adds a time series widget to the dashboard.
     *
     * @param widgetsNode Node of dashboard widgets
     * @param metricName Name of metric
     * @param title Title of metric
     * @param x x-coordinate of widget
     * @param y y-coordinate of widget
     * @param width width of widget
     * @param height height of widget
     * @param workflowModel Workflow model containing the necessary information
     */
    private void addTimeSeriesWidget(final ArrayNode widgetsNode,
                                            final String metricName,
                                            final String title,
                                            final int x,
                                            final int y,
                                            final int width,
                                            final int height,
                                            final WorkFlowModel workflowModel) {
        final ObjectNode widget = widgetsNode.addObject();
        widget.put("type", "metric")
                .put("x", x)
                .put("y", y)
                .put("width", width)
                .put("height", height);

        final ObjectNode properties = widget.putObject("properties");
        properties.put("view", "timeSeries")
                .put("stacked", false)
                .put("region", DEFAULT_REGION)
                .put("title", title)
                .put("liveData", true)
                .put("period", 60)
                .put("setPeriodToTimeRange", false);

        final ArrayNode metrics = properties.putArray("metrics");
        metrics.addArray()
                .add(METRICS_NAMESPACE)
                .add(metricName)
                .add("namespaceID")
                .add(workflowModel.getNamespaceID())
                .add("workflowName")
                .add(workflowModel.getWorkflowName());
    }

    private String createRunningJobsTable(final Map<String, BopsJobDetails> jobs,
                                                 final WorkFlowModel workflowModel) {
        final StringBuilder jobDetails = new StringBuilder();
        jobDetails.append("### Running Jobs\n");

        if (jobs == null) {
            jobDetails.append("*No running jobs*\n\n");
            return jobDetails.toString();
        }

        jobDetails.append("| Job ID | Status | Started | Active Time "
                        + "| Succeeded (Rate) | Failed (Rate) |\n")
                .append("|---------|---------|---------|-------------"
                        + "|------------------|---------------|\n");

        jobs.forEach((jobId, details) -> {
            final String jobLink = getJobLink(workflowModel.getSourceRegion(),
                    workflowModel.getSourceAccountNumber(),
                    jobId);
            final long totalTasks = details.getNumOfTotalTasks() != null ? details.getNumOfTotalTasks() : 0;
            final long succeededTasks = details.getNumOfTasksSucceeded() != null ? details.getNumOfTasksSucceeded() : 0;
            final long failedTasks = details.getNumOfTasksFailed() != null ? details.getNumOfTasksFailed() : 0;

            final double successRate = totalTasks > 0 ? (succeededTasks * 100.0) / totalTasks : 0;
            final double failureRate = totalTasks > 0 ? (failedTasks * 100.0) / totalTasks : 0;

            // Calculate active time
            final String activeTime = calculateActiveTime(details.getElapsedTimeInActiveSeconds());

            // Format creation times
            final String startTime = formatTime(details.getCreationTime());

            jobDetails.append(
                    String.format("| [%s](%s) | %s | %s | %s | %d (%.1f%%) | %d (%.1f%%) |%n",
                            getShortJobId(jobId),
                            jobLink,
                            details.getSdkJobStatus(),
                            startTime,
                            activeTime,
                            succeededTasks,
                            successRate,
                            failedTasks,
                            failureRate
                    ));
        });

        return jobDetails.toString();
    }

    private String createPendingJobsTable(final Map<String, BopsJobDetails> jobs,
                                                 final WorkFlowModel workflowModel) {
        final StringBuilder jobDetails = new StringBuilder();
        jobDetails.append("### Pending Jobs\n");

        if (jobs == null) {
            jobDetails.append("*No pending jobs*\n\n");
            return jobDetails.toString();
        }

        jobDetails.append("| Job ID | Status | Created | Waiting Time |\n")
                .append("|---------|---------|---------|-------------|\n");

        jobs.forEach((jobId, details) -> {
            final String jobLink = getJobLink(workflowModel.getSourceRegion(),
                    workflowModel.getSourceAccountNumber(),
                    jobId);
            jobDetails.append(String.format("| [%s](%s) | %s | %s | %s |%n",
                    getShortJobId(jobId),
                    jobLink,
                    details.getSdkJobStatus(),
                    formatTime(details.getCreationTime()),
                    calculateActiveTime(
                            Duration.between(
                                    Instant.ofEpochMilli(details.getCreationTime()),
                                    Instant.now()
                            ).getSeconds()
                    )
            ));
        });
        return jobDetails.toString();
    }

    private String createCompletedJobsTable(final Map<String, BopsJobDetails> jobs,
                                                   final WorkFlowModel workflowModel) {
        final StringBuilder jobDetails = new StringBuilder();
        jobDetails.append("### Completed Jobs\n");

        if (jobs == null) {
            jobDetails.append("*No completed jobs*\n\n");
            return jobDetails.toString();
        }

        jobDetails.append("| Job ID | Status | Started | Duration "
                        + "| Succeeded (Rate) | Failed (Rate) | Completed | Report |\n")
                .append("|---------|---------|---------|----------"
                        + "|------------------|---------------|-----------|--------|\n");

        jobs.forEach((jobId, details) -> {
            final String jobLink = getJobLink(workflowModel.getSourceRegion(),
                    workflowModel.getSourceAccountNumber(),
                    jobId);
            final long totalTasks = details.getNumOfTotalTasks() != null ? details.getNumOfTotalTasks() : 0;
            final long succeededTasks = details.getNumOfTasksSucceeded() != null ? details.getNumOfTasksSucceeded() : 0;
            final long failedTasks = details.getNumOfTasksFailed() != null ? details.getNumOfTasksFailed() : 0;

            final double successRate = totalTasks > 0 ? (succeededTasks * 100.0) / totalTasks : 0;
            final double failureRate = totalTasks > 0 ? (failedTasks * 100.0) / totalTasks : 0;

            // Calculate active time
            final String activeTime = calculateActiveTime(details.getElapsedTimeInActiveSeconds());

            // Format creation and termination times
            final String startTime = formatTime(details.getCreationTime());
            final String completedTime = formatTime(details.getTerminationTime());

            jobDetails.append(
                    String.format("| [%s](%s) | %s | %s | %s | %d (%.1f%%) | %d (%.1f%%) | %s | %s |%n",
                            getShortJobId(jobId),
                            jobLink,
                            details.getSdkJobStatus(),
                            startTime,
                            activeTime,
                            succeededTasks,
                            successRate,
                            failedTasks,
                            failureRate,
                            completedTime,
                            details.getJobCompletionReportUrl() != null
                                    ? String.format("[View Report](%s)", details.getJobCompletionReportUrl())
                                    : "N/A"
                    ));
        });
        return jobDetails.toString();
    }

    private String formatTime(final Long timeMillis) {
        if (timeMillis == null) {
            return "N/A";
        }
        return Instant.ofEpochMilli(timeMillis)
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy h:mma z"));
    }

    private String calculateActiveTime(final Long elapsedTimeInSeconds) {
        if (elapsedTimeInSeconds == null) {
            return "N/A";
        }

        final Duration duration = Duration.ofSeconds(elapsedTimeInSeconds);

        return duration.toString()
                .substring(2) // Remove the "PT" prefix
                .toLowerCase()
                .replace("h", "h ")
                .replace("m", "m ")
                .trim();
    }

    private String getShortJobId(final String jobId) {
        return jobId.substring(jobId.length() - 12);
    }

    private String getJobLink(final String region, final String accountNumber, final String jobId) {
        return String.format("https://%s.console.aws.amazon.com/s3/jobs/%s?region=%s&account=%s",
                region, jobId, region, accountNumber);
    }

    private JobDisplayStatus categorizeJobStatus(final String sdkJobStatus) {
        if (sdkJobStatus == null) {
            return JobDisplayStatus.PENDING;
        }

        switch (JobStatus.fromValue(sdkJobStatus)) {
            case ACTIVE:
            case CANCELLING:
            case COMPLETING:
            case FAILING:
            case PAUSING:
                return JobDisplayStatus.RUNNING;

            case COMPLETE:
            case FAILED:
            case CANCELLED:
            case PAUSED:
            case SUSPENDED:
                return JobDisplayStatus.COMPLETED;

            case NEW:
            case READY:
            case PREPARING:
            default:
                return JobDisplayStatus.PENDING;
        }
    }

    private Map<JobDisplayStatus, Map<String, BopsJobDetails>> getJobDetailsByStatus(
            final WorkFlowModel workflowModel) {
        if (workflowModel == null || workflowModel.getBopsJobDetails() == null) {
            return Collections.emptyMap();
        }

        return workflowModel.getBopsJobDetails().entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> categorizeJobStatus(entry.getValue().getSdkJobStatus()),
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        )
                ));
    }
}