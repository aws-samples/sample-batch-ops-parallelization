package com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig;

/**
 * Constants class to defined the glue job states.
 */
public final class GlueJobStates {

    private GlueJobStates() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final String GLUE_JOB_RUNNING = "RUNNING";
    public static final String GLUE_JOB_WAITING = "WAITING";
    public static final String GLUE_JOB_SUCCEEDED = "SUCCEEDED";
    public static final String GLUE_JOB_FAILED = "FAILED";
    public static final String GLUE_JOB_ERROR = "ERROR";
    public static final String GLUE_JOB_TIMEOUT = "TIMEOUT";
    public static final String GLUE_JOB_STOPPED = "STOPPED";



}