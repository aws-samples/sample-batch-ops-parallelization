package com.amazon.bopspar.service.validator;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.service.requests.WorkflowRequest;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

public final class WorkflowRequestValidator {
    private WorkflowRequestValidator() {

    }

    public static void validateWorkflowRequest(final WorkflowRequest workflowRequest) {
        if (isNullOrEmpty(workflowRequest.getWorkflowName()) || isNullOrEmpty(workflowRequest.getNamespaceID())) {
            throw new InvalidInputException("Invalid input! WorkflowName and NamespaceID are required.");
        }
    }
}
