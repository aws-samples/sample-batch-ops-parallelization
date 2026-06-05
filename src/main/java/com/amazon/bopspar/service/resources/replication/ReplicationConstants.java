package com.amazon.bopspar.service.resources.replication;

public final class ReplicationConstants {

    private ReplicationConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final String BUCKET_ENCRYPTION = "bucketEncryption";
    public static final String BUCKET_KMS_KEY_POLICY = "bucketKmsKeyPolicy";
    public static final String BUCKET_CROSS_ACCOUNT_SID = "S3ASourceCrossAccountAccess";
    public static final String BUCKET_LIFECYCLE = "bucketLifecycle";
    public static final String CROSS_ACCOUNT_STATEMENT_SID = "S3ASourceCrossAccountAccess";
    public static final String BOPS_ROLE_NAME = "s3a-bops-permissions";
    public static final String JOB_REPORT_FORMAT = "Report_CSV_20180820";

}