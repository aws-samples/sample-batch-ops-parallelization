package com.amazon.bopspar.persistence.manager;

import com.amazon.bopspar.model.AWSServiceException;
import com.amazon.bopspar.model.EntityNotFoundException;
import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.AlreadyExistsException;
import com.amazon.bopspar.model.CreateWorkflowRequest;
import com.amazon.bopspar.model.CreateWorkflowResponse;
import com.amazon.bopspar.model.DeleteWorkflowRequest;
import com.amazon.bopspar.model.DeleteWorkflowResponse;
import com.amazon.bopspar.model.GetWorkflowRequest;
import com.amazon.bopspar.model.GetWorkflowResponse;
import com.amazon.bopspar.model.SendControlCommandRequest;
import com.amazon.bopspar.model.SendControlCommandResponse;
import com.amazon.bopspar.model.StartWorkflowRequest;
import com.amazon.bopspar.model.StartWorkflowResponse;
import com.amazon.bopspar.model.StopWorkflowRequest;
import com.amazon.bopspar.model.StopWorkflowResponse;
import com.amazon.bopspar.model.Workflow;
import com.amazon.bopspar.persistence.WorkflowTestBase;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.persistence.utils.ModelConverter;
import com.amazon.bopspar.service.resources.workflow.WorkflowStateMachine;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessResponse;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for WorkFlowManagerImpl class
 */
@ExtendWith(MockitoExtension.class)
public class WorkflowManagerCRUDImplTest extends WorkflowTestBase {

    private WorkflowManagerCRUDImpl workflowManager;
    private WorkflowRepository mockWorkflowRepository;

    @Mock
    private SfnClient sfnClient;

    @Mock
    private Gson gson;

    @BeforeEach
    public void setUp() {
        mockWorkflowRepository = mock(WorkflowRepository.class);
        workflowManager = new WorkflowManagerCRUDImpl(mockWorkflowRepository, sfnClient, gson);
    }

    @Test
    public void testCreateWorkflow_WithValidInput() {
        // Arrange
        Workflow workflow = getTestWorkflow();

        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
            .withWorkflow(workflow)
            .build();

        Mockito.doNothing().when(mockWorkflowRepository).createWorkflow(Mockito.any(WorkFlowModel.class));

        // Act
        CreateWorkflowResponse response = workflowManager.createWorkflow(request);

        // Assert
        Assertions.assertNotNull(response);
        Mockito.verify(mockWorkflowRepository, Mockito.times(1))
            .createWorkflow(Mockito.any(WorkFlowModel.class));
    }

    @Test
    public void testCreateWorkflow_WithNullInput() {
        // Arrange
        CreateWorkflowRequest request = null;

        // Act & Assert
        Assertions.assertThrows(InvalidInputException.class, () -> {
            workflowManager.createWorkflow(request);
        });
    }

    @Test
    @DisplayName("Should throw AWSServiceException when DynamoDB throws exception")
    public void testCreateWorkflow_DynamoDBException() {
        // Arrange
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
            .withWorkflow(getTestWorkflow())
            .build();

        WorkFlowModel workflowModel = getWorkflowModel();

        try (MockedStatic<ModelConverter> mockedConverter = mockStatic(ModelConverter.class)) {
            // Mock the converter
            mockedConverter.when(() -> ModelConverter.convertWorkflowFromCoralToDdbModel(request))
                .thenReturn(workflowModel);

            // Mock repository to throw AmazonDynamoDBException
            String errorMessage = "DynamoDB error occurred";
            doThrow(new AmazonDynamoDBException(errorMessage))
                .when(mockWorkflowRepository)
                .createWorkflow(any(WorkFlowModel.class));

            // Act & Assert
            AWSServiceException exception = assertThrows(AWSServiceException.class, () ->
                workflowManager.createWorkflow(request));

            // Verify exception details
            assertTrue(exception.getMessage().contains("DynamoDB error occurred"));
            assertTrue(exception.getCause() instanceof AmazonDynamoDBException);

            // Verify repository was called
            verify(mockWorkflowRepository).createWorkflow(any(WorkFlowModel.class));
        }
    }

    @Test
    @DisplayName("Should throw AlreadyExistsException when workflow already exists")
    public void testCreateWorkflow_AlreadyExists() {
        // Arrange
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
            .withWorkflow(getTestWorkflow())
            .build();

        WorkFlowModel workflowModel = getWorkflowModel();

        try (MockedStatic<ModelConverter> mockedConverter = mockStatic(ModelConverter.class)) {
            // Mock the converter
            mockedConverter.when(() -> ModelConverter.convertWorkflowFromCoralToDdbModel(request))
                .thenReturn(workflowModel);

            // Mock repository to throw ConditionalCheckFailedException
            doThrow(new ConditionalCheckFailedException("Conditional check failed"))
                .when(mockWorkflowRepository)
                .createWorkflow(any(WorkFlowModel.class));

            // Act & Assert
            AlreadyExistsException exception = assertThrows(AlreadyExistsException.class, () ->
                workflowManager.createWorkflow(request));

            // Verify exception message
            assertTrue(exception.getMessage().contains("A workflow already exists"));
            assertTrue(exception.getMessage().contains(workflowModel.getWorkflowName()));
            assertTrue(exception.getMessage().contains(workflowModel.getNamespaceID()));

            // Verify repository was called
            verify(mockWorkflowRepository).createWorkflow(any(WorkFlowModel.class));
        }
    }

    @Test
    public void testGetWorkflow_WithValidInput() {
        // Arrange
        String workflowName = "Test Workflow";
        String namespaceID = "namespace-123";

        GetWorkflowRequest request = GetWorkflowRequest.builder()
            .withWorkflowName(workflowName)
            .withNamespaceID(namespaceID)
            .build();

        WorkFlowModel workflowModel = getWorkflowModel();
        Mockito.when(mockWorkflowRepository.getWorkflow(workflowName, namespaceID)).thenReturn(workflowModel);

        // Act
        GetWorkflowResponse response = workflowManager.getWorkflow(request);

        // Assert
        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getWorkflow());
        Assertions.assertEquals(workflowName, response.getWorkflow().getWorkflowName());
        Assertions.assertEquals(namespaceID, response.getWorkflow().getNamespaceID());
        Assertions.assertEquals("ACTIVE", response.getWorkflow().getState());
        Assertions.assertEquals("COPY", response.getWorkflow().getWorkflowType());
        Assertions.assertEquals("RUNNING", response.getWorkflow().getStatus());
        Assertions.assertEquals("us-east-1", response.getWorkflow().getSourceRegion());
        Assertions.assertEquals("us-west-2", response.getWorkflow().getDestRegion());
        Assertions.assertEquals("arn:aws:iam::123456789:role/source-role", response.getWorkflow().getSourceRoleARN());
        Assertions.assertEquals("arn:aws:iam::987654321:role/dest-role", response.getWorkflow().getDestRoleARN());
        Assertions.assertEquals("123456789", response.getWorkflow().getSourceAccountNumber());
        Assertions.assertEquals("987654321", response.getWorkflow().getDestAccountNumber());
        Assertions.assertEquals("job-123", response.getWorkflow().getBopsJobID());
        Assertions.assertEquals("testUrl", response.getWorkflow().getRuntimeConfig().getDashboardUrl());
        Assertions.assertEquals("testManifestLocation", response.getWorkflow().getRuntimeConfig().getManifestLocation());
        Mockito.verify(mockWorkflowRepository, Mockito.times(1)).getWorkflow(workflowName, namespaceID);
    }

    @Test
    public void testGetWorkflow_WithWorkflowNotFound() {
        // Arrange
        String workflowName = "Non-Existent Workflow";
        String namespaceID = "namespace-123";

        GetWorkflowRequest request = GetWorkflowRequest.builder()
            .withWorkflowName(workflowName)
            .withNamespaceID(namespaceID)
            .build();

        Mockito.when(mockWorkflowRepository.getWorkflow(workflowName, namespaceID))
            .thenThrow(new EntityNotFoundException("Workflow not found"));

        // Act & Assert
        Assertions.assertThrows(EntityNotFoundException.class, () -> {
            workflowManager.getWorkflow(request);
        });

        Mockito.verify(mockWorkflowRepository, Mockito.times(1)).getWorkflow(workflowName, namespaceID);
    }

    @Test
    public void testStartWorkflowSuccess() {
        // Arrange
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setWorkflowName("testWorkflow");
        request.setNamespaceID("testNamespace");

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflow");
        mockWorkflowModel.setNamespaceID("testNamespace");
        mockWorkflowModel.setStatus("READY");

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);
        when(sfnClient.startExecution(any(StartExecutionRequest.class))).thenReturn(StartExecutionResponse.builder()
            .executionArn("testArn")
            .build());
        // Act
        StartWorkflowResponse response = workflowManager.startWorkflow(request, WorkflowStateMachine.WORKFLOW_STATE_MACHINE);

        // Assert
        assertNotNull(response);
        assertEquals("RUNNING", mockWorkflowModel.getStatus());

        // Verify repository interactions
        verify(mockWorkflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");
        verify(mockWorkflowRepository, times(1)).updateWorkflow(mockWorkflowModel);
    }

    @Test
    public void testStartWorkflow_ConditionalCheckFailedException() {
        // Arrange
        StartWorkflowRequest request = StartWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus("STARTING");

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        Assertions.assertThrows(AWSServiceException.class, () -> {
            workflowManager.startWorkflow(request, WorkflowStateMachine.WORKFLOW_STATE_MACHINE);
        });
    }


    @Test
    public void testAlreadyExistsExceptionThrown() {
        // Arrange
        CreateWorkflowRequest request = new CreateWorkflowRequest();
        Workflow workflow = getTestWorkflow();
        request.setWorkflow(workflow);

        WorkFlowModel workflowModel = getWorkflowModel();

        // Mock static method using Mockito's mockStatic
        try (MockedStatic<ModelConverter> mocked = mockStatic(ModelConverter.class)) {
            mocked.when(() -> ModelConverter.convertWorkflowFromCoralToDdbModel(request)).thenReturn(workflowModel);

            // Simulate the AlreadyExistsException being thrown by the repository
            doThrow(new AlreadyExistsException("A workflow already exists with workflowName: Test Workflow, namespaceID: namespace-123")).when(mockWorkflowRepository).createWorkflow(any(WorkFlowModel.class));

            // Act & Assert
            AlreadyExistsException thrownException = assertThrows(AlreadyExistsException.class, () -> {
                workflowManager.createWorkflow(request);
            });

            // Verify the correct message is part of the thrown exception
            assertEquals("A workflow already exists with workflowName: Test Workflow, namespaceID: namespace-123", thrownException.getMessage());
        }
    }

    @Test
    public void testStopWorkflowSuccess_when_started() {
        // Arrange
        StopWorkflowRequest request = StopWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.RUNNING));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        StopWorkflowResponse response = workflowManager.stopWorkflow(request);

        // Assert
        assertNotNull(response);
    }

    @Test
    public void testStopWorkflowSuccess_WorkflowWaiting() {
        // Arrange
        StopWorkflowRequest request = StopWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.WAITING));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        StopWorkflowResponse response = workflowManager.stopWorkflow(request);

        // Assert
        assertNotNull(response);
    }

    @Test
    public void testStopWorkflowSuccess_when_starting() {
        // Arrange
        StopWorkflowRequest request = StopWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus("STARTING");

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        StopWorkflowResponse response = workflowManager.stopWorkflow(request);

        // Assert
        assertNotNull(response);
    }


    @Test
    public void testStopWorkflowInvalidState() {
        // Arrange
        StopWorkflowRequest request = StopWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus("PAUSED");

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        Assertions.assertThrows(AWSServiceException.class, () ->workflowManager.stopWorkflow(request));

    }

    @Test
    public void testStopWorkflow_ConditionalCheckFailedException() {
        // Arrange
        StopWorkflowRequest request = StopWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.RUNNING));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);
        Mockito.doThrow(new ConditionalCheckFailedException("Conditional check failed"))
            .when(mockWorkflowRepository).updateWorkflow(any());

        // Act
        Assertions.assertThrows(AWSServiceException.class, () ->workflowManager.stopWorkflow(request));
    }

    @Test
    public void testDeleteWorkflowSuccess_when_Stopped() {
        // Arrange
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.STOPPED));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        DeleteWorkflowResponse response = workflowManager.deleteWorkflow(request);

        // Assert
        assertNotNull(response);
        assertEquals(WorkflowStatus.DELETED.toString(), mockWorkflowModel.getStatus());
    }

    @Test
    public void testDeleteWorkflowSuccess_when_WorkFlowStatusIsFailed() {
        // Arrange
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.FAILED));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        DeleteWorkflowResponse response = workflowManager.deleteWorkflow(request);

        // Assert
        assertNotNull(response);
        assertEquals(WorkflowStatus.DELETED.toString(), mockWorkflowModel.getStatus());
    }

    @Test void testDeleteWorkflow_DynamoDBException() {
        // Arrange
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.STOPPED));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);
        Mockito.doThrow(new AmazonDynamoDBException("DynamoDB exception"))
            .when(mockWorkflowRepository).updateWorkflow(any());

        // Act
        Assertions.assertThrows(AWSServiceException.class, () ->workflowManager.deleteWorkflow(request));
    }

    @Test
    public void testDeleteWorkflowInvalidState() {
        // Arrange
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.PAUSED));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        Assertions.assertThrows(AWSServiceException.class, () ->workflowManager.deleteWorkflow(request));
    }

    @Test
    public void testDeleteWorkflow_ConditionalCheckFailedException() {
        // Arrange
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.STOPPED));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);
        Mockito.doThrow(new ConditionalCheckFailedException("Conditional check failed"))
            .when(mockWorkflowRepository).updateWorkflow(any());

        // Act
        Assertions.assertThrows(InvalidInputException.class, () ->workflowManager.deleteWorkflow(request));
    }

    @Test
    public void testSendControlCommandSuccess() {
        // Arrange
        SendControlCommandRequest request = SendControlCommandRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .withNotificationID("testNotificationID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.WAITING));
        mockWorkflowModel.setTaskToken("task-token");

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);
        when(sfnClient.sendTaskSuccess(any(SendTaskSuccessRequest.class))).thenReturn(SendTaskSuccessResponse.builder().build());

        // Act
        SendControlCommandResponse response = workflowManager.sendControlCommand(request);

        // Assert
        assertNotNull(response);
    }

    @Test
    public void testSendControlCommandInvalidState() {
        // Arrange
        SendControlCommandRequest request = SendControlCommandRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .withNotificationID("testNotificationID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.PAUSING));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Act
        Assertions.assertThrows(AWSServiceException.class, () ->workflowManager.sendControlCommand(request));
    }

    @Test
    public void testSendControlCommand_ConditionalCheckFailedException() {
        // Arrange
        SendControlCommandRequest request = SendControlCommandRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .withNotificationID("testNotificationID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.WAITING));
        mockWorkflowModel.setTaskToken("task-token");

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);
        Mockito.doThrow(new ConditionalCheckFailedException("Conditional check failed"))
            .when(mockWorkflowRepository).updateWorkflow(any());

        // Act
        Assertions.assertThrows(InvalidInputException.class, () ->workflowManager.sendControlCommand(request));
    }

    /**
     * Tests that the sendControlCommand method throws an AWSServiceException when DynamoDB
     * throws an AmazonDynamoDBException.
     */
    @Test
    public void testSendControlCommand_DynamoDBException() {
        // Arrange
        SendControlCommandRequest request = SendControlCommandRequest.builder()
            .withWorkflowName("testWorkflowName")
            .withNamespaceID("testNamespaceID")
            .withNotificationID("testNotificationID")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflowName");
        mockWorkflowModel.setNamespaceID("testNamespaceID");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.WAITING));
        mockWorkflowModel.setTaskToken("task-token");

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);
        doThrow(new AmazonDynamoDBException("DynamoDB exception"))
            .when(mockWorkflowRepository).updateWorkflow(any(WorkFlowModel.class));

        // Act & Assert
        AWSServiceException exception = assertThrows(AWSServiceException.class,
            () -> workflowManager.sendControlCommand(request));
        assertEquals("DynamoDB exception (Service: null; Status Code: 0; Error Code: null; Request ID: null; Proxy: null)", exception.getMessage());
    }

    /**
     * Test case for startWorkflow for ReRunFailedExecution
     */
    @Test
    public void testStartWorkflow_RerunFailedExecution() {
        // Arrange
        StartWorkflowRequest request = StartWorkflowRequest.builder()
            .withWorkflowName("testWorkflow")
            .withNamespaceID("testNamespace")
            .build();

        WorkFlowModel mockWorkflowModel = new WorkFlowModel();
        mockWorkflowModel.setWorkflowName("testWorkflow");
        mockWorkflowModel.setNamespaceID("testNamespace");
        mockWorkflowModel.setStatus(String.valueOf(WorkflowStatus.STOPPED));

        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(mockWorkflowModel);

        // Set STAGE to a non-prod value
        System.setProperty("STAGE", "dev");

        // Act
        assertThrows(RuntimeException.class, () -> workflowManager.startWorkflow(request, WorkflowStateMachine.WORKFLOW_STATE_MACHINE));

        verify(mockWorkflowRepository, never()).updateWorkflow(any(WorkFlowModel.class));

        // Clean up
        System.clearProperty("STAGE");
    }
}
