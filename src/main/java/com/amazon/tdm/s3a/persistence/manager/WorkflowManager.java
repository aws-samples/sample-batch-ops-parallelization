package com.amazon.tdm.s3a.persistence.manager;

import com.amazon.tdm.s3a.model.CreateWorkflowRequest;
import com.amazon.tdm.s3a.model.CreateWorkflowResponse;
import com.amazon.tdm.s3a.model.DeleteWorkflowRequest;
import com.amazon.tdm.s3a.model.DeleteWorkflowResponse;
import com.amazon.tdm.s3a.model.GetWorkflowRequest;
import com.amazon.tdm.s3a.model.GetWorkflowResponse;
import com.amazon.tdm.s3a.model.ListWorkflowsResponse;
import com.amazon.tdm.s3a.model.SendControlCommandRequest;
import com.amazon.tdm.s3a.model.SendControlCommandResponse;
import com.amazon.tdm.s3a.model.StartWorkflowRequest;
import com.amazon.tdm.s3a.model.StartWorkflowResponse;
import com.amazon.tdm.s3a.model.StopWorkflowRequest;
import com.amazon.tdm.s3a.model.StopWorkflowResponse;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowState;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowStateMachine;


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



