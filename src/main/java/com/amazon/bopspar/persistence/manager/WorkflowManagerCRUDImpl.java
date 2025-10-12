package com.amazon.bopspar.persistence.manager;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.model.AWSServiceException;
import com.amazon.bopspar.model.AlreadyExistsException;
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
import com.amazon.bopspar.model.Workflow;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.persistence.utils.InputValidator;
import com.amazon.bopspar.persistence.utils.ModelConverter;
import com.amazon.bopspar.service.resources.workflow.WorkflowStateMachine;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import javax.inject.Inject;

import org.joda.time.Instant;

import java.util.List;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

/**
 * Class holding CRUD logic of Workflow.
 */

@Log4j2
public class WorkflowManagerCRUDImpl implements WorkflowManager {
    private final WorkflowRepository workflowRepository;
    private final SfnClient sfnClient;
    private final Gson gson;

    @Inject
    public WorkflowManagerCRUDImpl(final WorkflowRepository workflowRepository,
                                   final SfnClient sfnClient,
                                   final Gson gson) {
        this.workflowRepository = workflowRepository;
        this.sfnClient = sfnClient;
        this.gson = gson;
    }

    @Override
    public CreateWorkflowResponse createWorkflow(final CreateWorkflowRequest request) {
        InputValidator.validateCreateWorkflowRequest(request);

        //Convert to wf object as CreateWorkflowRequest internally calls Workflow class
        final WorkFlowModel workflow = ModelConverter.convertWorkflowFromCoralToDdbModel(request);
        workflow.setStatus(String.valueOf(WorkflowStatus.READY));
        workflow.setState(String.valueOf(WorkflowStatus.READY));

        // Try persisting
        try {
            workflow.setCreatedAt(Instant.now().toString());
            workflowRepository.createWorkflow(workflow);
        } catch (final ConditionalCheckFailedException alreadyExistsException) {
            String errMsg = String.format("A workflow already exists with workflowName: %s, namespaceID: %s",
                workflow.getWorkflowName(), workflow.getNamespaceID());
            throw new AlreadyExistsException(errMsg, alreadyExistsException);
        } catch (final AmazonDynamoDBException dynamoDBException) {
            throw new AWSServiceException(dynamoDBException.getMessage(), dynamoDBException);
        }
        return CreateWorkflowResponse.builder().build();
    }


    @Override
    public GetWorkflowResponse getWorkflow(final GetWorkflowRequest request) {
        InputValidator.validateGetWorkflowRequest(request);

        String workFlowName = request.getWorkflowName();
        String nameSpaceID = request.getNamespaceID();
        log.info("GetWorkflow: Received request {}", request);
        try {
            WorkFlowModel workflowModel = workflowRepository.getWorkflow(workFlowName, nameSpaceID);
            Workflow coralConvertedResponse = workflowModel.toWorkflow();

            return GetWorkflowResponse.builder()
                .withWorkflow(coralConvertedResponse)
                .build();
        } catch (final AmazonDynamoDBException dynamoDBException) {
            throw new AWSServiceException(dynamoDBException.getMessage(), dynamoDBException);
        }
    }

    @Override
    public ListWorkflowsResponse listWorkflows() {
        try {
            List<WorkFlowModel> workflowModels = workflowRepository.listWorkflows();
            List<Workflow> workflows = workflowModels.stream()
                .map(WorkFlowModel::toWorkflow)
                .toList();

            return ListWorkflowsResponse.builder()
                .workflows(workflows)
                .build();
        } catch (final AmazonDynamoDBException dynamoDBException) {
            throw new AWSServiceException(dynamoDBException.getMessage(), dynamoDBException);
        }
    }

    @Override
    public StartWorkflowResponse startWorkflow(final StartWorkflowRequest request, WorkflowStateMachine workflowStateMachine) {
        InputValidator.validateStartWorkflowRequest(request);
        final String workFlowName = request.getWorkflowName();
        final String nameSpaceID = request.getNamespaceID();
        final WorkFlowModel workflow = getWorkflowFromDynamoDB(workFlowName, nameSpaceID);
        final String currentStatus = workflow.getStatus();

        try {
            if (!currentStatus.equals(String.valueOf(WorkflowStatus.READY))) {
                throw new AWSServiceException("Workflow can only be started from READY status.");
            }
            final StartExecutionRequest startExecutionRequest = StartExecutionRequest.builder()
                .stateMachineArn(System.getenv(workflowStateMachine.getValue() + "_ARN"))
                .input(gson.toJson(request))
                .build();
            StartExecutionResponse response = sfnClient.startExecution(startExecutionRequest);

            log.info("Started execution: {}", response.executionArn());
        } catch (RuntimeException exception) {
            String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s "
                + "cannot be started due to an exception", workFlowName, nameSpaceID);
            throw new AWSServiceException(errMsg, exception);
        }

        updateWorkflowToRunningStatus(workflow, workFlowName, nameSpaceID, currentStatus);
        return StartWorkflowResponse.builder().build();
    }

    private void updateWorkflowToRunningStatus(final WorkFlowModel workflow, final String workFlowName,
                                               final String nameSpaceID, final String currentStatus) {
        try {
            workflow.setStatus(String.valueOf(WorkflowStatus.RUNNING));
            workflow.setStartedAt(Instant.now().toString());
            log.info("Setting status to RUNNING for {}", workflow.toString());
            workflowRepository.updateWorkflow(workflow);
            log.info("Successfully set the {} status to RUNNING", workflow.toString());
        } catch (ConditionalCheckFailedException conditionalCheckFailedException) {
            log.error("Failed to start workflow for request with workflowName: {}, namespaceID: {}."
                    + " Current status: {}",
                workFlowName,
                nameSpaceID,
                currentStatus);

            String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s "
                + "cannot be started from status: %s", workFlowName, nameSpaceID, currentStatus);
            throw new AWSServiceException(errMsg, conditionalCheckFailedException);
        }

    }

    private WorkFlowModel getWorkflowFromDynamoDB(final String workFlowName, final String nameSpaceID) {
        try {
            return workflowRepository.getWorkflow(workFlowName, nameSpaceID);
        } catch (final AmazonDynamoDBException dynamoDBException) {
            throw new AWSServiceException(dynamoDBException.getMessage(), dynamoDBException);
        }
    }

    @Override
    public DeleteWorkflowResponse deleteWorkflow(final DeleteWorkflowRequest request) {
        InputValidator.validateDeleteWorkflow(request);
        String workFlowName = request.getWorkflowName();
        String nameSpaceID = request.getNamespaceID();
        WorkFlowModel workflowModel = workflowRepository.getWorkflow(workFlowName, nameSpaceID);
        String currentStatus = workflowModel.getStatus();

        if (currentStatus.equals(String.valueOf(WorkflowStatus.STOPPED))
            || currentStatus.equals(String.valueOf(WorkflowStatus.FAILED))) {
            try {
                workflowModel.setStatus(String.valueOf(WorkflowStatus.DELETED));
                log.info("Setting status to DELETED for {}", workflowModel.toString());
                workflowRepository.updateWorkflow(workflowModel);
                log.info("Successfully set the {} status to DELETED", workflowModel.toString());
            } catch (ConditionalCheckFailedException cafe) {
                log.error("Failed to delete workflow for request with workflowName: {}, namespaceID: {}."
                        + " Current status: {}",
                    workFlowName,
                    nameSpaceID,
                    currentStatus);
                String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s "
                    + "cannot be deleted from status: %s", workFlowName, nameSpaceID, currentStatus);
                throw new InvalidInputException(errMsg, cafe);
            } catch (final AmazonDynamoDBException dynamoDBException) {
                throw new AWSServiceException(dynamoDBException.getMessage(), dynamoDBException);
            }
        } else {
            String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s can only be"
                + " deleted from status STOPPED. CurrentStatus: %s", workFlowName, nameSpaceID, currentStatus);
            throw new AWSServiceException(errMsg);
        }

        return DeleteWorkflowResponse.builder().build();
    }

    @Override
    public StopWorkflowResponse stopWorkflow(final StopWorkflowRequest request) {
        InputValidator.validateStopWorkflow(request);
        String workFlowName = request.getWorkflowName();
        String nameSpaceID = request.getNamespaceID();
        WorkFlowModel workflowModel = workflowRepository.getWorkflow(workFlowName, nameSpaceID);
        WorkflowStatus currentStatus = WorkflowStatus.valueOf(workflowModel.getStatus());

        if (isStoppableStatus(currentStatus)) {
            try {
                workflowModel.setStatus(String.valueOf(WorkflowStatus.STOPPING));
                log.info("Setting status to STOPPING for {}", workflowModel.toString());
                workflowRepository.updateWorkflow(workflowModel);
                log.info("Successfully set the {} status to STOPPING", workflowModel.toString());
                if (currentStatus == WorkflowStatus.WAITING) {
                    log.info("Workflow is waiting for customer acknowledgement. Exiting wait state.");
//                    reportSuccessAsync(workFlowName, nameSpaceID, STOP_WORKFLOW);
                }
            } catch (ConditionalCheckFailedException cafe) {
                log.error("Failed to stop workflow for request with workflowName: {}, namespaceID: {}."
                        + " Current status: {}",
                    workFlowName,
                    nameSpaceID,
                    currentStatus);
                String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s"
                    + " cannot be stopped from status: %s", workFlowName, nameSpaceID, currentStatus);
                throw new AWSServiceException(errMsg, cafe);
            }
        } else {
            String errorMessage = String.format("The workflow with workflowName: %s, namespaceID: %s"
                + " can only be stopped from statuses:"
                + " STARTING, RUNNING, WAITING. CurrentStatus: %s", workFlowName, nameSpaceID, currentStatus);
            throw new AWSServiceException(errorMessage);
        }

        return StopWorkflowResponse.builder().build();
    }

    @Override
    public SendControlCommandResponse sendControlCommand(final SendControlCommandRequest request) {
        InputValidator.validateSendControlCommandRequest(request);
        final String workFlowName = request.getWorkflowName();
        final String nameSpaceID = request.getNamespaceID();
        final String notificationID = request.getNotificationID();

        final WorkFlowModel workflowModel = workflowRepository.getWorkflow(workFlowName, nameSpaceID);
        final String currentStatus = workflowModel.getStatus();
        final String taskToken = workflowModel.getTaskToken();

        if (isNullOrEmpty(taskToken)) {
            String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s "
                + "cannot be resumed as taskToken is null", workFlowName, nameSpaceID);
            throw new AWSServiceException(errMsg);
        }

        if (currentStatus.equals(String.valueOf(WorkflowStatus.WAITING))
            || currentStatus.equals(String.valueOf(WorkflowStatus.RESUMING))) {
            try {
                if (currentStatus.equals(String.valueOf(WorkflowStatus.WAITING))) {
                    log.info("Resuming Workflow for {}", workflowModel.toString());
                    workflowModel.setStatus(String.valueOf(WorkflowStatus.RESUMING));
                }
                workflowRepository.updateWorkflow(workflowModel);

                WorkflowResponse response = WorkflowResponseBuilder.buildSuccessResponse(workflowModel, WorkflowStatus.RUNNING);
                sfnClient.sendTaskSuccess(SendTaskSuccessRequest.builder()
                    .taskToken(taskToken)
                    .output(gson.toJson(response))
                    .build());
                workflowModel.setStatus(String.valueOf(WorkflowStatus.RUNNING));
                workflowModel.setAckedNotification(notificationID);
                log.info("Setting status to RUNNING for {}", workflowModel.toString());
                workflowRepository.updateWorkflow(workflowModel);
                log.info("Successfully set the {} status to RUNNING", workflowModel.toString());
            } catch (ConditionalCheckFailedException cafe) {
                log.error("Failed to sendControlCommand for request with workflowName: {}, namespaceID: {}."
                        + " Current status: {}",
                    workFlowName,
                    nameSpaceID,
                    currentStatus);
                String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s,"
                    + " cannot be updated to RUNNING from status: %s", workFlowName, nameSpaceID, currentStatus);
                throw new InvalidInputException(errMsg, cafe);
            } catch (final AmazonDynamoDBException dynamoDBException) {
                throw new AWSServiceException(dynamoDBException.getMessage(), dynamoDBException);
            } catch (Exception workflowException) {
                String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s "
                    + "cannot be started due to an Workflow exception", workFlowName, nameSpaceID);
                throw new AWSServiceException(errMsg, workflowException);
            }
        } else {
            String errMsg = String.format("The workflow with workflowName: %s, namespaceID: %s, "
                + "can only be started from WAITING. CurrentStatus: %s", workFlowName, nameSpaceID, currentStatus);
            throw new AWSServiceException(errMsg);
        }

        return SendControlCommandResponse.builder().build();
    }

    private boolean isStoppableStatus(final WorkflowStatus workflowStatus) {
        return workflowStatus == WorkflowStatus.STARTING
            || workflowStatus == WorkflowStatus.RUNNING
            || workflowStatus == WorkflowStatus.WAITING;
    }
}
