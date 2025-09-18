package com.amazon.tdm.s3a.service.validator;

import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

public final class WorkflowValidator {
    private WorkflowValidator() {

    }

    public static void validateWorkflowArns(final WorkFlowModel workFlow) {
        if (isNullOrEmpty(workFlow.getSourceBucketARN()) || isNullOrEmpty(workFlow.getDestBucketARN())) {
            throw new InvalidInputException("Invalid input! SourceBucketArn and DestBucketArn are required.");
        }
    }
}
