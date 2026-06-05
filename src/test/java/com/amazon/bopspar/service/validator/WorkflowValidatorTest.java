package com.amazon.bopspar.service.validator;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowValidatorTest {

    @Test
    void testValidateWorkflowArns_Success() {
        WorkFlowModel workFlowModel = WorkFlowModel.builder()
                .sourceBucketARN("arn:aws:s3:::src-bucket")
                .destBucketARN("arn:aws:s3:::dest-bucket")
                .build();
        assertDoesNotThrow(() -> WorkflowValidator.validateWorkflowArns(workFlowModel));
    }

    @Test
    void testValidateWorkflowArns_NoSourceBucketArn_ThrowsInvalidInputException() {
        WorkFlowModel workFlowModel = WorkFlowModel.builder()
                .destBucketARN("arn:aws:s3:::dest-bucket")
                .build();
        assertThrows(InvalidInputException.class, () -> WorkflowValidator.validateWorkflowArns(workFlowModel));
    }

    @Test
    void testValidateWorkflowArns_NoDestBucketArn_ThrowsInvalidInputException() {
        WorkFlowModel workFlowModel = WorkFlowModel.builder()
                .sourceBucketARN("arn:aws:s3:::src-bucket")
                .build();
        assertThrows(InvalidInputException.class, () -> WorkflowValidator.validateWorkflowArns(workFlowModel));
    }
}