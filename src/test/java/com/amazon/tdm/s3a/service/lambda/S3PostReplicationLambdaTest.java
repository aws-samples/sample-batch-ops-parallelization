package com.amazon.tdm.s3a.service.lambda;

import com.amazon.tdm.s3a.model.EntityNotFoundException;
import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.requests.OrcaRequest;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import com.amazon.tdm.s3a.service.resources.replication.S3PostReplicationService;
import com.amazon.tdm.s3a.service.responses.OrcaResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3PostReplicationLambdaTest{
    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private Context context;
    @Mock
    private WorkFlowModel workflowModel;
    @Mock
    private OrcaRequest orcaRequest;
    @Mock
    private S3ClientFactory s3ClientFactory;
    @Mock
    private S3Client sourceS3Client;
    @Mock
    private S3Client destS3Client;
    @Mock
    private KmsClient destKmsClient;
    @Mock
    private S3PostReplicationService s3PostReplicationService;

    @InjectMocks
    private S3PostReplicationLambda lambda;

    private static final String TEST_SOURCE_ROLE = "arn:aws:iam::123456789012:role/source-role";
    private static final String TEST_DEST_ROLE = "arn:aws:iam::123456789012:role/dest-role";
    private static final String SOURCE_REGION = "us-west-2";
    private static final String DEST_REGION = "us-west-2";
    private static final String TEST_SOURCE_BUCKET = "arn:aws:s3:::source-bucket";
    private static final String TEST_DEST_BUCKET = "arn:aws:s3:::dest-bucket";
    private static final String TEST_WORKFLOW_NAME = "testWorkflow";
    private static final String TEST_NAMESPACE_ID = "testNamespace";
    private static final String TEST_SOURCE_ACCOUNT = "123456789123";
    private static final String TEST_DEST_ACCOUNT = "999123456789";

    @BeforeEach
    void setUp(){
        GetBucketLifecycleConfigurationResponse lifecycleConfiguration = GetBucketLifecycleConfigurationResponse.builder()
                .rules(Collections.singletonList(LifecycleRule.builder().build()))
                .build();
        Map<String, String> sourceConfig = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            sourceConfig.put("bucketLifecycle", mapper.writeValueAsString(lifecycleConfiguration.toBuilder()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to setup test data", e);
        }

        workflowModel = new WorkFlowModel();
        workflowModel.setWorkflowName(TEST_WORKFLOW_NAME);
        workflowModel.setNamespaceID(TEST_NAMESPACE_ID);
        workflowModel.setSourceBucketARN(TEST_SOURCE_BUCKET);
        workflowModel.setSourceRoleARN(TEST_SOURCE_ROLE);
        workflowModel.setSourceRegion(SOURCE_REGION);
        workflowModel.setDestBucketARN(TEST_DEST_BUCKET);
        workflowModel.setDestRoleARN(TEST_DEST_ROLE);
        workflowModel.setDestRegion(DEST_REGION);
        workflowModel.setSourceBucketConfig(sourceConfig);
        workflowModel.setSourceAccountNumber(TEST_SOURCE_ACCOUNT);
        workflowModel.setDestAccountNumber(TEST_SOURCE_ACCOUNT);

        orcaRequest = new OrcaRequest();
        orcaRequest.setWorkflowName(TEST_WORKFLOW_NAME);
        orcaRequest.setNamespaceID(TEST_NAMESPACE_ID);
    }

    @Test
    void testDefaultConstructor() {
        lambda = new S3PostReplicationLambda();
        Assertions.assertNotNull(lambda);
    }

    @Test
    void testS3PostReplication_Success_Same_Account() {
        when(workflowRepository.getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID)).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createKmsClient(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destKmsClient);
        doNothing().when(s3PostReplicationService).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), eq(workflowModel));

        OrcaResponse response = assertDoesNotThrow(() -> lambda.handleRequest(orcaRequest, context));

        assertNotNull(response);
        assertEquals(WorkflowStatus.FINISHED.name(), response.getStatus());
        assertEquals(200, response.getStatusCode());
        assertEquals(TEST_NAMESPACE_ID, response.getNamespaceID());
        assertEquals(TEST_WORKFLOW_NAME, response.getWorkflowName());

        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, times(1)).reEnableBucketLifecycleRules(sourceS3Client, "source-bucket", workflowModel);
        verify(s3PostReplicationService, times(1)).reEnableBucketLifecycleRules(destS3Client, "dest-bucket", workflowModel);
        verify(s3ClientFactory, times(1)).createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION);
        verify(s3ClientFactory, times(1)).createS3Client(TEST_DEST_ROLE, DEST_REGION);
        verify(s3PostReplicationService, times(0)).resetKmsKeyPolicyIfBucketIsKMSEncrypted(destKmsClient,
                workflowModel.getDestBucketConfig());
    }

    @Test
    void testS3PostReplication_Success_null_dest_Account() {
        workflowModel.setDestAccountNumber(null);
        when(workflowRepository.getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID)).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createKmsClient(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destKmsClient);
        doNothing().when(s3PostReplicationService).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), eq(workflowModel));

        OrcaResponse response = assertDoesNotThrow(() -> lambda.handleRequest(orcaRequest, context));

        assertNotNull(response);
        assertEquals(WorkflowStatus.FINISHED.name(), response.getStatus());
        assertEquals(200, response.getStatusCode());
        assertEquals(TEST_NAMESPACE_ID, response.getNamespaceID());
        assertEquals(TEST_WORKFLOW_NAME, response.getWorkflowName());

        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, times(1)).reEnableBucketLifecycleRules(sourceS3Client, "source-bucket", workflowModel);
        verify(s3PostReplicationService, times(1)).reEnableBucketLifecycleRules(destS3Client, "dest-bucket", workflowModel);
        verify(s3ClientFactory, times(1)).createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION);
        verify(s3ClientFactory, times(1)).createS3Client(TEST_DEST_ROLE, DEST_REGION);
        verify(s3PostReplicationService, times(0)).resetKmsKeyPolicyIfBucketIsKMSEncrypted(destKmsClient,
                workflowModel.getDestBucketConfig());
    }

    @Test
    void testS3PostReplication_Success_Cross_Account() {
        workflowModel.setDestAccountNumber(TEST_DEST_ACCOUNT);
        when(workflowRepository.getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID)).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        when(s3ClientFactory.createKmsClient(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destKmsClient);
        doNothing().when(s3PostReplicationService).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), eq(workflowModel));

        OrcaResponse response = assertDoesNotThrow(() -> lambda.handleRequest(orcaRequest, context));

        assertNotNull(response);
        assertEquals(WorkflowStatus.FINISHED.name(), response.getStatus());
        assertEquals(200, response.getStatusCode());
        assertEquals(TEST_NAMESPACE_ID, response.getNamespaceID());
        assertEquals(TEST_WORKFLOW_NAME, response.getWorkflowName());

        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, times(1)).reEnableBucketLifecycleRules(sourceS3Client, "source-bucket", workflowModel);
        verify(s3PostReplicationService, times(1)).reEnableBucketLifecycleRules(destS3Client, "dest-bucket", workflowModel);
        verify(s3ClientFactory, times(1)).createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION);
        verify(s3ClientFactory, times(1)).createS3Client(TEST_DEST_ROLE, DEST_REGION);
        verify(s3PostReplicationService, times(1)).resetKmsKeyPolicyIfBucketIsKMSEncrypted(destKmsClient,
                workflowModel.getDestBucketConfig());
    }

    @Test
    void testS3PostReplication_NullWorkflowName_ThrowsInvalidInputException() {
        orcaRequest.setWorkflowName(null);
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> lambda.handleRequest(orcaRequest, context));

        assertNotNull(exception);
        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", exception.getMessage());

        verify(workflowRepository, never()).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, never()).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), any(WorkFlowModel.class));
        verify(s3ClientFactory, never()).createS3Client(anyString(), anyString());
    }

    @Test
    void testS3PostReplication_NullNamespaceId_ThrowsInvalidInputException() {
        orcaRequest.setNamespaceID(null);
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> lambda.handleRequest(orcaRequest, context));

        assertNotNull(exception);
        assertEquals("Invalid input! WorkflowName and NamespaceID are required.", exception.getMessage());

        verify(workflowRepository, never()).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, never()).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), any(WorkFlowModel.class));
        verify(s3ClientFactory, never()).createS3Client(anyString(), anyString());
    }

    @Test
    void testS3PostReplication_NoWorkflowFound_ThrowsEntityNotFoundException() {
        when(workflowRepository.getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID)).thenThrow(EntityNotFoundException.class);

        assertThrows(EntityNotFoundException.class, () -> lambda.handleRequest(orcaRequest, context));

        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, never()).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), any(WorkFlowModel.class));
        verify(s3ClientFactory, never()).createS3Client(anyString(), anyString());
    }

    @Test
    void testS3PostReplication_CreateS3ClientFails_ReturnsFailed() {
        when(workflowRepository.getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID)).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenThrow(RuntimeException.class);

        OrcaResponse response = assertDoesNotThrow(() -> lambda.handleRequest(orcaRequest, context));

        assertNotNull(response);
        assertNotNull(response.getErrorDetails());
        assertEquals(WorkflowStatus.FAILED.name(), response.getStatus());
        assertEquals(500, response.getStatusCode());
        assertEquals(TEST_NAMESPACE_ID, response.getNamespaceID());
        assertEquals(TEST_WORKFLOW_NAME, response.getWorkflowName());

        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, never()).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), any(WorkFlowModel.class));
        verify(s3ClientFactory, times(1)).createS3Client(anyString(), anyString());
    }

    @Test
    void testS3PostReplication_ModifyBucketLifecycleFails_ReturnsFailed() {
        when(workflowRepository.getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID)).thenReturn(workflowModel);
        when(s3ClientFactory.createS3Client(TEST_SOURCE_ROLE, SOURCE_REGION)).thenReturn(sourceS3Client);
        when(s3ClientFactory.createS3Client(TEST_DEST_ROLE, DEST_REGION)).thenReturn(destS3Client);
        doThrow(S3Exception.class).when(s3PostReplicationService).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), eq(workflowModel));

        OrcaResponse response = assertDoesNotThrow(() -> lambda.handleRequest(orcaRequest, context));

        assertNotNull(response);
        assertNotNull(response.getErrorDetails());
        assertEquals(WorkflowStatus.FAILED.name(), response.getStatus());
        assertEquals(500, response.getStatusCode());
        assertEquals(TEST_NAMESPACE_ID, response.getNamespaceID());
        assertEquals(TEST_WORKFLOW_NAME, response.getWorkflowName());

        verify(workflowRepository, times(1)).getWorkflow(TEST_WORKFLOW_NAME, TEST_NAMESPACE_ID);
        verify(s3PostReplicationService, times(1)).reEnableBucketLifecycleRules(any(S3Client.class), anyString(), any(WorkFlowModel.class));
        verify(s3ClientFactory, times(2)).createS3Client(anyString(), anyString());
    }
}