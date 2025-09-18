package com.amazon.tdm.s3a.service.resources.workflow;

public enum WorkflowStateMachine {
    WORKFLOW_STATE_MACHINE("WORKFLOW_STATE_MACHINE"),
    MANIFEST_SPLIT_STATE_MACHINE("MANIFEST_SPLIT_STATE_MACHINE");

    private final String value;

    WorkflowStateMachine(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
