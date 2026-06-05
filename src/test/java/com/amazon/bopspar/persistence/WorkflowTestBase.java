package com.amazon.bopspar.persistence;

import com.amazon.bopspar.model.GetWorkflowRequest;
import com.amazon.bopspar.model.Workflow;
import com.amazon.bopspar.persistence.model.RuntimeConfig;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for test setup for all tests
 */

public class WorkflowTestBase {
    static AmazonDynamoDB amazonDynamoDB;
    static DynamoDB dynamoDB;
    static Table workflowTable;
    protected GetWorkflowRequest request;
    protected  Workflow getWorkFlowObject;
    @BeforeAll
    public static void setUpDynamoDB() {
        // Initialize DynamoDB Local
        amazonDynamoDB = DynamoDBEmbedded.create().amazonDynamoDB();
        dynamoDB = new DynamoDB(amazonDynamoDB);

        // Create the table if it doesn't exist
        CreateTableRequest request = new CreateTableRequest()
                .withTableName("WorkflowTable")
                .withKeySchema(new KeySchemaElement("workflowID", KeyType.HASH)) // Partition Key
                .withAttributeDefinitions(new AttributeDefinition("workflowID", ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L));

        workflowTable = dynamoDB.createTable(request);
        try {
            workflowTable.waitForActive();
        } catch (InterruptedException e) {
            throw new RuntimeException("Table creation interrupted", e);
        }
    }

    @BeforeEach
    public void setUpBase() {
        // Common setup for GetWorkflowRequest
        getWorkFlowObject = new Workflow();
        request = new GetWorkflowRequest();
        request.setNamespaceID("restrictedID");
        request.setWorkflowName("testName");

        getWorkFlowObject.setWorkflowName(request.getWorkflowName());
    }

    @AfterAll
    public static void tearDownDynamoDB() {
        if (workflowTable != null) {
            workflowTable.delete();
            try {
                workflowTable.waitForDelete();
            } catch (InterruptedException e) {
                throw new RuntimeException("Table deletion interrupted", e);
            }
        }

        if (dynamoDB != null) {
            dynamoDB.shutdown();
        }
    }

    protected Workflow createTestWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowName("testWorkflowName");
        workflow.setDestAccountNumber("testDestAcc");
        workflow.setSourceAccountNumber("testSrcAcc");
        workflow.setWorkflowType("testWorkflowType");
        workflow.setSourceRegion("testSourceRegion");
        workflow.setSourceRoleARN("XXXXXXXXXXXXXXXXXXX");
        workflow.setNamespaceID("testNamespaceID");
        return workflow;
    }

    protected Workflow getTestWorkflow() {
        return Workflow.builder()
                .workflowName("Test Workflow")
                .namespaceID("namespace-123")
                .state("ACTIVE")
                .workflowType("COPY")
                .status("RUNNING")
                .sourceRegion("us-east-1")
                .destRegion("us-west-2")
                .sourceRoleARN("arn:aws:iam::123456789:role/source-role")
                .destRoleARN("arn:aws:iam::987654321:role/dest-role")
                .sourceAccountNumber("123456789")
                .destAccountNumber("987654321")
                .bopsJobID("job-123")
                .build();
    }

    protected WorkFlowModel getWorkflowModel() {
        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setWorkflowName("Test Workflow");
        workflowModel.setNamespaceID("namespace-123");
        workflowModel.setState("ACTIVE");
        workflowModel.setWorkflowType("COPY");
        workflowModel.setStatus("RUNNING");
        workflowModel.setSourceRegion("us-east-1");
        workflowModel.setDestRegion("us-west-2");
        workflowModel.setSourceRoleARN("arn:aws:iam::123456789:role/source-role");
        workflowModel.setDestRoleARN("arn:aws:iam::987654321:role/dest-role");
        workflowModel.setSourceAccountNumber("123456789");
        workflowModel.setDestAccountNumber("987654321");
        workflowModel.setBopsJobID("job-123");
        workflowModel.setRuntimeConfig(RuntimeConfig.builder()
                        .dashboardUrl("testUrl")
                        .manifestLocation("testManifestLocation")
                        .build());
        return workflowModel;
    }
}
