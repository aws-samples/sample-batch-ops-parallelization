package com.amazon.bopspar.persistence.ddb;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.EntityNotFoundException;
import com.amazon.bopspar.model.Workflow;
import com.amazon.bopspar.persistence.WorkflowTestBase;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDeleteExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.function.Function;
import java.util.logging.Logger;

import static com.amazonaws.util.ValidationUtils.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test class for WorkFlowRepository class
 */


class WorkflowRepositoryTest extends WorkflowTestBase {
    private WorkflowRepository workflowRepository;
    private BaseDynamoDBFacade mockDdbFacade;

    @Mock
    private Workflow workflow;

    @Mock
    private Logger log;

    @BeforeEach
    public void setUp() {
        mockDdbFacade = mock(BaseDynamoDBFacade.class);
        workflowRepository = new WorkflowRepository(mockDdbFacade);
    }

    @Test
    public void testCreateWorkflowSuccess() {
        // Arrange
        WorkFlowModel workflow = new WorkFlowModel();
        workflow.setWorkflowName("TestWorkflow");
        workflow.setNamespaceID("TestNamespace");

        // Act
        workflowRepository.createWorkflow(workflow);

        // Assert
        ArgumentCaptor<WorkFlowModel> workflowCaptor = ArgumentCaptor.forClass(WorkFlowModel.class);
        ArgumentCaptor<Function<WorkFlowModel, DynamoDBSaveExpression>> functionCaptor
                = ArgumentCaptor.forClass(Function.class);

        // Verify that ddbFacade.create() is called with the correct arguments
        verify(mockDdbFacade, times(1)).create(workflowCaptor.capture(), functionCaptor.capture());

        // Ensure the correct WorkFlowModel is passed to ddbFacade.create()
        assertEquals("TestWorkflow", workflowCaptor.getValue().getWorkflowName());
        assertEquals("TestNamespace", workflowCaptor.getValue().getNamespaceID());
    }

    @Test
    public void testCreateWorkflowWithNullWorkflow() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () -> {
            workflowRepository.createWorkflow(null);
        });

        // Ensure that ddbFacade is never called when null is passed
        verifyNoInteractions(mockDdbFacade);
    }

    @Test
    public void testCreateWorkflowDDBFacadeException() {
        // Arrange
        WorkFlowModel workflow = WorkFlowModel.builder()
                .workflowName("TestWorkflow")
                .namespaceID("TestNamespace")
                .build();

        doThrow(new RuntimeException("DDB Error")).when(mockDdbFacade).create(any(), any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            workflowRepository.createWorkflow(workflow);
        });
    }


    @Test
    public void testGetWorkflowSuccess() {
        // Arrange
        String workflowName = "testWorkflow";
        String nameSpaceID = "testNamespace";

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName(workflowName);
        mockWorkflowModel.setNamespaceID(nameSpaceID);

        // Mocking ddbFacade behavior to return a valid workflow
        when(mockDdbFacade.get(any(WorkFlowModel.class), any())).thenReturn(mockWorkflowModel);

        // Act
        WorkFlowModel result = workflowRepository.getWorkflow(workflowName, nameSpaceID);

        // Assert
        assertNotNull(result, "Resulting workflow model should not be null");
        assertEquals(workflowName, result.getWorkflowName(), "Workflow name should match the input");
        assertEquals(nameSpaceID, result.getNamespaceID(), "Namespace ID should match the input");
        verify(mockDdbFacade, times(1)).get(any(WorkFlowModel.class), any()); // Verifying that ddbFacade's get method was called once
    }

    @Test
    public void testGetWorkflowNotFound() {
        // Arrange
        String workflowName = "nonExistentWorkflow";
        String nameSpaceID = "testNamespace";

        // Mocking ddbFacade to return null, simulating a not found case
        when(mockDdbFacade.get(any(WorkFlowModel.class), any())).thenReturn(null);

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            workflowRepository.getWorkflow(workflowName, nameSpaceID);
        });

        String expectedMessage = String.format("Workflow does not exist for {workflow %s}", workflowName);
        assertEquals(expectedMessage, exception.getMessage(), "Exception message should match expected format");

        verify(mockDdbFacade, times(1)).get(any(WorkFlowModel.class), any()); // Verifying that ddbFacade's get method was called once
    }

    @Test
    public void testStartWorkflowSuccess() {
        // Arrange
        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setWorkflowName("TestWorkflow");
        workflowModel.setNamespaceID("TestNamespace");

        // Act
        workflowRepository.updateWorkflow(workflowModel);

        // Assert
        ArgumentCaptor<WorkFlowModel> workflowCaptor = ArgumentCaptor.forClass(WorkFlowModel.class);
        verify(mockDdbFacade, times(1)).update(workflowCaptor.capture(), any());

        // Verify the correct WorkFlowModel is passed to ddbFacade.update
        assertEquals("TestWorkflow", workflowCaptor.getValue().getWorkflowName());
        assertEquals("TestNamespace", workflowCaptor.getValue().getNamespaceID());
    }

    @Test
    public void testCreateDynamoDBSaveExpression() {
        // Act
        DynamoDBSaveExpression saveExpression = workflowRepository.createDynamoDBSaveExpression();

        // Assert
        assertNotNull(saveExpression, "SaveExpression should not be null");
        assertTrue(saveExpression.getExpected().containsKey("workflowName"), "SaveExpression should contain 'workflowName'");
        assertTrue(saveExpression.getExpected().containsKey("namespaceID"), "SaveExpression should contain 'namespaceID'");

        ExpectedAttributeValue expectedWorkflowName = saveExpression.getExpected().get("workflowName");
        ExpectedAttributeValue expectedNamespaceID = saveExpression.getExpected().get("namespaceID");
        assertNotNull(expectedWorkflowName, "ExpectedAttributeValue for 'workflowName' should not be null");
        assertFalse(expectedWorkflowName.getExists(), "'workflowName' should be expected to not exist");
        assertNotNull(expectedNamespaceID, "expectedNamespaceID for 'workflowName' should not be null");
        assertFalse(expectedNamespaceID.getExists(), "'expectedNamespaceID' should be expected to not exist");
    }

    @Test
    public void testCreateDynamoDBUpdateExpression() {
      /*  // Arrange
        WorkFlowModel workflow = new WorkFlowModel();
        workflow.setWorkFlowName("TestWorkflow");*/

        // Act
        DynamoDBSaveExpression saveExpression = workflowRepository.createDynamoDBUpdateExpression(new WorkFlowModel());

        // Assert
        assertNotNull(saveExpression, "SaveExpression should not be null");
        assertTrue(saveExpression.getExpected().containsKey("workflowName"), "SaveExpression should contain 'workflowName'");
        assertTrue(saveExpression.getExpected().containsKey("namespaceID"), "SaveExpression should contain 'workflowID'");

        ExpectedAttributeValue expectedWorkflowName = saveExpression.getExpected().get("workflowName");
        ExpectedAttributeValue expectedNamespaceID = saveExpression.getExpected().get("namespaceID");
        assertNotNull(expectedWorkflowName, "ExpectedAttributeValue for 'workflowName' should not be null");
        assertTrue(expectedWorkflowName.getExists(), "'workflowName' should exist");
        assertNotNull(expectedNamespaceID, "expectedNamespaceID for 'workflowName' should not be null");
        assertTrue(expectedNamespaceID.getExists(), "'expectedNamespaceID' should exist");
    }

    @Test
    public void testCreateDynamoDBDeleteExpression() {
        // Act
        DynamoDBDeleteExpression deleteExpression = workflowRepository.createDynamoDBDeleteExpression(new WorkFlowModel());

        // Assert
        assertNotNull(deleteExpression, "DeleteExpression should not be null");
        assertTrue(deleteExpression.getExpected().containsKey("workflowName"), "DeleteExpression should contain 'workflowName'");
        assertTrue(deleteExpression.getExpected().containsKey("namespaceID"), "DeleteExpression should contain 'workflowID'");

        ExpectedAttributeValue expectedWorkflowName = deleteExpression.getExpected().get("workflowName");
        ExpectedAttributeValue expectedNamespaceID = deleteExpression.getExpected().get("namespaceID");
        assertNotNull(expectedWorkflowName, "ExpectedAttributeValue for 'workflowName' should not be null");
        assertTrue(expectedWorkflowName.getExists(), "'workflowName' should exist");
        assertNotNull(expectedNamespaceID, "expectedNamespaceID for 'workflowName' should not be null");
        assertTrue(expectedNamespaceID.getExists(), "'expectedNamespaceID' should exist");
    }
}

