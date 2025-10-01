package com.amazon.bopspar.service.resources.monitor;

public final class MonitorConstants {

    private MonitorConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final String METRICS_NAMESPACE = "TDMS3MigrationAccelerator";
    public static final String WORKFLOW_NAME_DIMENSION = "workflowName";
    public static final String NAMESPACE_ID_DIMENSION = "namespaceID";
    public static final String CRR_LATENCY_METRIC = "CRRLatency";
    public static final String CRR_BYTES_PENDING_METRIC = "CRRBytesPending";
    public static final String BOPS_TASKS_FAILED_METRIC = "BOPSFailedTasks";
    public static final String BOPS_TASKS_TOTAL_METRIC = "BOPSTotalTasks";
    public static final String BOPS_TASKS_SUCCEEDED_METRIC = "BOPSTasksSucceeded";
    public static final String BOPS_PROGRESS_PCT_METRIC = "BOPSProgressPct";
    public static final String WORKFLOW_STATUS_FAILED = "S3AWorkflowStatusFailure";
    public static final String TICKET_ARN_PREFIX = "arn:aws:cloudwatch::cwa-internal:ticket";
    public static final String ALARM_PREFIX = "S3AFailedAlarm";
    public static final String SOP_URL = "https://quip-amazon.com/9dvyAjCCGajd/S3A-Move-My-Bucket-SOP";

    //The maximum number of seconds by which the replication destination bucket is behind
    //the source bucket for a given replication rule.
    public static final String S3_REPLICATION_LATENCY = "ReplicationLatency";
    // The total number of bytes of objects that are pending replication for a given replication rule.
    public static final String S3_BYTES_PENDING_REPLICATION = "BytesPendingReplication";
    // We'll consider CRR complete when BytesPendingReplication = zero over a period of 15 minutes
    public static final int CRR_BYTESPENDING_THRESHOLD_VALUE = 0;
    public static final int CRR_BYTESPENDING_THRESHOLD_PERIOD = 600000;
    public static final String STOP_TRAFFIC_NOTIFICATION_ID = "STOP_SOURCE_TRAFFIC_ACK";

    // Alarm constants
    public static final String TICKET_SEVERITY = "4";
    public static final String TICKET_CATEGORY = "Company-wide Services";
    public static final String TICKET_TYPE = "CDMS";
    public static final String TICKET_ITEM = "S3A-Autocut";
    public static final String TICKET_RESOLVER = "CDMS";

    // URL Constants
    public static final String JOB_COMPLETION_REPORT_CONSOLE_URL_FORMAT = "https://%s.console.aws.amazon.com/s3/buckets/%s?region=%s&prefix=%s";

    public enum BOPSStatus {
        BOPS_RUNNING,
        BOPS_FAILED,
        BOPS_FINISHED,
        BOPS_STATUS_ERROR
    }

    public enum CRRStatus {
        CRR_RUNNING,
        CRR_FAILED,
        CRR_FINISHED,
        CRR_STATUS_ERROR
    }
}
