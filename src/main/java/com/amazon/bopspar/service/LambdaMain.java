package com.amazon.bopspar.service;

import com.amazon.bopspar.model.AlreadyExistsException;
import com.amazon.bopspar.model.CreateWorkflowRequest;
import com.amazon.bopspar.model.CreateWorkflowResponse;
import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.StartWorkflowResponse;
import com.amazon.bopspar.model.StartWorkflowRequest;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.activity.WorkflowActivity;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazon.bopspar.service.utils.ErrorResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazon.bopspar.persistence.utils.ModelConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class LambdaMain implements RequestHandler<CreateWorkflowRequest, WorkflowResponse> {
    private static final Logger log = LogManager.getLogger(LambdaMain.class);
    private final LambdaComponent lambdaComponent;
    private final WorkflowActivity workflowActivity;    
    private static final String ROUTE_CREATE_WORKFLOW = "/createWorkflow";
    private static final String ROUTE_GET_WORKFLOW = "/getWorkflow";
    private static final String ROUTE_START_WORKFLOW = "/startWorkflow";
    private static final String ROUTE_START_MANIFEST_SPLIT_WORKFLOW = "/startManifestSplitWorkflow";
    private static final String ROUTE_DELETE_WORKFLOW = "/deleteWorkflow";
    private static final String ROUTE_SEND_CONTROL_COMMAND = "/sendControlCommand";
    private static final String ROUTE_LIST_WORKFLOWS = "/listWorkflows";
    private static final String ROUTE_ECHO = "/Echo";
    private static final String HTTP_METHOD_POST = "POST";
    private static final int STATUS_CODE_500 = 500;
    private static final int STATUS_CODE_200 = 200;
    private static final int STATUS_CODE_404 = 404;

    public LambdaMain() {
        this.lambdaComponent = DaggerLambdaComponent.create();
        this.workflowActivity = lambdaComponent.getWorkflowActivity();        
    }

    /**
     * Main handler - Using the workflow parameters:
     * 1. Creates an entry in the DB 
     * 2. Starts executing the worflow
     *
     * @param workflowRequest  - workflow request
     */
    @Override    
    public WorkflowResponse handleRequest(final CreateWorkflowRequest workflowRequest, final Context context) {
        //final String path = request.getPath();
        //final String httpMethod = request.getHttpMethod();
        final String requestId = context.getAwsRequestId();
        WorkFlowModel workflowDetails = ModelConverter.convertWorkflowFromCoralToDdbModel(workflowRequest);            
        log.info("BEGIN: Processing request {}", requestId);
        try {            
            log.info("WF request: {}", workflowRequest.toString());
            log.info("WF model: {}", workflowDetails.toString());            
            
            CreateWorkflowResponse createWorkflowResponse = workflowActivity.createWorkflow(workflowRequest);
            log.info("WF created: {}", createWorkflowResponse.toString());
            final StartWorkflowRequest startWorkflowRequest = StartWorkflowRequest
                    .builder()
                    .withWorkflowName(workflowDetails.getWorkflowName())
                    .withNamespaceID(workflowDetails.getNamespaceID())
                    .build();
            StartWorkflowResponse startWorkflowResponse = workflowActivity.startWorkflow(startWorkflowRequest);
            log.info("WF Started: {}", startWorkflowResponse.toString());

        } catch (Exception e) {
            log.error("Unexpected error occurred", e);
            return WorkflowResponseBuilder.buildRuntimeErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED,                     
                    new RuntimeException(e.toString()));
        }

        return WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.RUNNING);
    }
    
}