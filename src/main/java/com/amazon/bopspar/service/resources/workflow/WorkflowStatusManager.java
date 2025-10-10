package com.amazon.bopspar.service.resources.workflow;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

/**
 * WorkflowStatusManager is responsible for managing the status and state of a workflow.
 */
public class WorkflowStatusManager {

    private final WorkflowRepository workflowRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStatusManager.class);

    public WorkflowStatusManager(final WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    /**
     * Method to handle updating the workflow status and state when a workflow is stopping.
     * @param workflowModel workflowModel containing the workflow status and state
     * @return WorkflowResponse containing the updated workflow status and state
     */
    public WorkflowResponse handleStoppingStatus(final WorkFlowModel workflowModel) {
        LOGGER.info("Workflow is in STOPPING state, cancelling the workflow. WorkflowName: {}, NamespaceId: {}",
                workflowModel.getWorkflowName(),
                workflowModel.getNamespaceID());
        workflowModel.setStatus(WorkflowStatus.STOPPED.name());
        workflowModel.setState(WorkflowState.CANCELLED.name());
        workflowRepository.updateWorkflow(workflowModel);
        return WorkflowResponseBuilder.buildSuccessResponse(workflowModel, WorkflowStatus.STOPPED);
    }

    /**
     * Checks if the workflow should continue monitoring based on its status.
     * @param workflowModel WorkflowModel containing the workflow status
     * @return true if monitoring should continue (status is RUNNING or STOPPING), false otherwise
     */
    public boolean shouldContinueMonitoring(final WorkFlowModel workflowModel) {
        final String currentStatus = workflowModel.getStatus();
        if (isNullOrEmpty(currentStatus) || !isMonitorableStatus(currentStatus)) {
            LOGGER.info("Workflow status is {} and should not monitor.", currentStatus);
            return false;
        }
        return true;
    }

    private boolean isMonitorableStatus(final String status) {
        return WorkflowStatus.RUNNING.name().equals(status)
                || WorkflowStatus.STOPPING.name().equals(status);
    }
}
