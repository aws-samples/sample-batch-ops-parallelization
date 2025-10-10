package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazon.bopspar.service.validator.WorkflowRequestValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Lambda to store task token when waiting for customer ack
 */
public class WaitForCustomerAckLambda implements RequestHandler<WorkflowRequest, WorkflowResponse> {
    private static final Logger LOGGER = LogManager.getLogger(WaitForCustomerAckLambda.class);
    private final WorkflowRepository workflowRepository;

    public WaitForCustomerAckLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
    }

    // Unit testing
    WaitForCustomerAckLambda(final WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Override
    public WorkflowResponse handleRequest(final WorkflowRequest workflowRequest, final Context context) {
        WorkflowRequestValidator.validateWorkflowRequest(workflowRequest);
        final String workflowName = workflowRequest.getWorkflowName();
        final String namespaceID = workflowRequest.getNamespaceID();
        final String taskToken = workflowRequest.getTaskToken();
        LOGGER.info("Workflow Request: {} {} {}", workflowName, namespaceID, taskToken);

        //Get workflow details
        final WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);
        workflowDetails.setTaskToken(taskToken);
        workflowRepository.updateWorkflow(workflowDetails);

        // Send Success Workflow Response
        return WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
    }
}
