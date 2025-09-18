package com.amazon.tdm.s3a.persistence.manager;

public enum WorkflowStatus {
    READY,
    CREATING,
    CREATED,
    DELETING,
    DELETED,
    FINISHED,
    PAUSING,
    PAUSED,
    RESUMING,
    STARTING,
    STARTED,
    STOPPING,
    STOPPED,
    WAITING,
    RUNNING,
    ROLLED_BACK,
    ROLLBACK_FAILED,
    START_FAILED,
    STOP_FAILED,
    DELETE_FAILED,
    FAILED,
    ROLLING_BACK,
    PAUSE_FAILED;

    private WorkflowStatus() {
    }
}