package com.amazon.bopspar.service.validator;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRequestValidatorTest {

    @Test
    void testValidateWorkflowRequest_Success() {
        WorkflowRequest workflowRequest = WorkflowRequest.builder()
                .workflowName("workflowName")
                .namespaceID("namespaceID")
                .build();
        assertDoesNotThrow(() -> WorkflowRequestValidator.validateWorkflowRequest(workflowRequest));
    }

    @Test
    void testValidateWorkflowRequest_NoWorkflowName_ThrowsInvalidInputException() {
        WorkflowRequest workflowRequest = WorkflowRequest.builder()
                .namespaceID("namespaceID")
                .build();
        assertThrows(InvalidInputException.class, () -> WorkflowRequestValidator.validateWorkflowRequest(workflowRequest));
    }

    @Test
    void testValidateWorkflowRequest_NoNamespaceID_ThrowsInvalidInputException() {
        WorkflowRequest workflowRequest = WorkflowRequest.builder()
                .workflowName("workflowName")
                .build();
        assertThrows(InvalidInputException.class, () -> WorkflowRequestValidator.validateWorkflowRequest(workflowRequest));
    }
}
