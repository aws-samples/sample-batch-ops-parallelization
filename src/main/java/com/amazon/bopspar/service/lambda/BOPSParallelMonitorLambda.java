package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.RuntimeConfig;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.monitor.S3MonitorManager;
import com.amazon.bopspar.service.resources.monitor.S3MonitorManagerFactory;
import com.amazon.bopspar.service.resources.monitor.dashboard.CloudWatchDashboardManager;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import com.amazon.bopspar.service.responses.WorkflowResponseBuilder;
import com.amazon.bopspar.service.validator.WorkflowRequestValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3control.S3ControlClient;

import java.util.Optional;

/**
* S3A Monitoring Lambda.
*
*/
public class BOPSParallelMonitorLambda implements RequestHandler<WorkflowRequest, WorkflowResponse> {
    private static final Logger LOGGER = LogManager.getLogger(BOPSParallelMonitorLambda.class);
    private final WorkflowRepository workflowRepository;
    private final S3MonitorManagerFactory monitorManagerFactory;
    private final CloudWatchDashboardManager cloudWatchDashboardManager;
    private final S3ClientFactory s3ClientFactory;
    private final WorkflowStatusManager workflowStatusManager;
    private static final String BOPS_MONITOR_VERSION = "VERSION: [BOPS Parallel Monitor - v1.0.0.20251008-10:30]";
    private static final String CLOUDWATCH_ROLE_NAME = "s3a-cloudwatch-permissions";

    /**
     * Initializes CRUD layer and S3MonitorManagerFactory.
     */
    public BOPSParallelMonitorLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.monitorManagerFactory = lambdaComponent.getS3MonitorManagerFactory();
        this.cloudWatchDashboardManager = lambdaComponent.getCloudWatchDashboardManager();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
        this.workflowStatusManager = lambdaComponent.getWorkflowStatusManager();
    }

    // for unit testing
    BOPSParallelMonitorLambda(final WorkflowRepository workflowRepository,
                    final S3MonitorManagerFactory monitorManagerFactory,
                    final CloudWatchDashboardManager cloudWatchDashboardManager,
                    final S3ClientFactory s3ClientFactory,
                    final WorkflowStatusManager workflowStatusManager) {
        this.workflowRepository = workflowRepository;
        this.monitorManagerFactory = monitorManagerFactory;
        this.cloudWatchDashboardManager = cloudWatchDashboardManager;
        this.s3ClientFactory = s3ClientFactory;
        this.workflowStatusManager = workflowStatusManager;
    }

    /**
     * Handles S3A monitoring functionality.
     *  1. validate input
     *  2. Get workflow details from CRUD
     *  3. Use S3MonitorManager to perform the BOPS and CRR checks
     *  4. Update workflow details using CRUD
     *  5. Return a proper response (success/failure)
     * @param workflowRequest Contains workflow name and namespaceID
     * @return success/error response
     */
    @Override
    public WorkflowResponse handleRequest(final WorkflowRequest workflowRequest, final Context context) {
        LOGGER.info(BOPS_MONITOR_VERSION);
        // IMPORTANT: For the lambda to be able to make the S3 SDK calls, the lambda role needs iam:PassRole
        // on the source or target account roles
        // and the source/target roles need a policy to allow S3 and lambda ops with the lambda role as principal
        WorkflowRequestValidator.validateWorkflowRequest(workflowRequest);
        final String workflowName = workflowRequest.getWorkflowName();
        final String namespaceID = workflowRequest.getNamespaceID();

        //Check initial monitoring status
        WorkFlowModel workflowModel = workflowRepository.getWorkflow(workflowName, namespaceID);
        LOGGER.info("[WORKFLOW: {}] - Current workflow details: {}", workflowModel.getWorkflowName(),
            workflowModel.toString());

        if (!workflowStatusManager.shouldContinueMonitoring(workflowModel)) {
            return WorkflowResponseBuilder.buildSuccessResponse(workflowModel,
                    WorkflowStatus.valueOf(workflowModel.getStatus()));
        }

        try (
            // Build S3ControlClient using wf details retrieved for Source Region Bucket.
            final S3ControlClient s3ControlClient = s3ClientFactory.createS3ControlClient(
                workflowModel.getSourceRoleARN(),
                workflowModel.getSourceRegion());
            // Build CWClient using wf details retrieved for Dest Region Bucket.
            final CloudWatchClient cloudwatchClient = s3ClientFactory.createCloudwatchClient(
                String.format("arn:aws:iam::%s:role/%s", workflowModel.getDestAccountNumber(), CLOUDWATCH_ROLE_NAME),
                workflowModel.getDestRegion())
        ) {
            final S3MonitorManager monitorManager = monitorManagerFactory.create(workflowModel);

            monitorManager.getIndividualBopsJobDetails(s3ControlClient);
            monitorManager.calculateAggregateMonitoringDetails();
            monitorManager.calculateCrrMonitoringDetails(cloudwatchClient);

            LOGGER.info("S3A Monitor Details result: {}",
                    workflowModel.getMonitoringDetails().toString());
            //Publish CW metrics
            CloudWatchClient s3aCloudWatchClient = CloudWatchClient.builder().build();
            if (monitorManager.publishCWMetrics(s3aCloudWatchClient)) {
                monitorManager.createCloudWatchAlarm(s3aCloudWatchClient);
                final String dashboardUrl = cloudWatchDashboardManager.createCloudWatchDashboard(s3aCloudWatchClient,
                        workflowModel);
                if (dashboardUrl != null) {
                    workflowModel.setRuntimeConfig(
                            Optional.ofNullable(workflowModel.getRuntimeConfig())
                                    .map(RuntimeConfig::toBuilder)
                                    .orElse(RuntimeConfig.builder())
                                    .dashboardUrl(dashboardUrl)
                                    .build()
                    );
                }
            }

            LOGGER.info("Workflow details to persist: {}", workflowModel.toString());
            workflowRepository.updateWorkflow(workflowModel);

        } catch (Exception exception) {
            // Catch all - because most service exceptions in the monitor are not rethrown
            LOGGER.error("Error monitoring workflow: {}, namespaceId: {}, ERROR: {}",
                workflowName, namespaceID, exception);
            workflowModel.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowModel);
            // Send Failed Workflow Response
            return WorkflowResponseBuilder.buildRuntimeErrorResponse(workflowModel,
                    WorkflowStatus.FAILED,
                    new RuntimeException(exception));
        }

        return WorkflowResponseBuilder.buildSuccessResponse(workflowModel,
                WorkflowStatus.valueOf(workflowModel.getStatus()));
    }
}
