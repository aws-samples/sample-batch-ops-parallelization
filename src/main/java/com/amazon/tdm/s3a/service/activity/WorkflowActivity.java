package com.amazon.tdm.s3a.service.activity;


import com.amazon.tdm.s3a.model.EntityNotFoundException;
import com.amazon.tdm.s3a.model.AlreadyExistsException;
import com.amazon.tdm.s3a.model.EligibilityCheckRequest;
import com.amazon.tdm.s3a.model.EligibilityCheckResponse;
import com.amazon.tdm.s3a.model.ListWorkflowsResponse;
import com.amazon.tdm.s3a.model.S3AInternalServiceException;
import com.amazon.tdm.s3a.model.AWSServiceException;
import com.amazon.tdm.s3a.model.InvalidInputException;
import com.amazon.tdm.s3a.model.DeleteWorkflowRequest;
import com.amazon.tdm.s3a.model.DeleteWorkflowResponse;
import com.amazon.tdm.s3a.model.EchoInput;
import com.amazon.tdm.s3a.model.EchoOutput;
import com.amazon.tdm.s3a.model.CreateWorkflowRequest;
import com.amazon.tdm.s3a.model.CreateWorkflowResponse;
import com.amazon.tdm.s3a.model.GetWorkflowRequest;
import com.amazon.tdm.s3a.model.GetWorkflowResponse;
import com.amazon.tdm.s3a.model.SendControlCommandRequest;
import com.amazon.tdm.s3a.model.SendControlCommandResponse;
import com.amazon.tdm.s3a.model.StopWorkflowRequest;
import com.amazon.tdm.s3a.model.StopWorkflowResponse;
import com.amazon.tdm.s3a.model.StartWorkflowResponse;
import com.amazon.tdm.s3a.model.StartWorkflowRequest;
import com.amazon.tdm.s3a.model.Workflow;
import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.manager.WorkflowManager;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import com.amazon.tdm.s3a.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowStateMachine;
import com.amazon.tdm.s3a.service.validator.InputValidator;
import com.amazon.tdm.s3a.service.validator.S3RequestValidator;
import javax.inject.Inject;

import static org.apache.commons.text.StringEscapeUtils.escapeJava;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.RequestedJobStatus;

/**
 * Workflow Activity implementation - Entry point for all S3A operations.
 */
public class WorkflowActivity {
    private static final Logger log = LogManager.getLogger(WorkflowActivity.class);

    private final WorkflowManager workflowManager;
    private final InputValidator inputValidator;
    private final S3ClientFactory s3ClientFactory;
    private final S3RequestValidator s3RequestValidator;
    private final WorkflowRepository workflowRepository;
    private final S3ReplicationConfigurator s3ReplicationConfigurator;

    private static final String CREATE_WORKFLOW_OPERATION = "createWorkflow";
    private static final String START_WORKFLOW_OPERATION = "startWorkflow";
    private static final String STOP_WORKFLOW_OPERATION = "stopWorkflow";
    private static final String DELETE_WORKFLOW_OPERATION = "deleteWorkflow";
    private static final String GET_WORKFLOW_OPERATION = "getWorkflow";
    private static final String SEND_CONTROL_COMMAND_OPERATION = "sendControlCommand";
    private static final String ELIGIBILITY_CHECK_OPERATION = "eligibilityCheck";

    /**
     * Injection of WorkflowManager.
     */
    @Inject
    public WorkflowActivity(final WorkflowManager workflowManager,
                            final InputValidator inputValidator,
                            final S3ClientFactory s3ClientFactory,
                            final S3RequestValidator s3RequestValidator,
                            final WorkflowRepository workflowRepository,
                            final S3ReplicationConfigurator s3ReplicationConfigurator) {
        this.s3RequestValidator = s3RequestValidator;
        this.workflowManager = workflowManager;
        this.inputValidator = inputValidator;
        this.s3ClientFactory = s3ClientFactory;
        this.workflowRepository = workflowRepository;
        this.s3ReplicationConfigurator = s3ReplicationConfigurator;
    }

    public EchoOutput echo(final EchoInput input) {
        log.info("Entered echo method");

        if (input == null || input.getString() == null) {
            throw new InvalidInputException("Invalid input!");
        }

        return EchoOutput.builder().withString(input.getString()).build();
    }

    /**
     * Handles the CreateWorkflow S3A API Operation.
     * Creates a new Workflow
     *
     * @param request CreateWorkflowRequest object
     * @return CreateWorkflowResponse object
     */
    public CreateWorkflowResponse createWorkflow(final CreateWorkflowRequest request) {

        if (request == null) {
            throw new InvalidInputException("Invalid input! Request is Null");
        }
        Workflow inputWorkflow = request.getWorkflow();
        final String workflowName = inputWorkflow.getWorkflowName();
        final String namespaceId = inputWorkflow.getNamespaceID();
        try (
            final S3Client sourceS3Client = createS3Client(inputWorkflow.getSourceRoleARN(),
                inputWorkflow.getSourceRegion());
            final S3Client destS3Client = createS3Client(inputWorkflow.getDestRoleARN(), inputWorkflow.getDestRegion());
            final S3ControlClient s3ControlClient = createS3ControlClient(inputWorkflow.getSourceRoleARN(),
                inputWorkflow.getSourceRegion());
        ) {
            //Basic sanity checks
            inputValidator.validateCreateWorkflowRequest(request);
            s3RequestValidator.validateMigrationRequest(inputWorkflow, sourceS3Client, destS3Client, s3ControlClient);

            log.info("Creating workflow with workflowName: {}, namespaceID: {}", workflowName, namespaceId);

            return workflowManager.createWorkflow(request);
        } catch (InvalidInputException | AlreadyExistsException | AWSServiceException e) {
            throw logAndRethrow(CREATE_WORKFLOW_OPERATION, workflowName, namespaceId, e);
        } catch (Exception e) {
            log.error("Unexpected error occurred during workflow creation. workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceId,
                e);
            throw new S3AInternalServiceException(e.getMessage(), e);
        }
    }


    /**
     * Handles the DeleteWorkflow S3A API Operation.
     *
     * @param request DeleteWorkflowRequest
     * @return empty structure
     */
    public DeleteWorkflowResponse deleteWorkflow(final DeleteWorkflowRequest request) {
        inputValidator.validateDeleteWorkflowRequest(request);
        final String workflowName = escapeJava(request.getWorkflowName());
        final String namespaceID = escapeJava(request.getNamespaceID());

        log.info("Deleting workflow {}", workflowName);

        DeleteWorkflowResponse deleteWorkflowResponse = null;

        try {
            deleteWorkflowResponse = workflowManager.deleteWorkflow(request);
        } catch (InvalidInputException | AlreadyExistsException | AWSServiceException e) {
            throw logAndRethrow(DELETE_WORKFLOW_OPERATION, workflowName, namespaceID, e);
        } catch (Exception exception) {
            log.error("Unexpected error occurred during workflow creation. workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceID,
                exception);
            throw new S3AInternalServiceException(exception.getMessage(), exception);
        }

        return deleteWorkflowResponse;
    }

    /**
     * Handles the StopWorkflow S3A API Operation.
     *
     * @param request StopWorkflowRequest
     * @return empty structure
     */
    public StopWorkflowResponse stopWorkflow(final StopWorkflowRequest request) {
        inputValidator.validateStopWorkflowRequest(request);
        final String workflowName = escapeJava(request.getWorkflowName());
        final String namespaceID = escapeJava(request.getNamespaceID());

        log.info("Stopping workflow with workflowName: {}, namespaceID: {}", workflowName, namespaceID);

        StopWorkflowResponse stopWorkflowResponse = null;

        try {
            stopWorkflowResponse = workflowManager.stopWorkflow(request);

            final WorkFlowModel workflowModel = workflowRepository.getWorkflow(workflowName, namespaceID);

            try (final S3ControlClient s3ControlClient =
                     s3ClientFactory.createS3ControlClient(workflowModel.getSourceRoleARN(),
                         workflowModel.getSourceRegion());
                 final S3Client s3Client =
                     s3ClientFactory.createS3Client(workflowModel.getSourceRoleARN(),
                         workflowModel.getSourceRegion())) {
                s3ReplicationConfigurator.batchUpdateJobStatus(
                    s3ControlClient, workflowModel, RequestedJobStatus.CANCELLED);
                s3ReplicationConfigurator.removeReplicationRule(s3Client, workflowModel);
            }
            log.info("Successfully initiated stop for workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceID);
        } catch (InvalidInputException | AlreadyExistsException | AWSServiceException exception) {
            throw logAndRethrow(STOP_WORKFLOW_OPERATION, workflowName, namespaceID, exception);
        } catch (Exception exception) {
            log.error("StopWorkflow failed for workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceID,
                exception);
            throw new S3AInternalServiceException(exception.getMessage(), exception);
        }

        return stopWorkflowResponse;
    }


    /**
     * Handles the StartWorkflow S3A API Operation.
     * Starts a Workflow
     *
     * @param request StartWorkflowRequest object
     * @return StartWorkflowResponse object
     */
    public StartWorkflowResponse startWorkflow(final StartWorkflowRequest request) {
        inputValidator.validateStartWorkflowRequest(request);
        final String workflowName = escapeJava(request.getWorkflowName());
        final String namespaceID = escapeJava(request.getNamespaceID());

        log.info("Starting workflow with workflowName: {}, namespaceID: {}", workflowName, namespaceID);

        try {
            workflowManager.startWorkflow(request, WorkflowStateMachine.WORKFLOW_STATE_MACHINE);
        } catch (InvalidInputException | AWSServiceException  e) {
            throw logAndRethrow(START_WORKFLOW_OPERATION, workflowName, namespaceID, e);
        } catch (RuntimeException e) {
            log.error("Unexpected error occurred while starting the workflow. workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceID,
                e);
            throw new S3AInternalServiceException(e.getMessage(), e);
        }

        return StartWorkflowResponse
            .builder()
            .build();
    }

        /**
     * Handles the StartWorkflow S3A API Operation.
     * Starts a Workflow
     *
     * @param request StartWorkflowRequest object
     * @return StartWorkflowResponse object
     */
    public StartWorkflowResponse startManifestSplitWorkflow(final StartWorkflowRequest request) {
        inputValidator.validateStartWorkflowRequest(request);
        final String workflowName = escapeJava(request.getWorkflowName());
        final String namespaceID = escapeJava(request.getNamespaceID());

        log.info("Starting workflow with workflowName: {}, namespaceID: {}", workflowName, namespaceID);

        try {
            workflowManager.startWorkflow(request, WorkflowStateMachine.MANIFEST_SPLIT_STATE_MACHINE);
        } catch (InvalidInputException | AWSServiceException  e) {
            throw logAndRethrow(START_WORKFLOW_OPERATION, workflowName, namespaceID, e);
        } catch (RuntimeException e) {
            log.error("Unexpected error occurred while starting the workflow. workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceID,
                e);
            throw new S3AInternalServiceException(e.getMessage(), e);
        }

        return StartWorkflowResponse
            .builder()
            .build();
    }

    /**
     * Handles the GetWorkflow S3A API Operation.
     * Fetches the latest Workflow metadata and returns it to the caller
     *
     * @param request GetWorkflowRequest
     * @return Workflow structure
     */
    public GetWorkflowResponse getWorkflow(final GetWorkflowRequest request) {
        inputValidator.validateGetWorkflowRequest(request);
        final String workflowName = escapeJava(request.getWorkflowName());
        final String namespaceID = escapeJava(request.getNamespaceID());

        log.info("Getting workflow with workflowName: {}, namespaceID: {}", workflowName, namespaceID);
        // 2024-09-09: We just call getWorkflow from WorkflowManager, it will return a proper response
        GetWorkflowResponse getWorkflowResponse = null;
        try {
            getWorkflowResponse = workflowManager.getWorkflow(request);
        } catch (InvalidInputException | AWSServiceException | EntityNotFoundException  e) {
            throw logAndRethrow(GET_WORKFLOW_OPERATION, workflowName, namespaceID, e);
        } catch (RuntimeException e) {
            log.error("Unexpected error occurred while starting the workflow. workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceID,
                e);
            throw new S3AInternalServiceException(e.getMessage(), e);
        }
        return getWorkflowResponse;
    }

    /**
     * Handles the ListWorkflows S3A API Operation.
     * Fetches the latest Workflows and returns it to the caller
     *
     * @return Workflow structure list
     */
    public ListWorkflowsResponse listWorkflows() {
        try {
            return workflowManager.listWorkflows();
        } catch (RuntimeException e) {
            log.error("Unexpected error occurred while starting the workflow.",
                e);
            throw new S3AInternalServiceException(e.getMessage(), e);
        }
    }

    /**
     * Handles the SendControlCommand S3A API Operation.
     * Sends a control command
     *
     * @param request SendControlCommandRequest object
     * @return SendControlCommandResponse object
     */
    public SendControlCommandResponse sendControlCommand(final SendControlCommandRequest request) {
        inputValidator.validateSendControlCommandRequest(request);
        final String workflowName = escapeJava(request.getWorkflowName());
        final String namespaceID = escapeJava(request.getNamespaceID());

        log.info("Sending control command to workflow with workflowName: {}, namespaceID: {}",
            workflowName, namespaceID);
        try {
            return workflowManager.sendControlCommand(request);
        } catch (InvalidInputException | AWSServiceException  e) {
            throw logAndRethrow(SEND_CONTROL_COMMAND_OPERATION, workflowName, namespaceID, e);
        } catch (RuntimeException e) {
            log.error("Unexpected error occurred for the operation: {} with workflowName: {}, namespaceID: {}",
                SEND_CONTROL_COMMAND_OPERATION,
                workflowName,
                namespaceID,
                e);
            throw new S3AInternalServiceException(e.getMessage(), e);
        }
    }

    /**
     * Handles the EligibilityCheck S3A API Operation.
     * Checks if the bucket is eligible for migration before we trigger a migration
     * @param request workflow payload
     * @return Either an exception or a success message.
     */
    public EligibilityCheckResponse eligibilityCheck(final EligibilityCheckRequest request) {
        inputValidator.validateEligibilityCheckRequest(request);
        Workflow inputWorkflow = request.getEligibilityCheckRequest();
        final String workflowName = inputWorkflow.getWorkflowName();
        final String namespaceId = inputWorkflow.getNamespaceID();

        log.info("Checking eligibility for listed bucket(s)");
        try (
            final S3Client sourceS3Client = createS3Client(inputWorkflow.getSourceRoleARN(),
                inputWorkflow.getSourceRegion());
            final S3Client destS3Client = createS3Client(inputWorkflow.getDestRoleARN(), inputWorkflow.getDestRegion());
            final S3ControlClient s3ControlClient = createS3ControlClient(inputWorkflow.getSourceRoleARN(),
                inputWorkflow.getSourceRegion())
        ) {

            s3RequestValidator.validateMigrationRequest(inputWorkflow, sourceS3Client, destS3Client, s3ControlClient);

            return EligibilityCheckResponse.builder()
                .withEligibilityCheckResponse("Bucket eligible for migration")
                .build();
        } catch (InvalidInputException | AlreadyExistsException | AWSServiceException e) {
            throw logAndRethrow(ELIGIBILITY_CHECK_OPERATION, workflowName, namespaceId, e);
        } catch (Exception e) {
            log.error("Unexpected error occurred during workflow creation. workflowName: {}, namespaceID: {}",
                workflowName,
                namespaceId,
                e);
            throw new S3AInternalServiceException(e.getMessage(), e);
        }
    }


    private S3Client createS3Client(final String roleARN, final String region) {
        return s3ClientFactory.createS3Client(roleARN, region);
    }

    private S3ControlClient createS3ControlClient(final String roleARN, final String region) {
        return s3ClientFactory.createS3ControlClient(roleARN, region);
    }

    /**
     * Logs the error with contextual information and returns the exception for rethrowing.
     *
     * @param operationType the type of operation being performed
     * @param workflowName  the name of the workflow involved
     * @param namespaceId   the namespace identifier
     * @param exception     the exception to log and rethrow
     * @param <T>           the type of RuntimeException
     * @return the same exception that was passed in, for rethrowing
     */
    private <T extends RuntimeException> T logAndRethrow(final String operationType, final String workflowName,
                                                         final String namespaceId, final T exception) {
        log.error("Exception occurred while processing: {} request for workflow: {}, "
                + "namespaceID: {} with message: {}",
            operationType,workflowName, namespaceId, exception);
        return exception;
    }
}
