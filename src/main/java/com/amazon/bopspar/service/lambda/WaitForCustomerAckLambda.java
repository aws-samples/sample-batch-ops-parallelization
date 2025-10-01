package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.requests.OrcaRequest;
import com.amazon.bopspar.service.responses.OrcaResponse;
import com.amazon.bopspar.service.responses.OrcaResponseBuilder;
import com.amazon.bopspar.service.validator.OrcaRequestValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Lambda to store task token when waiting for customer ack
 */
public class WaitForCustomerAckLambda implements RequestHandler<OrcaRequest, OrcaResponse> {
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
    public OrcaResponse handleRequest(final OrcaRequest orcaRequest, final Context context) {
        OrcaRequestValidator.validateOrcaRequest(orcaRequest);
        final String workflowName = orcaRequest.getWorkflowName();
        final String namespaceID = orcaRequest.getNamespaceID();
        final String taskToken = orcaRequest.getTaskToken();
        LOGGER.info("Orca Request: {} {} {}", workflowName, namespaceID, taskToken);

        //Get workflow details
        final WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);
        workflowDetails.setTaskToken(taskToken);
        workflowRepository.updateWorkflow(workflowDetails);

        // Send Success Orca Response
        return OrcaResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
    }
}