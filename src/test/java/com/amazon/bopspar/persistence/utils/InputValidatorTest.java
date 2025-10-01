package com.amazon.bopspar.persistence.utils;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.CreateWorkflowRequest;
import com.amazon.bopspar.model.DeleteWorkflowRequest;
import com.amazon.bopspar.model.GetWorkflowRequest;
import com.amazon.bopspar.model.SendControlCommandRequest;
import com.amazon.bopspar.model.StartWorkflowRequest;
import com.amazon.bopspar.model.StopWorkflowRequest;
import com.amazon.bopspar.model.Workflow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InputValidatorTest {

    @Test
    public void testValidateCreateWorkflowRequest_NullWorkflowName() {
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = new Workflow();
        request.setWorkflow(workflow);  // Workflow Name is null

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateCreateWorkflowRequest(request);
        });

        assertEquals("Workflow name should not be null", exception.getMessage());
    }

    @Test
    public void testValidateCreateWorkflowRequest_NullWorkflowType() {
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("TestWorkflow");
        request.setWorkflow(workflow);  // Workflow Type is null

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateCreateWorkflowRequest(request);
        });

        assertEquals("Workflow type should not be null", exception.getMessage());
    }

    @Test
    public void testValidateCreateWorkflowRequest_NullSourceAccountNumber() {
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("TestWorkflow");
        workflow.setWorkflowType("TestType");
        request.setWorkflow(workflow);  // Source Account Number is null

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateCreateWorkflowRequest(request);
        });

        assertEquals("Source account number should not be null", exception.getMessage());
    }

    @Test
    public void testValidateCreateWorkflowRequest_NullSourceRegion() {
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("TestWorkflow");
        workflow.setWorkflowType("TestType");
        workflow.setSourceAccountNumber("123456789");
        request.setWorkflow(workflow);  // Source Region is null

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateCreateWorkflowRequest(request);
        });

        assertEquals("Source region should not be null", exception.getMessage());
    }

    @Test
    public void testValidateCreateWorkflowRequest_NullNamespaceID() {
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("TestWorkflow");
        workflow.setWorkflowType("TestType");
        workflow.setSourceAccountNumber("123456789");
        workflow.setSourceRegion("us-east-1");
        request.setWorkflow(workflow);  // Namespace ID is null

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateCreateWorkflowRequest(request);
        });

        assertEquals("Namespace ID should not be null", exception.getMessage());
    }

    @Test
    public void testValidateCreateWorkflowRequest_NullSourceRoleARN() {
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("TestWorkflow");
        workflow.setWorkflowType("TestType");
        workflow.setSourceAccountNumber("123456789");
        workflow.setSourceRegion("us-east-1");
        workflow.setNamespaceID("namespace123");
        request.setWorkflow(workflow);  // Source Role ARN is null

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateCreateWorkflowRequest(request);
        });

        assertEquals("Source role ARN should not be null", exception.getMessage());
    }

    @Test
    public void testValidateCreateWorkflowRequest_AllFieldsValid() {
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("TestWorkflow");
        workflow.setWorkflowType("TestType");
        workflow.setSourceAccountNumber("123456789");
        workflow.setSourceRegion("us-east-1");
        workflow.setNamespaceID("namespace123");
        workflow.setSourceRoleARN("arn:aws:iam::123456789012:role/TestRole");
        request.setWorkflow(workflow);

        assertDoesNotThrow(() -> InputValidator.validateCreateWorkflowRequest(request));
    }

    @Test
    void testValidateStartWorkflowRequest_ValidRequest() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setWorkflowName("TestWorkflow");
        request.setNamespaceID("testNamespaceID");

        assertDoesNotThrow(() -> InputValidator.validateStartWorkflowRequest(request));
    }

    @Test
    void testValidateStartWorkflowRequest_EmptyWorkflowName() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setWorkflowName("");
        request.setNamespaceID("testNamespaceID");

        // This test assumes that an empty string is considered valid.
        // If it should be invalid, you would use assertThrows instead.
        assertDoesNotThrow(() -> InputValidator.validateStartWorkflowRequest(request));
    }

    @Test
    public void testValidateStartWorkflowRequest_NullNamespaceID() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setWorkflowName("testWorkflowName");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateStartWorkflowRequest(request);
        });

        assertEquals("Namespace ID should not be null", exception.getMessage());
    }

    @Test
    public void testValidateStartWorkflowRequest_NullWorkflowName() {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setNamespaceID("testNamespaceID");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateStartWorkflowRequest(request);
        });

        assertEquals("Workflow Name should not be null", exception.getMessage());
    }

    @Test
    public void testValidateStartWorkflowRequest_NullRequest() {
        assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateStartWorkflowRequest(null);
        });
    }

    @Test
    public void testValidateDeleteWorkflowRequest_NullNamespaceID() {
        DeleteWorkflowRequest request = new DeleteWorkflowRequest();
        request.setWorkflowName("testWorkflowName");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateDeleteWorkflow(request);
        });

        assertEquals("Namespace ID should not be null", exception.getMessage());
    }

    @Test
    public void testValidateDeleteWorkflowRequest_NullWorkflowName() {
        DeleteWorkflowRequest request = new DeleteWorkflowRequest();
        request.setNamespaceID("testNamespaceID");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateDeleteWorkflow(request);
        });

        assertEquals("Workflow Name should not be null", exception.getMessage());
    }
    @Test
    public void testValidateDeleteWorkflowRequest_NullRequest() {
        assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateDeleteWorkflow(null);
        });
    }

    @Test
    public void testValidateStopWorkflowRequest_NullWorkflowName() {
        StopWorkflowRequest request = new StopWorkflowRequest();
        request.setNamespaceID("testNamespaceID");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateStopWorkflow(request);
        });

        assertEquals("Workflow Name should not be null", exception.getMessage());
    }

    @Test
    public void testValidateStopWorkflowRequest_NullNamespaceID() {
        StopWorkflowRequest request = new StopWorkflowRequest();
        request.setWorkflowName("testWorkflowName");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateStopWorkflow(request);
        });

        assertEquals("Namespace ID should not be null", exception.getMessage());
    }

    @Test
    public void testValidateStopWorkflowRequest_NullRequest() {
        assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateStopWorkflow(null);
        });
    }


    @Test
    public void validateSendControlCommandRequest_NullWorkflowName() {
        SendControlCommandRequest request = new SendControlCommandRequest();
        request.setNamespaceID("testNamespaceID");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateSendControlCommandRequest(request);
        });

        assertEquals("Workflow Name should not be null", exception.getMessage());
    }

    @Test
    public void validateSendControlCommandRequest_NullNamespaceID() {
        SendControlCommandRequest request = new SendControlCommandRequest();
        request.setWorkflowName("testWorkflowName");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateSendControlCommandRequest(request);
        });

        assertEquals("Namespace ID should not be null", exception.getMessage());
    }

    @Test
    public void validateSendControlCommandRequest_NullState() {
        SendControlCommandRequest request = new SendControlCommandRequest();
        request.setWorkflowName("testWorkflowName");
        request.setNamespaceID("testNamespaceID");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateSendControlCommandRequest(request);
        });

        assertEquals("Notification ID should not be null", exception.getMessage());
    }

    @Test
    public void validateSendControlCommandRequest_NullRequest() {
        assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateSendControlCommandRequest(null);
        });
    }

    @Test
    public void validateGetWorkflowRequest_NullWorkflowName() {
        GetWorkflowRequest request = new GetWorkflowRequest();
        request.setNamespaceID("testNamespaceID");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateGetWorkflowRequest(request);
        });

        assertEquals("Workflow name should not be null", exception.getMessage());
    }

    @Test
    public void validateGetWorkflowRequest_NullNampspaceID() {
        GetWorkflowRequest request = new GetWorkflowRequest();
        request.setWorkflowName("testWorkflowName");

        Exception exception = assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateGetWorkflowRequest(request);
        });

        assertEquals("Namespace ID should not be null", exception.getMessage());
    }

    @Test
    public void validateGetWorkflowRequest_NullRequest() {
        assertThrows(InvalidInputException.class, () -> {
            InputValidator.validateGetWorkflowRequest(null);
        });
    }
}
