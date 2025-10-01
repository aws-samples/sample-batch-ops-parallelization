package com.amazon.bopspar.service.validator;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.service.requests.OrcaRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrcaRequestValidatorTest {

    @Test
    void testValidateOrcaRequest_Success() {
        OrcaRequest orcaRequest = OrcaRequest.builder()
                .workflowName("workflowName")
                .namespaceID("namespaceID")
                .build();
        assertDoesNotThrow(() -> OrcaRequestValidator.validateOrcaRequest(orcaRequest));
    }

    @Test
    void testValidateOrcaRequest_NoWorkflowName_ThrowsInvalidInputException() {
        OrcaRequest orcaRequest = OrcaRequest.builder()
                .namespaceID("namespaceID")
                .build();
        assertThrows(InvalidInputException.class, () -> OrcaRequestValidator.validateOrcaRequest(orcaRequest));
    }

    @Test
    void testValidateOrcaRequest_NoNamespaceID_ThrowsInvalidInputException() {
        OrcaRequest orcaRequest = OrcaRequest.builder()
                .workflowName("workflowName")
                .build();
        assertThrows(InvalidInputException.class, () -> OrcaRequestValidator.validateOrcaRequest(orcaRequest));
    }
}