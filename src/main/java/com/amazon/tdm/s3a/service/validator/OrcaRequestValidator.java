package com.amazon.tdm.s3a.service.validator;

import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.service.requests.OrcaRequest;

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
