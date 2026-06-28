package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.RuntimeConfig;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.CreateJobResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ReplicationSetupLambdaTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private S3ReplicationConfigurator replicationService;

    @Mock
    private S3ClientFactory s3ClientFactory;

    @Mock
    private WorkflowStatusManager workflowStatusManager;

    @Mock
    private S3Client mockSourceS3Client;

    @Mock
    private S3ControlClient mockS3ControlClient;

    @Mock
    private KmsClient mockDestKmsClient;

    @Mock
    S3Client mockDestS3Client;

    @InjectMocks
    private S3ReplicationSetupLambda lambda;

    private WorkflowRequest workflowRequest;

    private static final String BOPS_ROLE_NAME = "s3a-bops-permissions";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");
    }

    @Test
    void testDefaultConstructor() {
        lambda = new S3ReplicationSetupLambda();
        Assertions.assertNotNull(lambda);
    }


    @Test
    void testHandleRequest_InvalidInput_ThrowsInvalidInputException_NullWorkflowName() {
        // Given: WorkflowRequest with null workflow name
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName(null);
        workflowRequest.setNamespaceID("validNamespace");

        // When & Then: Expect InvalidInputException to be thrown
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                lambda.handleRequest(workflowRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", thrown.getMessage());
    }

    @Test
    void testHandleRequest_InvalidInput_ThrowsInvalidInputException_NullNamespaceID() {
        // Given: WorkflowRequest with null namespace ID
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("validWorkflow");
        workflowRequest.setNamespaceID(null);

        // When & Then: Expect InvalidInputException to be thrown
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                lambda.handleRequest(workflowRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", thrown.getMessage());
    }

    @Test
    void testHandleRequest_InvalidInput_ThrowsInvalidInputException_EmptyValues() {
        // Given: WorkflowRequest with empty workflow name and namespace ID
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("");
        workflowRequest.setNamespaceID("");

        // When & Then: Expect InvalidInputException to be thrown
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                lambda.handleRequest(workflowRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", thrown.getMessage());
    }

    // Optionally, you can also test both values being null or empty
    @Test
    void testHandleRequest_InvalidInput_ThrowsInvalidInputException_NullValues() {
        // Given: WorkflowRequest with null values
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName(null);
        workflowRequest.setNamespaceID(null);

        // When & Then: Expect InvalidInputException to be thrown
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                lambda.handleRequest(workflowRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", thrown.getMessage());
    }

    @Test
    void testHandleRequest_CrossAccount_Success() {
        // Given: Mocking valid workflow details and S3Clients
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);

        // Mock source S3Client
        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenReturn(mockSourceS3Client);

        // Mock destination S3Client
        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getDestRoleARN()),
                eq(workflowDetails.getDestRegion())
        )).thenReturn(mockDestS3Client);

        when(s3ClientFactory.createS3ControlClient(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenReturn(mockS3ControlClient);

        when(s3ClientFactory.createKmsClient(
                eq(workflowDetails.getDestRoleARN()),
                eq(workflowDetails.getDestRegion())
        )).thenReturn(mockDestKmsClient);

        // When: Mock all required method calls
        PutBucketVersioningResponse mockVersioningResponse = PutBucketVersioningResponse.builder().build();
        when(replicationService.enableBucketVersioning(any(WorkFlowModel.class), any(S3Client.class)))
                .thenReturn(mockVersioningResponse);

        doNothing().when(replicationService).setupLiveReplication(
                any(WorkFlowModel.class),
                any(S3Client.class),
                any(S3Client.class),
                any(String.class));

        CreateJobResponse mockJobResponse = CreateJobResponse.builder().build();
        when(replicationService.setupBOPSJob(
                any(WorkFlowModel.class),
                any(S3ControlClient.class),
                any(WorkflowRepository.class),
                any(String.class)))
                .thenReturn(mockJobResponse);

                // Mock S3 encryption response chain
        GetBucketEncryptionResponse encryptionResponse = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(ServerSideEncryptionRule.builder()
                                        .applyServerSideEncryptionByDefault(
                                                ServerSideEncryptionByDefault.builder()
                                                        .sseAlgorithm("aws:kms")
                                                        .kmsMasterKeyID("arn:aws:kms:region:account:key/test-key-id")
                                                        .build())
                                        .build())
                                .build())
                .build();

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(encryptionResponse);

        // Then: Execute the lambda handler
        WorkflowResponse result = lambda.handleRequest(workflowRequest, null);
        assertEquals("FINISHED", result.getStatus());

        // Verify all interactions
        verify(workflowRepository, times(1))
                .getWorkflow(anyString(), anyString());
        verify(replicationService, times(1))
                .enableBucketVersioning(any(WorkFlowModel.class), eq(mockSourceS3Client));
        verify(replicationService, times(1))
                .setupLiveReplication(any(WorkFlowModel.class),
                eq(mockSourceS3Client),
                eq(mockDestS3Client),
                any(String.class));
        verify(replicationService, times(1))
                .setupBOPSJob(any(WorkFlowModel.class), eq(mockS3ControlClient),
                eq(workflowRepository), any(String.class));
    }


    @Test
    void testHandleRequest_SameAccount_Success() {
        // Given: Mocking valid workflow details and S3Clients
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        //same account number for source and destination
        workflowDetails.setSourceAccountNumber("0123456789");
        workflowDetails.setDestAccountNumber("0123456789");
        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);

        // Mock source S3Client
        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenReturn(mockSourceS3Client);

        // Mock destination S3Client
        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getDestRoleARN()),
                eq(workflowDetails.getDestRegion())
        )).thenReturn(mockDestS3Client);

        when(s3ClientFactory.createS3ControlClient(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenReturn(mockS3ControlClient);

        when(s3ClientFactory.createKmsClient(
                eq(workflowDetails.getDestRoleARN()),
                eq(workflowDetails.getDestRegion())
        )).thenReturn(mockDestKmsClient);

        // When: Mock all required method calls
        PutBucketVersioningResponse mockVersioningResponse = PutBucketVersioningResponse.builder().build();
        when(replicationService.enableBucketVersioning(any(WorkFlowModel.class), any(S3Client.class)))
                .thenReturn(mockVersioningResponse);

        doNothing().when(replicationService).setupLiveReplication(
                any(WorkFlowModel.class),
                any(S3Client.class),
                any(S3Client.class),
                any(String.class));

        CreateJobResponse mockJobResponse = CreateJobResponse.builder().build();
        when(replicationService.setupBOPSJob(
                any(WorkFlowModel.class),
                any(S3ControlClient.class),
                any(WorkflowRepository.class),
                any(String.class)))
                .thenReturn(mockJobResponse);

        // Mock S3 encryption response chain
        GetBucketEncryptionResponse encryptionResponse = GetBucketEncryptionResponse.builder()
                .serverSideEncryptionConfiguration(
                        ServerSideEncryptionConfiguration.builder()
                                .rules(ServerSideEncryptionRule.builder()
                                        .applyServerSideEncryptionByDefault(
                                                ServerSideEncryptionByDefault.builder()
                                                        .sseAlgorithm("aws:kms")
                                                        .kmsMasterKeyID("arn:aws:kms:region:account:key/test-key-id")
                                                        .build())
                                        .build())
                                .build())
                .build();

        when(mockDestS3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenReturn(encryptionResponse);

        // Then: Execute the lambda handler
        WorkflowResponse result = lambda.handleRequest(workflowRequest, null);
        assertEquals("FINISHED", result.getStatus());

        // Verify all interactions
        verify(workflowRepository, times(1))
                .getWorkflow(anyString(), anyString());
        verify(replicationService, times(1))
                .enableBucketVersioning(any(WorkFlowModel.class), eq(mockSourceS3Client));
        verify(replicationService, times(1))
                .setupLiveReplication(any(WorkFlowModel.class),
                        eq(mockSourceS3Client),
                        eq(mockDestS3Client),
                        any(String.class));
        verify(replicationService, times(1))
                .setupBOPSJob(any(WorkFlowModel.class), eq(mockS3ControlClient),
                        eq(workflowRepository), any(String.class));
    }

    @Test
    void testHandleRequest_RuntimeExceptionDuringReplication() {
        // Given: Mocking valid workflow details
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);

        // Mock source S3Client
        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenReturn(mockSourceS3Client);

        // Mock destination S3Client
        S3Client mockDestS3Client = mock(S3Client.class);
        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getDestRoleARN()),
                eq(workflowDetails.getDestRegion())
        )).thenReturn(mockDestS3Client);

        // Mock S3ControlClient
        S3ControlClient mockS3ControlClient = mock(S3ControlClient.class);
        when(s3ClientFactory.createS3ControlClient(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenReturn(mockS3ControlClient);

        // Mock successful bucket versioning
        PutBucketVersioningResponse mockVersioningResponse = PutBucketVersioningResponse.builder().build();
        when(replicationService.enableBucketVersioning(any(WorkFlowModel.class), any(S3Client.class)))
                .thenReturn(mockVersioningResponse);

        // When: Mock replicationService to throw a RuntimeException during setupLiveReplication
        doThrow(new RuntimeException("Replication error"))
                .when(replicationService)
                .setupLiveReplication(any(WorkFlowModel.class), any(S3Client.class), any(S3Client.class), any(String.class));

        // Then: Execute the lambda handler and check for failure response
        WorkflowResponse result = lambda.handleRequest(workflowRequest, null);
        assertEquals("FAILED", result.getStatus());

        // Verify interactions
        verify(workflowRepository, times(1)).getWorkflow(anyString(), anyString());
        verify(replicationService, times(1)).enableBucketVersioning(any(WorkFlowModel.class), eq(mockSourceS3Client));
        verify(replicationService, times(1)).setupLiveReplication(
                any(WorkFlowModel.class),
                eq(mockSourceS3Client),
                eq(mockDestS3Client),
                any(String.class)
        );
        // Verify that setupBOPSJob was never called due to the exception
        verify(replicationService, never()).setupBOPSJob(any(), any(), any(), any());
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowDetails.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowDetails);
    }

    @Test
    void testHandleRequest_AwsServiceException() {
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);

        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenThrow(AwsServiceException.class);

        WorkflowResponse result = lambda.handleRequest(workflowRequest, null);

        assertEquals(String.valueOf(WorkflowStatus.FAILED), result.getStatus());
        verify(workflowRepository, times(1))
                .getWorkflow(anyString(), anyString());
        verify(replicationService, never()).enableBucketVersioning(any(WorkFlowModel.class), eq(mockSourceS3Client));
        verify(replicationService, never()).setupLiveReplication(
                any(WorkFlowModel.class),
                any(S3Client.class),
                any(S3Client.class), any(String.class)
        );
        verify(replicationService, never()).setupBOPSJob(any(), any(), any(), any());
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowDetails.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowDetails);
    }

    @Test
    void testHandleRequest_StoppingWorkflow() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");

        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        workflowDetails.setStatus(WorkflowStatus.STOPPING.name());

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowDetails);
        when(workflowStatusManager.handleStoppingStatus(workflowDetails)).thenReturn(
                WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.STOPPED));

        WorkflowResponse response = assertDoesNotThrow(() -> lambda.handleRequest(workflowRequest, null));

        verify(workflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");
        verify(workflowStatusManager, times(1)).handleStoppingStatus(workflowDetails);
        assertEquals(String.valueOf(WorkflowStatus.STOPPED), response.getStatus());
    }

    // Helper method to create a valid workflow model
    private WorkFlowModel createValidWorkFlowModel() {
        WorkFlowModel workflow = new WorkFlowModel();
        workflow.setSourceRoleARN("arn:aws:iam::123456789012:role/source-role");
        workflow.setSourceRegion("us-east-1");
        workflow.setSourceBucketARN("arn:aws:s3:::source-bucket");
        workflow.setDestBucketARN("arn:aws:s3:::dest-bucket");
        workflow.setSourceAccountNumber("1234567890");
        workflow.setDestAccountNumber("0123456789");
        workflow.setDestRegion("us-west-2");
        workflow.setSourceRegion("us-west-2");

        return workflow;
    }
}
