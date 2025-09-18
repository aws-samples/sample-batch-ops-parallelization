package com.amazon.tdm.s3a.service.resources.replication;

import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ReplicationUtilsTest {

    @Mock
    private S3Client mockS3Client;

    @Mock
    private S3ReplicationConfigurator mockReplicationConfigurator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGenerateReplicationRuleId_Success() {
        String destBucketArn = "arn:aws:s3:::test-bucket";
        String destRegion = "us-west-2";

        String result = S3ReplicationUtils.generateReplicationRuleId(destBucketArn, destRegion);

        assertEquals("S3A-test-bucket-us-west-2", result);
    }

    @Test
    void testGenerateReplicationRuleId_NullBucketArn() {
        String destRegion = "us-west-2";

        InvalidInputException exception = assertThrows(InvalidInputException.class, () ->
                S3ReplicationUtils.generateReplicationRuleId(null, destRegion));

        assertEquals("Destination bucket ARN cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGenerateReplicationRuleId_EmptyBucketArn() {
        String destRegion = "us-west-2";

        InvalidInputException exception = assertThrows(InvalidInputException.class, () ->
                S3ReplicationUtils.generateReplicationRuleId("", destRegion));

        assertEquals("Destination bucket ARN cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGenerateReplicationRuleId_NullRegion() {
        String destBucketArn = "arn:aws:s3:::test-bucket";

        InvalidInputException exception = assertThrows(InvalidInputException.class, () ->
                S3ReplicationUtils.generateReplicationRuleId(destBucketArn, null));

        assertEquals("Destination region cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGenerateReplicationRuleId_EmptyRegion() {
        String destBucketArn = "arn:aws:s3:::test-bucket";

        InvalidInputException exception = assertThrows(InvalidInputException.class, () ->
                S3ReplicationUtils.generateReplicationRuleId(destBucketArn, ""));

        assertEquals("Destination region cannot be null or empty", exception.getMessage());
    }

    @Test
    void testGenerateReplicationRuleId_InvalidArn() {
        String invalidArn = "invalid-arn";
        String destRegion = "us-west-2";

        InvalidInputException exception = assertThrows(InvalidInputException.class, () ->
                S3ReplicationUtils.generateReplicationRuleId(invalidArn, destRegion));

        assertTrue(exception.getMessage().contains("Invalid ARN format"));
    }

    @Test
    void testAddCrossAccountPolicyIfNeeded_CrossAccount() {
        WorkFlowModel workflowDetails = new WorkFlowModel();
        workflowDetails.setSourceAccountNumber("123456789");
        workflowDetails.setDestAccountNumber("987654321");
        workflowDetails.setSourceBucketARN("arn:aws:s3:::source-bucket");

        S3ReplicationUtils.addCrossAccountPolicyIfNeeded(
                workflowDetails,
                mockS3Client,
                mockReplicationConfigurator
        );

        verify(mockReplicationConfigurator, times(1)).addCrossAccountBucketPolicy(
                eq(workflowDetails),
                eq(mockS3Client),
                eq("source-bucket")
        );
    }

    @Test
    void testAddCrossAccountPolicyIfNeeded_SameAccount() {
        WorkFlowModel workflowDetails = new WorkFlowModel();
        workflowDetails.setSourceAccountNumber("123456789");
        workflowDetails.setDestAccountNumber("123456789");

        S3ReplicationUtils.addCrossAccountPolicyIfNeeded(
                workflowDetails,
                mockS3Client,
                mockReplicationConfigurator
        );

        verify(mockReplicationConfigurator, never()).addCrossAccountBucketPolicy(
                any(WorkFlowModel.class),
                any(S3Client.class),
                anyString()
        );
    }

    @Test
    void testIsCrossAccountReplication_AllBranches() {
        // Test all four possible branches
        assertFalse(S3ReplicationUtils.isCrossAccountReplication(null, "123"));
        assertFalse(S3ReplicationUtils.isCrossAccountReplication("123", "123"));
        assertTrue(S3ReplicationUtils.isCrossAccountReplication("123", "456"));
        assertTrue(S3ReplicationUtils.isCrossAccountReplication("123", null));
    }

    // Add these tests to your existing S3ReplicationUtilsTest class

    @Test
    void testGetUpdatedWorkflow_FullWorkflowModel() {
        // Arrange
        Map<String, String> sourceConfig = new HashMap<>();
        sourceConfig.put("sourceKey", "sourceValue");

        Map<String, String> destConfig = new HashMap<>();
        destConfig.put("destKey", "destValue");

        Map<String, String> workflowConfig = new HashMap<>();
        workflowConfig.put("key", "value");

        WorkFlowModel originalWorkflow = WorkFlowModel.builder()
                .workflowName("test-workflow")
                .namespaceID("test-namespace")
                .status("IN_PROGRESS")
                .state("ACTIVE")
                .workflowType("REPLICATION")
                .sourceBucketARN("source-bucket-arn")
                .destBucketARN("dest-bucket-arn")
                .sourceRoleARN("source-role-arn")
                .destRoleARN("dest-role-arn")
                .sourceAccountNumber("123")
                .destAccountNumber("456")
                .sourceRegion("us-east-1")
                .destRegion("us-west-2")
                .sourceBucketConfig(sourceConfig)
                .destBucketConfig(destConfig)
                .workflowConfig(workflowConfig)
                .bopsJobID("bops-123")
                .ackedNotification("acked-1")
                .sentNotification("sent-1")
                .jobReportBucketARN("report-arn")
                .createdAt("2023-01-01")
                .startedAt("2023-01-02")
                .bopsJobDuration("1h")
                .manifestLocation("s3://manifest")
                .build();

        // Act
        WorkFlowModel updatedWorkflow = S3ReplicationUtils.getUpdatedWorkflow(originalWorkflow);

        // Assert
        // Verify swapped fields
        assertEquals(originalWorkflow.getDestBucketARN(), updatedWorkflow.getSourceBucketARN());
        assertEquals(originalWorkflow.getSourceBucketARN(), updatedWorkflow.getDestBucketARN());
        assertEquals(originalWorkflow.getDestRoleARN(), updatedWorkflow.getSourceRoleARN());
        assertEquals(originalWorkflow.getSourceRoleARN(), updatedWorkflow.getDestRoleARN());
        assertEquals(originalWorkflow.getDestAccountNumber(), updatedWorkflow.getSourceAccountNumber());
        assertEquals(originalWorkflow.getSourceAccountNumber(), updatedWorkflow.getDestAccountNumber());
        assertEquals(originalWorkflow.getDestRegion(), updatedWorkflow.getSourceRegion());
        assertEquals(originalWorkflow.getSourceRegion(), updatedWorkflow.getDestRegion());
        assertEquals(originalWorkflow.getDestBucketConfig(), updatedWorkflow.getSourceBucketConfig());
        assertEquals(originalWorkflow.getSourceBucketConfig(), updatedWorkflow.getDestBucketConfig());

        // Verify unchanged fields
        assertEquals(originalWorkflow.getWorkflowName(), updatedWorkflow.getWorkflowName());
        assertEquals(originalWorkflow.getNamespaceID(), updatedWorkflow.getNamespaceID());
        assertEquals(originalWorkflow.getStatus(), updatedWorkflow.getStatus());
        assertEquals(originalWorkflow.getState(), updatedWorkflow.getState());
        assertEquals(originalWorkflow.getWorkflowType(), updatedWorkflow.getWorkflowType());
        assertEquals(originalWorkflow.getWorkflowConfig(), updatedWorkflow.getWorkflowConfig());
        assertEquals(originalWorkflow.getBopsJobID(), updatedWorkflow.getBopsJobID());
        assertEquals(originalWorkflow.getAckedNotification(), updatedWorkflow.getAckedNotification());
        assertEquals(originalWorkflow.getSentNotification(), updatedWorkflow.getSentNotification());
        assertEquals(originalWorkflow.getJobReportBucketARN(), updatedWorkflow.getJobReportBucketARN());
        assertEquals(originalWorkflow.getCreatedAt(), updatedWorkflow.getCreatedAt());
        assertEquals(originalWorkflow.getStartedAt(), updatedWorkflow.getStartedAt());
        assertEquals(originalWorkflow.getBopsJobDuration(), updatedWorkflow.getBopsJobDuration());
        assertEquals(originalWorkflow.getManifestLocation(), updatedWorkflow.getManifestLocation());
    }

    @Test
    void testGetUpdatedWorkflow_NullInput() {
        assertThrows(InvalidInputException.class, () -> {
            S3ReplicationUtils.getUpdatedWorkflow(null);
        }, "While setting up reverse replication, found that the source workflow is null");
    }

    @Test
    void testGetUpdatedWorkflow_MinimalWorkflow() {
        // Arrange
        WorkFlowModel originalWorkflow = WorkFlowModel.builder()
                .workflowName("test-workflow")
                .namespaceID("test-namespace")
                .build();

        // Act
        WorkFlowModel updatedWorkflow = S3ReplicationUtils.getUpdatedWorkflow(originalWorkflow);

        // Assert
        assertEquals(originalWorkflow.getWorkflowName(), updatedWorkflow.getWorkflowName());
        assertEquals(originalWorkflow.getNamespaceID(), updatedWorkflow.getNamespaceID());
        assertNull(updatedWorkflow.getSourceBucketARN());
        assertNull(updatedWorkflow.getDestBucketARN());
        assertNull(updatedWorkflow.getSourceBucketConfig());
        assertNull(updatedWorkflow.getDestBucketConfig());
    }

    @Test
    void testGetUpdatedWorkflow_NullConfigs() {
        // Arrange
        WorkFlowModel originalWorkflow = WorkFlowModel.builder()
                .workflowName("test-workflow")
                .namespaceID("test-namespace")
                .sourceBucketARN("source-arn")
                .destBucketARN("dest-arn")
                .sourceBucketConfig(null)
                .destBucketConfig(null)
                .workflowConfig(null)
                .build();

        // Act
        WorkFlowModel updatedWorkflow = S3ReplicationUtils.getUpdatedWorkflow(originalWorkflow);

        // Assert
        assertEquals(originalWorkflow.getDestBucketARN(), updatedWorkflow.getSourceBucketARN());
        assertEquals(originalWorkflow.getSourceBucketARN(), updatedWorkflow.getDestBucketARN());
        assertNull(updatedWorkflow.getSourceBucketConfig());
        assertNull(updatedWorkflow.getDestBucketConfig());
        assertNull(updatedWorkflow.getWorkflowConfig());
    }

    @Test
    void testGetUpdatedWorkflow_EmptyConfigs() {
        // Arrange
        WorkFlowModel originalWorkflow = WorkFlowModel.builder()
                .workflowName("test-workflow")
                .namespaceID("test-namespace")
                .sourceBucketARN("source-arn")
                .destBucketARN("dest-arn")
                .sourceBucketConfig(new HashMap<>())
                .destBucketConfig(new HashMap<>())
                .workflowConfig(new HashMap<>())
                .build();

        // Act
        WorkFlowModel updatedWorkflow = S3ReplicationUtils.getUpdatedWorkflow(originalWorkflow);

        // Assert
        assertEquals(originalWorkflow.getDestBucketARN(), updatedWorkflow.getSourceBucketARN());
        assertEquals(originalWorkflow.getSourceBucketARN(), updatedWorkflow.getDestBucketARN());
        assertTrue(updatedWorkflow.getSourceBucketConfig().isEmpty());
        assertTrue(updatedWorkflow.getDestBucketConfig().isEmpty());
        assertTrue(updatedWorkflow.getWorkflowConfig().isEmpty());
    }

}
