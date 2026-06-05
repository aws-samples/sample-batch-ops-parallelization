package com.amazon.bopspar.persistence.manager;

import com.amazon.bopspar.model.CreateWorkflowRequest;
import com.amazon.bopspar.model.CreateWorkflowResponse;
import com.amazon.bopspar.model.DeleteWorkflowRequest;
import com.amazon.bopspar.model.DeleteWorkflowResponse;
import com.amazon.bopspar.model.GetWorkflowRequest;
import com.amazon.bopspar.model.GetWorkflowResponse;
import com.amazon.bopspar.model.ListWorkflowsResponse;
import com.amazon.bopspar.model.SendControlCommandRequest;
import com.amazon.bopspar.model.SendControlCommandResponse;
import com.amazon.bopspar.model.StartWorkflowRequest;
import com.amazon.bopspar.model.StartWorkflowResponse;
import com.amazon.bopspar.model.StopWorkflowRequest;
import com.amazon.bopspar.model.StopWorkflowResponse;
import com.amazon.bopspar.service.resources.workflow.WorkflowState;
import com.amazon.bopspar.service.resources.workflow.WorkflowStateMachine;


/**
 * Class holding CRUD logic of Workflow.
 */
public interface WorkflowManager {
    /**
     * Create all resources of a workflow.
     * @param request core create workflow request
     * @return core create workflow response
     */
    CreateWorkflowResponse createWorkflow(CreateWorkflowRequest request);

    /**
     * Get the workflow details of the workflow.
     * @param request core get workflow request
     * @return core get workflow response object
     */
    GetWorkflowResponse getWorkflow(GetWorkflowRequest request);

    /**
     * Get the workflow details of the workflow.
     * @return core get workflow response object
     */
    ListWorkflowsResponse listWorkflows();

    /**
     * Delete the specified workflow.
     * @param request core delete request
     */
    DeleteWorkflowResponse deleteWorkflow(DeleteWorkflowRequest request);

    /**
     * Start the specified workflow.
     * @param request core start request
     */

    StartWorkflowResponse startWorkflow(StartWorkflowRequest request, WorkflowStateMachine workflowStateMachine);

    /**
     * Stop the specified workflow.
     * @param request core stop request
     */
    StopWorkflowResponse stopWorkflow(StopWorkflowRequest request);



    /**
     * Send Control command.
     * @param request core SendControlCommandRequest
     */
    SendControlCommandResponse sendControlCommand(SendControlCommandRequest request);

}



