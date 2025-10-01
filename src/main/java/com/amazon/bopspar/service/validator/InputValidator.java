package com.amazon.bopspar.service.validator;

import com.amazon.bopspar.model.CreateWorkflowRequest;
import com.amazon.bopspar.model.DeleteWorkflowRequest;
import com.amazon.bopspar.model.EligibilityCheckRequest;
import com.amazon.bopspar.model.GetWorkflowRequest;
import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.SendControlCommandRequest;
import com.amazon.bopspar.model.StartWorkflowRequest;
import com.amazon.bopspar.model.StopWorkflowRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InputValidator {
    private static final Logger log = LogManager.getLogger(InputValidator.class);

    public InputValidator() {

    }

    public void validateCreateWorkflowRequest(final CreateWorkflowRequest request)
            throws InvalidInputException {
        if (request == null || request.getWorkflow() == null) {
            throw new InvalidInputException("Invalid input! Workflow is required.");
        }
    }

    public void validateDeleteWorkflowRequest(final DeleteWorkflowRequest request) throws InvalidInputException {
        if (request == null || request.getWorkflowName() == null || request.getNamespaceID() == null) {
            throw new InvalidInputException("Invalid input! WorkflowName and NamespaceID are required.");
        }
    }

    public void validateStopWorkflowRequest(final StopWorkflowRequest request) throws InvalidInputException {
        if (request == null || request.getNamespaceID() == null || request.getWorkflowName() == null) {
            throw new InvalidInputException("Invalid input! WorkflowName and NamespaceID are required.");
        }
    }

    public void validateStartWorkflowRequest(final StartWorkflowRequest request) throws InvalidInputException {
        if (request == null || request.getWorkflowName() == null || request.getNamespaceID() == null) {
            throw new InvalidInputException("Invalid input! WorkflowName and NamespaceID are required.");
        }
    }

    public void validateGetWorkflowRequest(final GetWorkflowRequest request) throws InvalidInputException {
        if (request == null || request.getWorkflowName() == null || request.getNamespaceID() == null) {
            throw new InvalidInputException("Invalid input! WorkflowName and NamespaceID are required.");
        }
    }

    public void validateSendControlCommandRequest(final SendControlCommandRequest request)
            throws InvalidInputException {
        if (request == null || request.getWorkflowName() == null || request.getNamespaceID() == null
                || request.getNotificationID() == null) {
            throw new InvalidInputException("Invalid input! WorkflowName, NamespaceID and"
                    + " notificationID are required.");
        }
    }

    public void validateEligibilityCheckRequest(final EligibilityCheckRequest request)
        throws InvalidInputException {

        if (request == null) {
            throw new InvalidInputException("Invalid input! Request is null");
        }
    }
}