package com.amazon.tdm.s3a.service.activity;

import com.amazon.tdm.s3a.model.AWSServiceException;
import com.amazon.tdm.s3a.model.AlreadyExistsException;
import com.amazon.tdm.s3a.model.CreateWorkflowRequest;
import com.amazon.tdm.s3a.model.CreateWorkflowResponse;
import com.amazon.tdm.s3a.model.DeleteWorkflowRequest;
import com.amazon.tdm.s3a.model.DeleteWorkflowResponse;
import com.amazon.tdm.s3a.model.EchoInput;
import com.amazon.tdm.s3a.model.GetWorkflowRequest;
import com.amazon.tdm.s3a.model.GetWorkflowResponse;
import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.model.S3AInternalServiceException;
import com.amazon.tdm.s3a.model.SendControlCommandRequest;
import com.amazon.tdm.s3a.model.SendControlCommandResponse;
import com.amazon.tdm.s3a.model.StartWorkflowRequest;
import com.amazon.tdm.s3a.model.StartWorkflowResponse;
import com.amazon.tdm.s3a.model.StopWorkflowRequest;
import com.amazon.tdm.s3a.model.StopWorkflowResponse;
import com.amazon.tdm.s3a.service.TestBase;
import com.amazon.tdm.s3a.model.Workflow;
import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.manager.WorkflowManager;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import com.amazon.tdm.s3a.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowStateMachine;
import com.amazon.tdm.s3a.service.validator.InputValidator;
import com.amazon.tdm.s3a.service.validator.S3RequestValidator;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.RequestedJobStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class WorkflowActivityTest extends TestBase {
    @Mock
    private WorkflowManager mockWorkflowManager;

    @Mock
    private WorkflowActivity mockWorkflowActivity;

    @Mock
    private  S3ClientFactory mockS3ClientFactory;

    @Mock
    private  S3RequestValidator mockS3RequestValidator;

    @Mock
    private WorkflowRepository mockWorkflowRepository;

    @Mock
    private S3ReplicationConfigurator mockS3ReplicationConfigurator;

    @Mock
    private WorkFlowModel workflowModel;

    @BeforeEach
    public void setup() {
        openMocks(this);
        workflowModel = WorkFlowModel.builder()
                .sourceRoleARN("source-role")
                .destRoleARN("dest-role")
                .sourceRegion("eu-west-1")
                .destRegion("eu-south-2")
                .sourceBucketARN("arn:aws:s3:::test-bucket")
                .destBucketARN("arn:aws:s3:::test-bucket2")
                .bopsJobIds(List.of("job-123", "job-456"))
                .build();
        mockWorkflowActivity = new WorkflowActivity(mockWorkflowManager, new InputValidator(),
                mockS3ClientFactory,mockS3RequestValidator, mockWorkflowRepository, mockS3ReplicationConfigurator);
    }



    @Test
    public void testCreateWorkflow_Success() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .withWorkflow(buildWorkflow())
                .build();

        CreateWorkflowResponse response = CreateWorkflowResponse.builder().build();
        when(mockWorkflowManager.createWorkflow(any())).thenReturn(response);

        //mock out S3 calls as this is not the main test we are doing
        S3Client s3Client = mock(S3Client.class);
        Mockito.when(mockS3ClientFactory.createS3Client(Mockito.anyString(), Mockito.anyString())).thenReturn(s3Client);


        S3ControlClient s3ControlClient = mock(S3ControlClient.class);
        Mockito.when(mockS3ClientFactory.createS3ControlClient(Mockito.anyString(), Mockito.anyString())).thenReturn(s3ControlClient);

        CreateWorkflowResponse expectedResponse = new CreateWorkflowResponse();
        Mockito.when(mockWorkflowManager.createWorkflow(Mockito.any(CreateWorkflowRequest.class))).thenReturn(expectedResponse);

        // Act
        CreateWorkflowResponse actualResponse = mockWorkflowActivity.createWorkflow(request);

        // Assert
        Mockito.verify(mockS3RequestValidator).validateMigrationRequest(Mockito.any(Workflow.class),
                Mockito.any(S3Client.class),
                Mockito.any(S3Client.class),
                Mockito.any(S3ControlClient.class));
        Mockito.verify(mockWorkflowManager).createWorkflow(Mockito.any(CreateWorkflowRequest.class));
        assertNotNull(actualResponse);
        Assertions.assertSame(expectedResponse, actualResponse);
    }


    @Test
    public void testCreateWorkflow_WithNullRequest() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.createWorkflow(null));
    }

    @Test
    public void testCreateWorkflow_WithCreateWorkflowException() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .withWorkflow(buildWorkflow())
                .build();

        when(mockWorkflowManager.createWorkflow(any())).thenThrow(new RuntimeException());

        assertThrows(RuntimeException.class, () -> mockWorkflowActivity.createWorkflow(request));
    }

    @Test
    public void testCreateWorkflow_ThrowsAWSServiceException() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .withWorkflow(buildWorkflow())
                .build();

        AWSServiceException simulatedException = new AWSServiceException("AWS service error simulated");
        when(mockWorkflowManager.createWorkflow(any(CreateWorkflowRequest.class)))
                .thenThrow(simulatedException);

        assertThrows(AWSServiceException.class, () -> mockWorkflowActivity.createWorkflow(request));
    }

    @Test
    public void testCreateWorkflow_AlreadyExistsExceptionHandling() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .withWorkflow(buildWorkflow())
                .build();

        AlreadyExistsException simulatedException = new AlreadyExistsException("Workflow already exists simulated");
        when(mockWorkflowManager.createWorkflow(any(CreateWorkflowRequest.class)))
                .thenThrow(simulatedException);

        assertThrows(AlreadyExistsException.class, () -> mockWorkflowActivity.createWorkflow(request));
    }


    @Test
    public void testEcho_WithNullRequest() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.echo(null));
    }

    @Test
    public void testEcho_WithNullString() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.echo(EchoInput.builder().withString(null).build()));
    }

    private Workflow buildWorkflow() {

        ImmutableMap<String, String> workflowConfig = ImmutableMap.<String, String>builder()
                .put("key1", "value1")
                .build();

        return Workflow.builder()
                .namespaceID(NAMESPACE_ID)
                .workflowName(WORKFLOW_NAME)
                .workflowType(WORKFLOW_TYPE)
                .status(STATUS)
                .sourceRoleARN(SOURCE_ROLE_ARN)
                .destRoleARN(DEST_ROLE_ARN)
                .workflowConfig(workflowConfig)
                .sourceAccountNumber(SOURCE_ACCOUNT_NUMBER)
                .sourceRegion(SOURCE_REGION)
                .destRegion(DEST_REGION)
                .bopsJobID(BOPS_JOB_ID)
                .build();
    }

    @Test
    public void testStopWorkflow_CancelsJobs_RemovesCrr() {
        S3Client s3Client = mock(S3Client.class);
        S3ControlClient s3ControlClient = mock(S3ControlClient.class);
        StopWorkflowRequest request = StopWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME).withNamespaceID(NAMESPACE_ID).build();

        when(mockS3ClientFactory.createS3Client(anyString(), anyString())).thenReturn(s3Client);
        when(mockS3ClientFactory.createS3ControlClient(anyString(), anyString())).thenReturn(s3ControlClient);
        when(mockWorkflowManager.stopWorkflow(any())).thenReturn(StopWorkflowResponse.builder().build());
        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowModel);
        doNothing().when(mockS3ReplicationConfigurator).batchUpdateJobStatus(any(S3ControlClient.class), any(WorkFlowModel.class), eq(RequestedJobStatus.CANCELLED));
        doNothing().when(mockS3ReplicationConfigurator).removeReplicationRule(any(S3Client.class), any(WorkFlowModel.class));

        StopWorkflowResponse stopWorkflowResponse = mockWorkflowActivity.stopWorkflow(request);

        assertNotNull(stopWorkflowResponse);
        verify(mockWorkflowManager, times(1)).stopWorkflow(request);
        verify(mockS3ReplicationConfigurator, times(1)).batchUpdateJobStatus(any(S3ControlClient.class), any(WorkFlowModel.class), eq(RequestedJobStatus.CANCELLED));
        verify(mockS3ReplicationConfigurator, times(1)).removeReplicationRule(any(S3Client.class), any(WorkFlowModel.class));
    }

    @Test
    public void testStopWorkflow_ThrowsRemoveCrrException() {
        workflowModel.setBopsJobIds(new ArrayList<>());
        S3Client s3Client = mock(S3Client.class);
        S3ControlClient s3ControlClient = mock(S3ControlClient.class);
        StopWorkflowRequest request = StopWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME).withNamespaceID(NAMESPACE_ID).build();

        when(mockS3ClientFactory.createS3Client(anyString(), anyString())).thenReturn(s3Client);
        when(mockS3ClientFactory.createS3ControlClient(anyString(), anyString())).thenReturn(s3ControlClient);
        when(mockWorkflowManager.stopWorkflow(any())).thenReturn(StopWorkflowResponse.builder().build());
        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowModel);
        doNothing().when(mockS3ReplicationConfigurator).batchUpdateJobStatus(any(S3ControlClient.class), any(WorkFlowModel.class), eq(RequestedJobStatus.CANCELLED));
        doThrow(new RuntimeException()).when(mockS3ReplicationConfigurator).removeReplicationRule(any(S3Client.class), any(WorkFlowModel.class));

        assertThrows(RuntimeException.class, () -> mockWorkflowActivity.stopWorkflow(request));

        verify(mockWorkflowManager, times(1)).stopWorkflow(request);
        verify(mockS3ReplicationConfigurator, times(1)).removeReplicationRule(any(S3Client.class), any(WorkFlowModel.class));
        verify(mockS3ReplicationConfigurator, times(1)).batchUpdateJobStatus(any(S3ControlClient.class), any(WorkFlowModel.class), eq(RequestedJobStatus.CANCELLED));
    }

    @Test
    public void testStopWorkflow_ThrowsExceptionOnUpdateJobStatus() {
        StopWorkflowRequest request = StopWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME).withNamespaceID(NAMESPACE_ID).build();

        S3Client s3Client = mock(S3Client.class);
        S3ControlClient s3ControlClient = mock(S3ControlClient.class);
        when(mockS3ClientFactory.createS3Client(anyString(), anyString())).thenReturn(s3Client);
        when(mockS3ClientFactory.createS3ControlClient(anyString(), anyString())).thenReturn(s3ControlClient);
        when(mockWorkflowManager.stopWorkflow(any())).thenReturn(StopWorkflowResponse.builder().build());
        when(mockWorkflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowModel);
        doThrow(new RuntimeException()).when(mockS3ReplicationConfigurator).batchUpdateJobStatus(any(S3ControlClient.class), any(WorkFlowModel.class), eq(RequestedJobStatus.CANCELLED));

        assertThrows(RuntimeException.class, () -> mockWorkflowActivity.stopWorkflow(request));

        verify(mockWorkflowManager, times(1)).stopWorkflow(request);
        verify(mockS3ReplicationConfigurator, times(1)).batchUpdateJobStatus(any(S3ControlClient.class), any(WorkFlowModel.class), eq(RequestedJobStatus.CANCELLED));
        verify(mockS3ReplicationConfigurator, never()).removeReplicationRule(any(S3Client.class), any(WorkFlowModel.class));
    }

    @Test
    public void testStopWorkflow_WithNullRequest() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.stopWorkflow(null));
    }

    @Test
    public void testStopWorkflow_WithNullNamespaceID() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.stopWorkflow(StopWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .build()));
    }

    @Test
    public void testStopWorkflow_WithNullWorkflowName() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.stopWorkflow(StopWorkflowRequest.builder()
                .withNamespaceID(NAMESPACE_ID)
                .build()));
    }

    @Test
    public void testStoptWorkflow_Exception() {
        StopWorkflowRequest request = StopWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID).build();

        when(mockWorkflowManager.stopWorkflow(any())).thenThrow(new InvalidInputException());

        assertThrows(Exception.class, () -> mockWorkflowActivity.stopWorkflow(request));
    }

    @Test
    public void testDeleteWorkflow_Success() {
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME).withNamespaceID(NAMESPACE_ID).build();

        DeleteWorkflowResponse response = DeleteWorkflowResponse.builder().build();

        when(mockWorkflowManager.deleteWorkflow(any())).thenReturn(response);

        DeleteWorkflowResponse deleteWorkflowResponse = mockWorkflowActivity.deleteWorkflow(request);

        verify(mockWorkflowManager, times(1)).deleteWorkflow(request);
        assertNotNull(deleteWorkflowResponse);
    }

    @Test
    public void testDeleteWorkflow_WithNullRequest() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.deleteWorkflow(null));
    }

    @Test
    public void testDeleteWorkflow_WithNullNamespaceID() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.deleteWorkflow(DeleteWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .build()));
    }

    @Test
    public void testDeleteWorkflow_WithNullWorkflowName() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.deleteWorkflow(DeleteWorkflowRequest.builder()
                .withNamespaceID(NAMESPACE_ID)
                .build()));
    }

    @Test
    public void testDeleteWorkflow_ThrowsAWSServiceException() {
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID).build();

        AWSServiceException simulatedException = new AWSServiceException("AWS service error simulated");
        when(mockWorkflowManager.deleteWorkflow(any(DeleteWorkflowRequest.class)))
                .thenThrow(simulatedException);

        assertThrows(AWSServiceException.class, () -> mockWorkflowActivity.deleteWorkflow(request));
    }

    @Test
    public void testDeleteWorkflow_AlreadyExistsExceptionHandling() {
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID).build();

        AlreadyExistsException simulatedException = new AlreadyExistsException("Workflow already exists simulated");
        when(mockWorkflowManager.deleteWorkflow(any(DeleteWorkflowRequest.class)))
                .thenThrow(simulatedException);

        assertThrows(AlreadyExistsException.class, () -> mockWorkflowActivity.deleteWorkflow(request));
    }

    @Test
    public void testDeleteWorkflow_UnexpectedErrorHandling() {
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID).build();

        String errorMessage = "Unexpected error";
        when(mockWorkflowManager.deleteWorkflow(any()))
                .thenThrow(new RuntimeException(errorMessage));

        // Act & Assert
        S3AInternalServiceException exception = assertThrows(
                S3AInternalServiceException.class,
                () -> mockWorkflowActivity.deleteWorkflow(request)
        );

        // Verify exception details
        assertEquals(errorMessage, exception.getMessage());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    public void testStartWorkflow_WithNullRequest() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.startWorkflow(null));
    }

    @Test
    public void testStartWorkflow_WithNullNamespaceID() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.startWorkflow(StartWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .build()));
    }

    @Test
    public void testDeleteWorkflow_Exception() {
        DeleteWorkflowRequest request = DeleteWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID).build();

        when(mockWorkflowManager.deleteWorkflow(any())).thenThrow(new InvalidInputException());

        assertThrows(Exception.class, () -> mockWorkflowActivity.deleteWorkflow(request));
    }


    @Test
    public void testStartWorkflow_WithNullWorkflowName() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.startWorkflow(StartWorkflowRequest.builder()
                .withNamespaceID(NAMESPACE_ID)
                .build()));
    }

    @Test
    public void testStartWorkflow_Success() {
        StartWorkflowRequest request = StartWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME).withNamespaceID(NAMESPACE_ID).build();

        StartWorkflowResponse response = StartWorkflowResponse.builder().build();

        when(mockWorkflowManager.startWorkflow(any(), any(WorkflowStateMachine.class))).thenReturn(response);

        StartWorkflowResponse startWorkflowResponse = mockWorkflowActivity.startWorkflow(request);

        verify(mockWorkflowManager, times(1)).startWorkflow(request, WorkflowStateMachine.WORKFLOW_STATE_MACHINE);
        assertNotNull(startWorkflowResponse);

    }

    @Test
    public void testStartWorkflow_Exception() {
        StartWorkflowRequest request = StartWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME).withNamespaceID(NAMESPACE_ID).build();

        when(mockWorkflowManager.startWorkflow(any(), any(WorkflowStateMachine.class))).thenThrow(new InvalidInputException());

        assertThrows(Exception.class, () -> mockWorkflowActivity.startWorkflow(request));
    }

    @Test
    @DisplayName("Should throw S3AInternalServiceException when unexpected RuntimeException occurs")
    public void testStartWorkflow_UnexpectedRuntimeException() {
        // Arrange
        StartWorkflowRequest request = StartWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID)
                .build();

        String errorMessage = "Unexpected error";
        when(mockWorkflowManager.startWorkflow(any(), any(WorkflowStateMachine.class)))
                .thenThrow(new RuntimeException(errorMessage));

        // Act & Assert
        S3AInternalServiceException exception = assertThrows(
                S3AInternalServiceException.class,
                () -> mockWorkflowActivity.startWorkflow(request)
        );

        // Verify exception details
        assertEquals(errorMessage, exception.getMessage());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    public void testGetWorkflow_Success() {
        GetWorkflowRequest request = GetWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID)
                .build();
        GetWorkflowResponse response = GetWorkflowResponse.builder().build();
        when(mockWorkflowManager.getWorkflow(any())).thenReturn(response);
        GetWorkflowResponse getWorkflowResponse = mockWorkflowActivity.getWorkflow(request);
        verify(mockWorkflowManager, times(1)).getWorkflow(request);
        assertNotNull(getWorkflowResponse);
    }
    @Test
    public void testGetWorkflow_WithNullRequest() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.getWorkflow(null));
    }

    @Test
    public void testGetWorkflow_Exception() {
        GetWorkflowRequest request = GetWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID).build();

        when(mockWorkflowManager.getWorkflow(any())).thenThrow(new InvalidInputException());

        assertThrows(Exception.class, () -> mockWorkflowActivity.getWorkflow(request));
    }

    @Test
    public void testGetWorkflow_WithNullNamespaceID() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.getWorkflow(GetWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .build()));
    }

    @Test
    public void testGetWorkflow_WithNullWorkflowName() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.getWorkflow(GetWorkflowRequest.builder()
                .withNamespaceID(NAMESPACE_ID)
                .build()));
    }

    @Test
    @DisplayName("Should throw S3AInternalServiceException when unexpected RuntimeException occurs")
    public void testgetWorkflow_UnexpectedRuntimeException() {
        // Arrange
        GetWorkflowRequest request = GetWorkflowRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID)
                .build();

        String errorMessage = "Unexpected error";
        when(mockWorkflowManager.getWorkflow(any()))
                .thenThrow(new RuntimeException(errorMessage));

        // Act & Assert
        S3AInternalServiceException exception = assertThrows(
                S3AInternalServiceException.class,
                () -> mockWorkflowActivity.getWorkflow(request)
        );

        // Verify exception details
        assertEquals(errorMessage, exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
    }

    @Test
    public void testSendControlCommand_WithNullWorkflowName() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.sendControlCommand(SendControlCommandRequest.builder()
                .withNamespaceID(NAMESPACE_ID)
                .withNotificationID(NOTIFICATION_ID)
                .build()));
    }

    @Test
    public void testSendControlCommand_WithNullNamespaceID() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.sendControlCommand(SendControlCommandRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNotificationID(NOTIFICATION_ID)
                .build()));
    }

    @Test
    public void testSendControlCommand_WithNullNotificationID() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.sendControlCommand(SendControlCommandRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID)
                .build()));
    }

    @Test
    public void testSendControlCommand_WithNullRequest() {
        assertThrows(InvalidInputException.class, () -> mockWorkflowActivity.sendControlCommand(null));
    }

    @Test
    public void testSendControlCommand_Success() {
        SendControlCommandRequest request = SendControlCommandRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID)
                .withNotificationID(NOTIFICATION_ID)
                .build();

        SendControlCommandResponse response = SendControlCommandResponse.builder().build();

        when(mockWorkflowManager.sendControlCommand(any())).thenReturn(response);

        SendControlCommandResponse sendControlCommandResponse = mockWorkflowActivity.sendControlCommand(request);

        verify(mockWorkflowManager, times(1)).sendControlCommand(request);
        assertNotNull(sendControlCommandResponse);
    }

    @Test
    public void testSendControlCommand_Exception() {
        SendControlCommandRequest request = SendControlCommandRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID)
                .withNotificationID(NOTIFICATION_ID)
                .build();

        when(mockWorkflowManager.sendControlCommand(any())).thenThrow(new InvalidInputException());

        assertThrows(Exception.class, () -> mockWorkflowActivity.sendControlCommand(request));
    }

    @Test
    @DisplayName("Should throw S3AInternalServiceException when unexpected RuntimeException occurs in sendControlCommand")
    public void testSendControlCommand_UnexpectedRuntimeException() {
        // Arrange
        SendControlCommandRequest request = SendControlCommandRequest.builder()
                .withWorkflowName(WORKFLOW_NAME)
                .withNamespaceID(NAMESPACE_ID)
                .withNotificationID(NOTIFICATION_ID)
                .build();

        RuntimeException runtimeException = new RuntimeException("Unexpected runtime error");
        when(mockWorkflowManager.sendControlCommand(any()))
                .thenThrow(runtimeException);

        // Act
        S3AInternalServiceException exception = assertThrows(
                S3AInternalServiceException.class,
                () -> mockWorkflowActivity.sendControlCommand(request)
        );

        // Verify the exception message matches the original runtime exception
        assertEquals("Unexpected runtime error", exception.getMessage());

        // Verify the cause is the original RuntimeException
        assertEquals(runtimeException, exception.getCause());
    }
}