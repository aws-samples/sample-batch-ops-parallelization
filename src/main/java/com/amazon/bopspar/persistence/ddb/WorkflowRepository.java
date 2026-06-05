
package com.amazon.bopspar.persistence.ddb;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.EntityNotFoundException;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDeleteExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalOperator;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * This class abstracts the storage layer for Workflow.
 */
@Log4j2
public class WorkflowRepository {

    private static final String WORKFLOW_NAME = "workflowName";
    private static final String NAMESPACE_ID = "namespaceID";

    //Why suppress you many wonder? https://sage.amazon.dev/posts/1730645?t=7
    private final BaseDynamoDBFacade ddbFacade;

    public WorkflowRepository(final BaseDynamoDBFacade ddbFacade) {
        this.ddbFacade = ddbFacade;
    }

    /*
     * Persist the specified workflow entry in DynamoDB if no entry with the same Primary Key
     * already existed.
     * Use BaseDynamoDBFacade to persist the data in DDB.
     * @param workflow the workflow entry to be persisted in DDB
     */

    public void createWorkflow(final WorkFlowModel workflow) {
        if (workflow == null) {
            throw new InvalidInputException("Workflow cannot be null");
        }
        ddbFacade.create(workflow, (wf) -> createDynamoDBSaveExpression());
    }

    /**
     * Create a DynamoDBSaveExpression to be used for create operation.
     *
     * @return the DynamoDBSaveExpression
     */
    DynamoDBSaveExpression createDynamoDBSaveExpression() {
        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
        saveExpression.withExpectedEntry(WORKFLOW_NAME,
                new ExpectedAttributeValue().withExists(false))
                .withExpectedEntry(NAMESPACE_ID, new ExpectedAttributeValue()
                        .withExists(false))
                .withConditionalOperator(ConditionalOperator.AND);;
        return saveExpression;
    }

    /**
     * Create a DynamoDBSaveExpression to be used for update operations.
     *
     * @return the DynamoDBSaveExpression
     */
    DynamoDBSaveExpression createDynamoDBUpdateExpression(final WorkFlowModel workflowModel) {
        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
        saveExpression.withExpectedEntry(WORKFLOW_NAME,
                        new ExpectedAttributeValue()
                                .withExists(true)
                                .withValue(new AttributeValue().withS(workflowModel.getWorkflowName())))
                .withExpectedEntry(NAMESPACE_ID,
                        new ExpectedAttributeValue()
                                .withExists(true)
                                .withValue(new AttributeValue().withS(workflowModel.getNamespaceID())))
                .withConditionalOperator(ConditionalOperator.AND);
        return saveExpression;
    }

    public WorkFlowModel getWorkflow(final String workflowName, final String nameSpaceID) {
        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setWorkflowName(workflowName);
        workflowModel.setNamespaceID(nameSpaceID);

        workflowModel = ddbFacade.get(workflowModel, WorkFlowModel::toString);
        if (workflowModel == null) {
            String errorMessage = String.format(
                    "Workflow does not exist for {workflow %s}",
                    workflowName);
            throw new EntityNotFoundException(errorMessage);
        }
        return workflowModel;
    }

    public List<WorkFlowModel> listWorkflows() {
        PaginatedScanList<WorkFlowModel> workflowModels = ddbFacade.list(WorkFlowModel.class, new DynamoDBScanExpression());
        return workflowModels.stream().toList();
    }

    /**
     * Updating workflow in DynamoDB.
     * @param workflowModel the object of Workflow
     *
     */
    public void updateWorkflow(final WorkFlowModel workflowModel) {
        ddbFacade.update(workflowModel, (wf) -> createDynamoDBUpdateExpression(workflowModel));
    }

    /**
     * Deleting workflow in DynamoDB.
     * @param workflowModel the object of Workflow
     *
     */
    public void deleteWorkflow(final WorkFlowModel workflowModel) {
        ddbFacade.delete(workflowModel, (wf) -> createDynamoDBDeleteExpression(workflowModel) );
    }

    /**
     * Create a DynamoDBDeleteExpression to be used for delete operation.
     * @param workflowModel the object of Workflow
     * @return the DynamoDBDeleteExpression
     */
    DynamoDBDeleteExpression createDynamoDBDeleteExpression(final WorkFlowModel workflowModel) {
        DynamoDBDeleteExpression deleteExpression = new DynamoDBDeleteExpression();
        deleteExpression.withExpectedEntry(WORKFLOW_NAME,
                        new ExpectedAttributeValue()
                                .withExists(true)
                                .withValue(new AttributeValue().withS(workflowModel.getWorkflowName())))
                .withExpectedEntry(NAMESPACE_ID,
                        new ExpectedAttributeValue()
                                .withExists(true)
                                .withValue(new AttributeValue().withS(workflowModel.getNamespaceID())))
                .withConditionalOperator(ConditionalOperator.AND);
        return deleteExpression;
    }
}
