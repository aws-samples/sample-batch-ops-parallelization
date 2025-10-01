package com.amazon.bopspar.service.resources.workflow;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.responses.OrcaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

class WorkflowStatusManagerTest {

    @Mock
    private WorkflowRepository mockWorkflowRepository;

    @InjectMocks
    private WorkflowStatusManager workflowStatusManager;

    private WorkFlowModel workflowModel;

    @BeforeEach
    void setUp() {
        openMocks(this);
        workflowModel = new WorkFlowModel();
        workflowModel.setWorkflowName("testWorkflow");
        workflowModel.setNamespaceID("testNamespace");
    }

    @Test
    void handleStoppingStatus_WhenStatusIsStopping_UpdatesAndReturnsResponse() {
        // Arrange
        workflowModel.setStatus(WorkflowStatus.STOPPING.name());

        // Act
        OrcaResponse response = workflowStatusManager.handleStoppingStatus(workflowModel);

        // Assert
        assertNotNull(response);
        ArgumentCaptor<WorkFlowModel> modelCaptor = ArgumentCaptor.forClass(WorkFlowModel.class);
        verify(mockWorkflowRepository).updateWorkflow(modelCaptor.capture());

        WorkFlowModel capturedModel = modelCaptor.getValue();
        assertEquals(WorkflowStatus.STOPPED.name(), capturedModel.getStatus());
        assertEquals("CANCELLED", capturedModel.getState());
    }

    @Test
    void shouldContinueMonitoring_WhenStatusIsRunning_ReturnsTrue() {
        // Arrange
        workflowModel.setStatus(WorkflowStatus.RUNNING.name());

        // Act
        boolean result = workflowStatusManager.shouldContinueMonitoring(workflowModel);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldContinueMonitoring_WhenStatusIsStopping_ReturnsTrue() {
        // Arrange
        workflowModel.setStatus(WorkflowStatus.STOPPING.name());

        // Act
        boolean result = workflowStatusManager.shouldContinueMonitoring(workflowModel);

        // Assert
        assertTrue(result);
    }

    @Test
    void shouldContinueMonitoring_WhenStatusIsStopped_ReturnsFalse() {
        // Arrange
        workflowModel.setStatus(WorkflowStatus.STOPPED.name());

        // Act
        boolean result = workflowStatusManager.shouldContinueMonitoring(workflowModel);

        // Assert
        assertFalse(result);
    }

    @Test
    void shouldContinueMonitoring_WhenStatusIsNull_ReturnsTrue() {
        // Arrange
        workflowModel.setStatus(null);

        // Act
        boolean result = workflowStatusManager.shouldContinueMonitoring(workflowModel);

        // Assert
        assertFalse(result);
    }

    @Test
    void shouldContinueMonitoring_WhenStatusIsEmpty_ReturnsTrue() {
        // Arrange
        workflowModel.setStatus("");

        // Act
        boolean result = workflowStatusManager.shouldContinueMonitoring(workflowModel);

        // Assert
        assertFalse(result);
    }
}