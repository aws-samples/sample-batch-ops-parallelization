package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.s3.bucket.S3CreateBucketImpl;
import com.amazon.bopspar.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3CreateBucketLambdaTest {

    private S3CreateBucketLambda s3CreateBucketLambda;
    private WorkflowRepository workflowRepository;
    private Context context;
    private S3CreateBucketService s3CreateBucketService;
    private S3CreateBucketImpl s3CreateBucketImpl;
    private S3ClientFactory s3ClientFactory;
    private WorkflowStatusManager workflowStatusManager;
    private S3Client sourceS3Client;
    private S3Client destS3Client;
    private static final String TEST_BUCKET_NAME = "testBucketName";

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        context = mock(Context.class);
        s3CreateBucketService = mock(S3CreateBucketService.class);
        s3ClientFactory = mock(S3ClientFactory.class);
        sourceS3Client = mock(S3Client.class);
        destS3Client= mock(S3Client.class);
        s3CreateBucketImpl = mock(S3CreateBucketImpl.class);
        workflowStatusManager = mock(WorkflowStatusManager.class);

        s3CreateBucketLambda = new S3CreateBucketLambda(workflowRepository, s3CreateBucketService, s3ClientFactory, workflowStatusManager);
        when(s3CreateBucketService.generateBucketName(anyString())).thenReturn(TEST_BUCKET_NAME);
    }

    @Test
    void testDefaultConstructor() {
        s3CreateBucketLambda = new S3CreateBucketLambda();

        assertNotNull(s3CreateBucketLambda);
    }


    @Test
    void testHandleRequest_Success_CreateBucket_SameAccount() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");


        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setDestBucketARN(null); // No existing bucket ARN
        workflowModel.setSourceBucketARN("arn:aws:s3:::existing-bucket-source");
        workflowModel.setDestRoleARN("testRoleArn");
        workflowModel.setSourceRoleARN("sourceRoleArn");
        workflowModel.setDestRegion("us-west-2");
        workflowModel.setSourceRegion("us-west-2");
        workflowModel.setSourceAccountNumber("1234567891");
        workflowModel.setDestAccountNumber("1234567891");


        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client("testRoleArn", "us-west-2")).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client("sourceRoleArn", "us-west-2")).thenReturn(sourceS3Client);

        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(false);



        s3CreateBucketLambda.handleRequest(workflowRequest, context);

        verify(workflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);

    }
    @Test
    void testHandleRequest_Success_CreateBucket_CrossAccount() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");


        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setDestBucketARN(null); // No existing bucket ARN
        workflowModel.setSourceBucketARN("arn:aws:s3:::existing-bucket-source");
        workflowModel.setDestRoleARN("testRoleArn");
        workflowModel.setSourceRoleARN("sourceRoleArn");
        workflowModel.setDestRegion("us-west-2");
        workflowModel.setSourceRegion("us-west-2");
        workflowModel.setSourceAccountNumber("1234567891");
        workflowModel.setDestAccountNumber("1234567892");


        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client("testRoleArn", "us-west-2")).thenReturn(destS3Client);
        when(s3ClientFactory.createS3Client("sourceRoleArn", "us-west-2")).thenReturn(sourceS3Client);

        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(false);



        s3CreateBucketLambda.handleRequest(workflowRequest, context);

        verify(workflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);

    }

    @Test
    void testHandleRequest_BucketAlreadyExists() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");

        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setDestBucketARN("arn:aws:s3:::existing-bucket");
        workflowModel.setSourceBucketARN("arn:aws:s3:::existing-bucket-source");
        workflowModel.setDestRoleARN("testRoleArn");
        workflowModel.setSourceRoleARN("sourceRoleArn");
        workflowModel.setDestRegion("us-west-2");
        workflowModel.setSourceRegion("us-west-2");


        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client(anyString(), anyString())).thenReturn(destS3Client);
        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), eq("existing-bucket"))).thenReturn(true);

        WorkflowResponse response = s3CreateBucketLambda.handleRequest(workflowRequest, context);

        // Assert that the response indicates the bucket already exists
        assertEquals("CREATED", response.getStatus());
        verify(workflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");

    }

    @Test
    void testHandleRequest_InvalidInput() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName(null); // Invalid input
        InvalidInputException thrown = assertThrows(InvalidInputException.class, () ->
                s3CreateBucketLambda.handleRequest(workflowRequest, null));

        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", thrown.getMessage());
    }

    @Test
    void testHandleRequest_createBucketS3Exception() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");

        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setDestBucketARN(null);
        workflowModel.setDestRoleARN("testRoleArn");
        workflowModel.setDestRegion("us-west-2");
        workflowModel.setSourceBucketARN("arn:aws:s3:::existing-bucket-source");

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client(anyString(), anyString())).thenReturn(destS3Client);
        when(s3CreateBucketService.checkBucketExists(any(S3Client.class), anyString())).thenReturn(false);
        doThrow(S3Exception.builder().statusCode(500).message("S3 error").build())
                .when(s3CreateBucketService).createS3Bucket(any(S3Client.class), anyString(), anyString(),
                        anyString());

        WorkflowResponse response = s3CreateBucketLambda.handleRequest(workflowRequest, context);

        // Assert that the response indicates the bucket already exists
        assertEquals("FAILED", response.getStatus());
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowModel.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
    }

    @Test
    void testHandleRequest_RuntimeException() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");

        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setDestBucketARN(null); // No existing bucket ARN
        workflowModel.setSourceBucketARN("arn:aws:s3:::existing-bucket-source");
        workflowModel.setDestRoleARN("testRoleArn");
        workflowModel.setSourceRoleARN("sourceRoleArn");
        workflowModel.setDestRegion("us-west-2");
        workflowModel.setSourceRegion("us-west-2");
        workflowModel.setSourceAccountNumber("1234567891");
        workflowModel.setDestAccountNumber("1234567891");

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client("sourceRoleArn", "us-west-2")).thenThrow(RuntimeException.class);

        WorkflowResponse response = s3CreateBucketLambda.handleRequest(workflowRequest, context);

        assertEquals(String.valueOf(WorkflowStatus.FAILED), response.getStatus());
        verify(workflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
        assertEquals(String.valueOf(WorkflowStatus.FAILED), workflowModel.getStatus());
        verify(workflowRepository, times(1)).updateWorkflow(workflowModel);
    }

    @Test
    void testHandleRequest_StoppingWorkflow() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");

        WorkFlowModel workflowDetails = new WorkFlowModel();
        workflowDetails.setStatus(WorkflowStatus.STOPPING.name());

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowDetails);
        when(workflowStatusManager.handleStoppingStatus(workflowDetails)).thenReturn(
                WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.STOPPED));

        WorkflowResponse response = assertDoesNotThrow(() -> s3CreateBucketLambda.handleRequest(workflowRequest, null));

        verify(workflowRepository, times(1)).getWorkflow("testWorkflow", "testNamespace");
        verify(workflowStatusManager, times(1)).handleStoppingStatus(workflowDetails);
        assertEquals(String.valueOf(WorkflowStatus.STOPPED), response.getStatus());
    }
}
