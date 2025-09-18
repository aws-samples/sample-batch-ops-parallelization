package com.amazon.tdm.s3a.service.lambda;

import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.model.RuntimeConfig;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.requests.OrcaRequest;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import com.amazon.tdm.s3a.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.tdm.s3a.service.resources.s3.configuration.S3ConfigurationService;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowStatusManager;
import com.amazon.tdm.s3a.service.responses.OrcaResponse;
import com.amazon.tdm.s3a.service.responses.OrcaResponseBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ConfigureBucketLambdaTest{

    private S3ConfigureBucketLambda s3ConfigureBucketLambda;
    private WorkflowRepository workflowRepository;
    private Context context;
    private WorkFlowModel workflowModel;
    private OrcaRequest orcaRequest;
    private S3ClientFactory s3ClientFactory;
    private S3Client sourceS3Client;
    private S3Client destS3Client;
    private S3ConfigurationService s3ConfigurationService;
    private S3CreateBucketService s3CreateBucketService;
    private WorkflowStatusManager workflowStatusManager;

    private static final String TEST_SOURCE_ROLE = "arn:aws:iam::123456789012:role/source-role";
    private static final String TEST_DEST_ROLE = "arn:aws:iam::123456789012:role/dest-role";
    private static final String SOURCE_REGION = "us-west-2";
    private static final String DEST_REGION = "us-west-2";
    private static final String TEST_SOURCE_BUCKET = "arn:aws:s3:::source-bucket";
    private static final String TEST_DEST_BUCKET = "arn:aws:s3:::dest-bucket";
    private static final String TEST_WORKFLOW_NAME = "testWorkflow";
    private static final String TEST_NAMESPACE_ID = "testNamespace";

    @InjectMocks
    private S3ConfigureBucketLambda lambda;

    @BeforeEach
    void setUp() {
        System.setProperty("aws.region", "us-west-2");
        workflowRepository = mock(WorkflowRepository.class);
        context = mock(Context.class);
        s3ClientFactory = Mockito.mock(S3ClientFactory.class);
        sourceS3Client = Mockito.mock(S3Client.class);
        destS3Client= Mockito.mock(S3Client.class);
        s3ConfigurationService = mock(S3ConfigurationService.class);
        s3CreateBucketService = mock(S3CreateBucketService.class);
        workflowStatusManager = mock(WorkflowStatusManager.class);
        s3ConfigureBucketLambda = new S3ConfigureBucketLambda(workflowRepository, s3ClientFactory, s3ConfigurationService, s3CreateBucketService, workflowStatusManager);

        //Workflow Model and Orca
        workflowModel = new WorkFlowModel();
        workflowModel.setSourceBucketARN(TEST_SOURCE_BUCKET);
        workflowModel.setSourceRoleARN(TEST_SOURCE_ROLE);
        workflowModel.setSourceRegion(SOURCE_REGION);
        workflowModel.setDestBucketARN(TEST_DEST_BUCKET);
        workflowModel.setDestRoleARN(TEST_DEST_ROLE);
        workflowModel.setDestRegion(DEST_REGION);


        orcaRequest = new OrcaRequest();
        orcaRequest.setWorkflowName(TEST_WORKFLOW_NAME);
        orcaRequest.setNamespaceID(TEST_NAMESPACE_ID);
        when(workflowRepository.getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID)).thenReturn(workflowModel);

    }

    @Test
    void testDefaultConstructor() {
        lambda = new S3ConfigureBucketLambda();
        assertNotNull(lambda);
    }

    @Test
    void testHandleRequest_Success_ConfigureBucket() {

        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);


        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(true);

        doNothing().when(s3ConfigurationService).saveConfiguration(any(S3Client.class),
                any(S3Client.class), anyString(), anyString());
        doNothing().when(s3ConfigurationService).modifyBucketLifecycleRulesStatus(any(S3Client.class), any(String.class), any(ExpirationStatus.class));

        s3ConfigureBucketLambda.handleRequest(orcaRequest, context);
        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
        verify(s3ConfigurationService, times(1)).saveConfiguration(any(S3Client.class),
                any(S3Client.class), anyString(), anyString());
        verify(s3ConfigurationService, times(1)).modifyBucketLifecycleRulesStatus(eq(sourceS3Client), any(String.class), eq(ExpirationStatus.DISABLED));
        verify(s3ConfigurationService, times(1)).modifyBucketLifecycleRulesStatus(eq(destS3Client), any(String.class), eq(ExpirationStatus.DISABLED));
    }

    @Test
    void testHandleRequest_skipBucketOwnershipValidationAndCopyWhenTrue() {
        workflowModel.setRuntimeConfig(RuntimeConfig.builder()
                .skipBucketOwnershipValidationAndCopy(true)
                .build());
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);

        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(true);

        s3ConfigureBucketLambda.handleRequest(orcaRequest, context);

        verify(s3ConfigurationService, never()).configureBucketOwnershipControls(
                any(S3Client.class),
                any(S3Client.class),
                any(WorkFlowModel.class));
    }

    @Test
    void testHandleRequest_configureBucketOwnershipWhenSkipBucketOwnershipValidationAndCopyIsFalse() {
        workflowModel.setRuntimeConfig(RuntimeConfig.builder()
                .skipBucketOwnershipValidationAndCopy(false)
                .build());
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);

        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(true);

        s3ConfigureBucketLambda.handleRequest(orcaRequest, context);

        verify(s3ConfigurationService, times(1)).configureBucketOwnershipControls(
                any(S3Client.class),
                any(S3Client.class),
                any(WorkFlowModel.class));
    }

    @Test
    void testHandleRequest_configureBucketOwnershipWhenSkipBucketOwnershipValidationAndCopyIsNull() {
        workflowModel.setRuntimeConfig(RuntimeConfig.builder().build());
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);

        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(true);

        s3ConfigureBucketLambda.handleRequest(orcaRequest, context);

        verify(s3ConfigurationService, times(1)).configureBucketOwnershipControls(
                any(S3Client.class),
                any(S3Client.class),
                any(WorkFlowModel.class));
    }

    @Test
    void testHandleRequest_RuntimeConfigNull_ValidateAndCopyBucketOwnership() {
        workflowModel.setRuntimeConfig(null);
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);

        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(true);

        s3ConfigureBucketLambda.handleRequest(orcaRequest, context);

        verify(s3ConfigurationService, times(1)).configureBucketOwnershipControls(
                any(S3Client.class),
                any(S3Client.class),
                any(WorkFlowModel.class));
    }

    @Test
    void testHandleRequest_BucketNotExists() {


        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);

        s3ConfigureBucketLambda.handleRequest(orcaRequest, context);
        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);

    }

    @Test
    void testHandleRequest_InvalidInputException() {
        OrcaRequest orcaRequest = new OrcaRequest();
        orcaRequest.setWorkflowName(null); // Invalid input
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                s3ConfigureBucketLambda.handleRequest(orcaRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", thrown.getMessage());
    }

    @Test
    void testHandleRequest_S3Exception() {
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);
        when(s3CreateBucketService.checkBucketExists(any(), anyString()))
                .thenReturn(true);

        doThrow(S3Exception.builder().statusCode(500).message("S3 error").build())
                .when(s3ConfigurationService).saveConfiguration(any(S3Client.class), any(S3Client.class), anyString(), anyString());

        OrcaResponse response = s3ConfigureBucketLambda.handleRequest(orcaRequest, context);

        Assertions.assertEquals("FAILED", response.getStatus());
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowModel.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
    }

    @Test
    void testHandleRequest_AwsServiceException() {
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION))
                .thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION))
                .thenReturn(sourceS3Client);

        when(s3CreateBucketService.checkBucketExists(any(), anyString()))
                .thenReturn(true);

        AwsServiceException awsServiceException = AwsServiceException
                .builder()
                .statusCode(500)
                .message("S3 error")
                .build();

        doThrow(awsServiceException)
                .when(s3ConfigurationService)
                .configureServerAccessLogging(any(), any(), any(), any());

        OrcaResponse response = s3ConfigureBucketLambda.handleRequest(orcaRequest, context);

        assertNotNull(response);
        assertEquals("FAILED", response.getStatus());
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowModel.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
    }

    @Test
    void testHandleRequest_Exception() {
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);

        doThrow(S3Exception.builder().statusCode(500).message("S3 error").build())
                .when(s3ConfigurationService).saveConfiguration(any(S3Client.class), any(S3Client.class), anyString(), anyString());

        OrcaResponse response = s3ConfigureBucketLambda.handleRequest(orcaRequest, context);


        Assertions.assertEquals("FAILED", response.getStatus());
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowModel.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
    }

    @Test
    void testHandleRequest_RuntimeException() {
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenThrow(RuntimeException.class);

        OrcaResponse response = s3ConfigureBucketLambda.handleRequest(orcaRequest, context);

        assertEquals(String.valueOf(WorkflowStatus.FAILED), response.getStatus());
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowModel.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
    }

    @Test
    void testHandleRequest_NullNamespaceID() {
        // Create request with null namespaceID
        OrcaRequest orcaRequest = new OrcaRequest();
        orcaRequest.setWorkflowName(TEST_WORKFLOW_NAME);
        orcaRequest.setNamespaceID(null);

        // Assert that InvalidInputException is thrown with correct message
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                s3ConfigureBucketLambda.handleRequest(orcaRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.",
                thrown.getMessage());
    }

    @Test
    void testHandleRequest_EmptyNamespaceID() {
        // Create request with empty namespaceID
        OrcaRequest orcaRequest = new OrcaRequest();
        orcaRequest.setWorkflowName(TEST_WORKFLOW_NAME);
        orcaRequest.setNamespaceID("");

        // Assert that InvalidInputException is thrown with correct message
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                s3ConfigureBucketLambda.handleRequest(orcaRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.",
                thrown.getMessage());
    }

    @Test
    void testHandleRequest_StoppingWorkflow() {
        OrcaRequest orcaRequest = new OrcaRequest();
        orcaRequest.setWorkflowName("testWorkflow");
        orcaRequest.setNamespaceID("testNamespace");

        workflowModel.setStatus(WorkflowStatus.STOPPING.name());

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        when(workflowStatusManager.handleStoppingStatus(workflowModel)).thenReturn(
                OrcaResponseBuilder.buildSuccessResponse(workflowModel, WorkflowStatus.STOPPED));

        OrcaResponse response = assertDoesNotThrow(() -> s3ConfigureBucketLambda.handleRequest(orcaRequest, null));

        verify(workflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");
        verify(workflowStatusManager, times(1)).handleStoppingStatus(workflowModel);
        assertEquals(String.valueOf(WorkflowStatus.STOPPED), response.getStatus());
    }
}