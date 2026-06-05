package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.workflow.WorkflowState;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazon.bopspar.service.validator.WorkflowRequestValidator;
import com.amazon.bopspar.service.validator.WorkflowValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazon.bopspar.service.resources.s3.inventoryreportconfig.GlueJobStates;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.GetJobRunRequest;
import software.amazon.awssdk.services.glue.model.GetJobRunResponse;

import java.util.Set;

/**
 * Lambda to poll glue job state.
 */
public class S3PollGlueJobLambda implements RequestHandler<WorkflowRequest, WorkflowResponse> {
    private static final Logger LOGGER = LogManager.getLogger(S3PollGlueJobLambda.class);
    private final WorkflowRepository workflowRepository;
    private final S3ClientFactory s3ClientFactory;

    private static final String S3A_GLUE_JOB_NAME = "manifest-split-glue-job";
    private static final String S3A_GLUE_JOB_ROLE_NAME = "s3a-cross-account-glue-job-role";

    private static final Set<String> RUNNING_STATES = Set.of(
            GlueJobStates.GLUE_JOB_RUNNING,
            GlueJobStates.GLUE_JOB_WAITING
    );

    private static final Set<String> SUCCEEDED_STATES = Set.of(
            GlueJobStates.GLUE_JOB_SUCCEEDED
    );

    private static final Set<String> FAILED_STATES = Set.of(
            GlueJobStates.GLUE_JOB_FAILED,
            GlueJobStates.GLUE_JOB_ERROR,
            GlueJobStates.GLUE_JOB_TIMEOUT,
            GlueJobStates.GLUE_JOB_STOPPED
    );


    public S3PollGlueJobLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
    }

    // Unit testing
    S3PollGlueJobLambda(final WorkflowRepository workflowRepository,
                        final S3ClientFactory s3ClientFactory) {
        this.workflowRepository = workflowRepository;
        this.s3ClientFactory = s3ClientFactory;
    }

    @Override
    public WorkflowResponse handleRequest(final WorkflowRequest workflowRequest, final Context context) {
        WorkflowRequestValidator.validateWorkflowRequest(workflowRequest);
        final String workflowName = workflowRequest.getWorkflowName();
        final String namespaceID = workflowRequest.getNamespaceID();
        LOGGER.info("Workflow Request: {} {}", workflowName, namespaceID);

        //Get workflow details
        final WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);

        WorkflowValidator.validateWorkflowArns(workflowDetails);
        return processGlueJobStatus(workflowDetails);
    }

    private WorkflowResponse processGlueJobStatus(final WorkFlowModel workflowDetails) {

        final String glueJobRoleArn = String.format(
                "arn:aws:iam::%s:role/%s",
                System.getenv("AWS_ACCOUNT_ID"),
                S3A_GLUE_JOB_ROLE_NAME
        );

        try (
                final GlueClient glueClient = s3ClientFactory.createGlueClient(
                        glueJobRoleArn,
                        workflowDetails.getSourceRegion());

        ) {
            final String jobRunState = getGlueJobState(glueClient, workflowDetails);
            return handleJobRunState(jobRunState, workflowDetails);
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Error polling glue job state for workflow: {}, namespaceId: {}",
                    workflowDetails.getWorkflowName(), workflowDetails.getNamespaceID(), runtimeException);
            workflowDetails.setState(WorkflowState.PROCESS_INVENTORY_FAILED.name());
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);

            return WorkflowResponseBuilder.buildRuntimeErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, runtimeException);
        }
    }

    private String getGlueJobState(final GlueClient glueClient, final WorkFlowModel workflowDetails) {
        final GetJobRunRequest getJobRunRequest = GetJobRunRequest.builder()
                .jobName(S3A_GLUE_JOB_NAME)
                .runId(workflowDetails.getRuntimeConfig().getInventoryReportGlueJobRunId())
                .build();
        final GetJobRunResponse jobRunResponse = glueClient.getJobRun(getJobRunRequest);
        return jobRunResponse.jobRun().jobRunState().toString();
    }

    private WorkflowResponse handleJobRunState(final String jobRunState, final WorkFlowModel workflowDetails) {
        final String jobRunId = workflowDetails.getRuntimeConfig().getInventoryReportGlueJobRunId();

        if (RUNNING_STATES.contains(jobRunState)) {
            LOGGER.info("Job Run state for job id: {} is state: {}", jobRunId, jobRunState);
            return WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.RUNNING);
        } else if (SUCCEEDED_STATES.contains(jobRunState)) {
            LOGGER.info("Job Run state for job id: {} is state: {}", jobRunId, jobRunState);
            return WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
        } else if (FAILED_STATES.contains(jobRunState)) {
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowDetails.setState(WorkflowState.PROCESS_INVENTORY_FAILED.toString());
            workflowRepository.updateWorkflow(workflowDetails);
            return WorkflowResponseBuilder.buildRuntimeErrorResponse(workflowDetails, WorkflowStatus.FAILED,
                    new RuntimeException("Glue job failed to process manifest"));
        }
        return WorkflowResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
    }
}
