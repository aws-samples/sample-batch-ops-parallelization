package com.amazon.tdm.s3a.persistence.utils;

import com.amazon.tdm.s3a.model.CreateWorkflowRequest;
import com.amazon.tdm.s3a.model.Workflow;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModelConverterTest {
    @Test
    public void testConvertWorkflowFromCoralToDdbModel_WithValidInput() {
        // Arrange
        Workflow workflow = Workflow.builder()
                .workflowName("Test Workflow")
                .namespaceID("namespace-123")
                .build();

        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .withWorkflow(workflow)
                .build();

        // Act
        WorkFlowModel result = ModelConverter.convertWorkflowFromCoralToDdbModel(request);

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertEquals("Test Workflow", result.getWorkflowName());
        Assertions.assertEquals("namespace-123", result.getNamespaceID());
    }

    @Test
    public void testConvertWorkflowFromCoralToDdbModel_WithValidInputAndManifestLocation() {
        // Arrange
        Workflow workflow = Workflow.builder()
                .workflowName("Test Workflow")
                .namespaceID("namespace-123")
                .runtimeConfig(com.amazon.tdm.s3a.model.RuntimeConfig.builder().withManifestLocation("testManifestLocation").build())
                .build();

        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .withWorkflow(workflow)
                .build();

        // Act
        WorkFlowModel result = ModelConverter.convertWorkflowFromCoralToDdbModel(request);

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertEquals("Test Workflow", result.getWorkflowName());
        Assertions.assertEquals("namespace-123", result.getNamespaceID());
        Assertions.assertEquals("testManifestLocation", result.getRuntimeConfig().getManifestLocation());
    }

    @Test
    public void testConvertWorkflowFromCoralToDdbModel_WithNullInput() {
        // Arrange
        CreateWorkflowRequest request = null;

        // Act
        Assertions.assertThrows(NullPointerException.class, () -> {
            ModelConverter.convertWorkflowFromCoralToDdbModel(request);
        });
    }
}

