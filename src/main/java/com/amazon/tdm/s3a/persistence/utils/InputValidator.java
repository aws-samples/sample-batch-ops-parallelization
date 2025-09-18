package com.amazon.tdm.s3a.persistence.utils;

import com.amazon.tdm.s3a.model.CreateWorkflowRequest;
import com.amazon.tdm.s3a.model.DeleteWorkflowRequest;
import com.amazon.tdm.s3a.model.GetWorkflowRequest;
import com.amazon.tdm.s3a.model.SendControlCommandRequest;
import com.amazon.tdm.s3a.model.StartWorkflowRequest;
import com.amazon.tdm.s3a.model.StopWorkflowRequest;
import lombok.extern.log4j.Log4j2;

import static com.amazon.tdm.s3a.persistence.utils.ExceptionUtils.checkArgument;

/**
 * Class for Input validations.
 */
@Log4j2
public final class InputValidator {

    private InputValidator() {
    }

    /**
     * Check if the create workflow request is valid. Verify if all required fields are passed
     *
     * @param request the create workflow request to be validated.
     */
    public static void validateCreateWorkflowRequest(final CreateWorkflowRequest request) {
        checkArgument(request != null, "CreateWorkflowRequest should not be null!");
        checkArgument(request.getWorkflow().getWorkflowName() != null, "Workflow name should not be null");
        checkArgument(request.getWorkflow().getWorkflowType() != null, "Workflow type should not be null");
        checkArgument(request.getWorkflow().getSourceAccountNumber() != null, "Source account number should "
                +
                "not be null");
        checkArgument(request.getWorkflow().getSourceRegion() != null, "Source region should not be null");
        checkArgument(request.getWorkflow().getNamespaceID() != null, "Namespace ID should not be null");
        checkArgument(request.getWorkflow().getSourceRoleARN() != null, "Source role ARN should not be null");
    }

    /**
     * Check if a Read workflow request is valid.
     * Verify if all required fields are passed
     *
     * @param request  - workflow request to be validated.
     */
    public static void validateGetWorkflowRequest(final GetWorkflowRequest request) {
        checkArgument(request != null, "GetWorkflowRequest should not be null!");
        checkArgument(request.getWorkflowName() != null, "Workflow name should not be null");
        checkArgument(request.getNamespaceID() != null, "Namespace ID should not be null");
    }

    /**
     * Check if a Start workflow request is valid.
     * Verify if all required fields are passed
     *
     * @param request  - workflow request to be validated.
     */
    public static void validateStartWorkflowRequest(final StartWorkflowRequest request) {
        checkArgument(request != null, "StartWorkflowRequest should not be null!");
        checkArgument(request.getWorkflowName() != null, "Workflow Name should not be null");
        checkArgument(request.getNamespaceID() != null, "Namespace ID should not be null");
    }

    public static void validateStopWorkflow(final StopWorkflowRequest request) {
        checkArgument(request != null, "StopWorkflowRequest should not be null!");
        checkArgument(request.getWorkflowName() != null, "Workflow Name should not be null");
        checkArgument(request.getNamespaceID() != null, "Namespace ID should not be null");
    }

    public static void validateDeleteWorkflow(final DeleteWorkflowRequest request) {
        checkArgument(request != null, "DeleteWorkflowRequest should not be null!");
        checkArgument(request.getWorkflowName() != null, "Workflow Name should not be null");
        checkArgument(request.getNamespaceID() != null, "Namespace ID should not be null");
    }

    public static void validateSendControlCommandRequest(final SendControlCommandRequest request) {
        checkArgument(request != null, "SendControlCommandRequest should not be null!");
        checkArgument(request.getWorkflowName() != null, "Workflow Name should not be null");
        checkArgument(request.getNamespaceID() != null, "Namespace ID should not be null");
        checkArgument(request.getNotificationID() != null, "Notification ID should not be null");
    }
}
