package com.amazon.bopspar.service;

public class TestBase {
    protected static final String NAMESPACE_ID = "XXXXXXXXXXX";
    protected static final String WORKFLOW_NAME = "workflow-1";
    protected static final String WORKFLOW_TYPE = "type-1";
    protected static final String STATUS = "IN_PROGRESS";
    protected static final String SOURCE_ROLE_ARN = "arn::123";
    protected static final String DEST_ROLE_ARN = "arn::456";
    protected static final String SOURCE_ACCOUNT_NUMBER = "123456789";
    protected static final String SOURCE_REGION = "us-east-1";
    protected static final String DEST_REGION = "us-west-2";
    protected static final String BOPS_JOB_ID = "XXXXX";
    protected static final String NOTIFICATION_ID = "NotificationID1";
    protected static final String SOURCE_BUCKET_ARN = "arn:aws:s3:::TEST_BUCKET";

    // Write an enum for Job status
    protected enum JobStatus {
        IN_PROGRESS,
        FAILED,
        FINISHED;
    }
}