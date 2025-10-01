package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.service.TestBase;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.requests.OrcaRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.bopspar.service.resources.replication.S3ReplicationUtils;
import com.amazon.bopspar.service.responses.OrcaResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3RollbackReplicationSetupLambdaTest extends TestBase{

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private S3ReplicationConfigurator replicationService;

    @Mock
    private S3ClientFactory s3ClientFactory;

    @Mock
    private S3Client mockSourceS3Client;

    @Mock
    private S3Client mockDestS3Client;

    @Mock
    private KmsClient mockDestKmsClient;

    @InjectMocks
    private S3RollbackReplicationSetupLambda lambda;

    private OrcaRequest orcaRequest;
    private static final String BIDIRECTIONAL_FLAG = "bidirectional";
    private static final String SUCCESS_STATUS = String.valueOf(JobStatus.FINISHED);
    private static final String FAILURE_STATUS = String.valueOf(JobStatus.FAILED);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orcaRequest = new OrcaRequest();
        orcaRequest.setWorkflowName("testWorkflow");
        orcaRequest.setNamespaceID("testNamespace");
    }

    @Test
    void testDefaultConstructor() {
        lambda = new S3RollbackReplicationSetupLambda();
        Assertions.assertNotNull(lambda);
    }

    @Test
    void testHandleRequest_InvalidInput_ThrowsInvalidInputException_NullWorkflowName() {
        orcaRequest.setWorkflowName(null);
        orcaRequest.setNamespaceID("validNamespace");

        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                lambda.handleRequest(orcaRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", thrown.getMessage());
    }

    @Test
    void testHandleRequest_Success_BidirectionalSetup() {
        // Setup workflow with bidirectional flag
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        Map<String, String> workflowConfig = new HashMap<>();
        workflowConfig.put(BIDIRECTIONAL_FLAG, "true");
        workflowDetails.setWorkflowConfig(workflowConfig);

        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);
        setupMockClients(workflowDetails);
        setupSuccessfulReplicationScenario();

        OrcaResponse result = lambda.handleRequest(orcaRequest, null);

        assertEquals(SUCCESS_STATUS, result.getStatus());
        // Get the swapped workflow that will be used in the lambda
        WorkFlowModel swappedWorkflow = S3ReplicationUtils.getUpdatedWorkflow(workflowDetails);
        verifyRollbackInteractions(swappedWorkflow);
    }

    @Test
    void testHandleRequest_Success_RollbackScenario() {
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);
        setupMockClients(workflowDetails);
        setupSuccessfulReplicationScenario();

        OrcaResponse result = lambda.handleRequest(orcaRequest, null);

        assertEquals(SUCCESS_STATUS, result.getStatus());
        // Get the swapped workflow that will be used in the lambda
        WorkFlowModel swappedWorkflow = S3ReplicationUtils.getUpdatedWorkflow(workflowDetails);
        verifyRollbackInteractions(swappedWorkflow);
    }

    @Test
    void testHandleRequest_CrossAccount_Success() {
        // Arrange
        WorkFlowModel originalWorkflow = createValidWorkFlowModel();
        originalWorkflow.setSourceAccountNumber("123");
        originalWorkflow.setDestAccountNumber("456");

        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(originalWorkflow);
        setupMockClients(originalWorkflow);
        setupSuccessfulReplicationScenario();

        when(s3ClientFactory.createKmsClient(
                eq(originalWorkflow.getDestRoleARN()),
                eq(originalWorkflow.getDestRegion())
        )).thenReturn(mockDestKmsClient);

        // Act
        OrcaResponse result = lambda.handleRequest(orcaRequest, null);

        // Assert
        assertEquals(SUCCESS_STATUS, result.getStatus());

        // Get the swapped workflow that will be used in the lambda
        WorkFlowModel swappedWorkflow = S3ReplicationUtils.getUpdatedWorkflow(originalWorkflow);

        // Verify all expected method calls in order using inOrder
        InOrder inOrder = inOrder(replicationService);

        // Verify cross-account policy
        inOrder.verify(replicationService).addCrossAccountBucketPolicy(
                eq(swappedWorkflow),
                eq(mockSourceS3Client),
                anyString()
        );

        // Verify replication setup
        inOrder.verify(replicationService).setupLiveReplication(
                eq(swappedWorkflow),
                eq(mockDestS3Client),
                eq(mockSourceS3Client),
                contains("s3a-bops-permissions")
        );

        // Verify KMS policy update
        inOrder.verify(replicationService).updateDestKeyPolicyIfBucketIsKmsEncrypted(
                eq(swappedWorkflow),
                eq(mockSourceS3Client),
                isNull() // Explicitly verify that null is passed
        );
    }


    @Test
    void testHandleRequest_SameAccount_Success() {
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        workflowDetails.setSourceAccountNumber("123");
        workflowDetails.setDestAccountNumber("123");

        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);
        setupMockClients(workflowDetails);
        setupSuccessfulReplicationScenario();

        OrcaResponse result = lambda.handleRequest(orcaRequest, null);

        assertEquals(SUCCESS_STATUS, result.getStatus());
        verify(replicationService, never()).updateDestKeyPolicyIfBucketIsKmsEncrypted(
                any(), any(), any()
        );
    }

    @Test
    void testHandleRequest_AwsServiceException() {
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);

        when(s3ClientFactory.createS3Client(anyString(), anyString()))
                .thenThrow(AwsServiceException.builder().build());

        OrcaResponse result = lambda.handleRequest(orcaRequest, null);

        assertEquals(FAILURE_STATUS, result.getStatus());
        verify(workflowRepository).updateWorkflow(argThat(wf ->
                wf.getStatus().equals(String.valueOf(WorkflowStatus.FAILED))));
    }

    @Test
    void testHandleRequest_RuntimeException() {
        WorkFlowModel workflowDetails = createValidWorkFlowModel();
        when(workflowRepository.getWorkflow(anyString(), anyString())).thenReturn(workflowDetails);
        setupMockClients(workflowDetails);

        doThrow(new RuntimeException("Test exception"))
                .when(replicationService)
                .setupLiveReplication(any(), any(), any(), any());

        OrcaResponse result = lambda.handleRequest(orcaRequest, null);

        assertEquals(FAILURE_STATUS, result.getStatus());
        verify(workflowRepository).updateWorkflow(argThat(wf ->
                wf.getStatus().equals(String.valueOf(WorkflowStatus.FAILED))));
    }

    private void setupMockClients(WorkFlowModel workflowDetails) {
        // Now using the swapped workflow's source/dest details directly
        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getSourceRoleARN()),
                eq(workflowDetails.getSourceRegion())
        )).thenReturn(mockSourceS3Client);

        when(s3ClientFactory.createS3Client(
                eq(workflowDetails.getDestRoleARN()),
                eq(workflowDetails.getDestRegion())
        )).thenReturn(mockDestS3Client);

        // KMS client setup if needed
        when(s3ClientFactory.createKmsClient(
                eq(workflowDetails.getDestRoleARN()),
                eq(workflowDetails.getDestRegion())
        )).thenReturn(mockDestKmsClient);
    }

    private void setupSuccessfulReplicationScenario() {
        doNothing().when(replicationService).setupLiveReplication(
                any(WorkFlowModel.class),
                any(S3Client.class),
                any(S3Client.class),
                any(String.class));

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
    }

    private void verifyCommonInteractions(WorkFlowModel workflowDetails) {
        verify(workflowRepository).getWorkflow(anyString(), anyString());
        verify(replicationService).setupLiveReplication(
                eq(workflowDetails),
                eq(mockSourceS3Client),
                eq(mockDestS3Client),
                any(String.class)
        );
    }

    private void verifyRollbackInteractions(WorkFlowModel swappedWorkflow) {
        verify(replicationService).addCrossAccountBucketPolicy(
                eq(swappedWorkflow),
                eq(mockSourceS3Client),
                anyString()
        );

        verify(replicationService).setupLiveReplication(
                eq(swappedWorkflow),
                eq(mockDestS3Client),
                eq(mockSourceS3Client),
                contains("s3a-bops-permissions")
        );

        // Only verify KMS policy update if it's a cross-account scenario
        // Verify KMS policy update
        verify(replicationService).updateDestKeyPolicyIfBucketIsKmsEncrypted(
                eq(swappedWorkflow),
                eq(mockSourceS3Client),
                any() // Accept any KMS client, including null
        );
    }


    private WorkFlowModel createValidWorkFlowModel() {
        WorkFlowModel workflow = new WorkFlowModel();
        workflow.setSourceRoleARN("arn:aws:iam::123456789012:role/source-role");
        workflow.setSourceRegion("us-east-1");
        workflow.setSourceBucketARN("arn:aws:s3:::source-bucket");
        workflow.setDestBucketARN("arn:aws:s3:::dest-bucket");
        workflow.setSourceAccountNumber("1234567890");
        workflow.setDestAccountNumber("0123456789");
        workflow.setDestRegion("us-west-2");
        return workflow;
    }
}

