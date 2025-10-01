package com.amazon.bopspar.service.validator;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.service.requests.OrcaRequest;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

public final class OrcaRequestValidator {
    private OrcaRequestValidator() {

    }

    public static void validateOrcaRequest(final OrcaRequest orcaRequest) {
        if (isNullOrEmpty(orcaRequest.getWorkflowName()) || isNullOrEmpty(orcaRequest.getNamespaceID())) {
            throw new InvalidInputException("Invalid input! WorkflowName and NamespaceID are required.");
        }
    }
}
