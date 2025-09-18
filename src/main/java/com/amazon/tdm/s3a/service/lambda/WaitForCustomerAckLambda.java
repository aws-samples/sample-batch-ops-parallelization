package com.amazon.tdm.s3a.service.lambda;

import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.dagger.DaggerLambdaComponent;
import com.amazon.tdm.s3a.service.dagger.LambdaComponent;
import com.amazon.tdm.s3a.service.requests.OrcaRequest;
import com.amazon.tdm.s3a.service.responses.OrcaResponse;
import com.amazon.tdm.s3a.service.responses.OrcaResponseBuilder;
import com.amazon.tdm.s3a.service.validator.OrcaRequestValidator;
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