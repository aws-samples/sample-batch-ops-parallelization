package com.amazon.tdm.s3a.service.lambda;

import com.amazon.tdm.s3a.service.dagger.DaggerLambdaComponent;
import com.amazon.tdm.s3a.service.dagger.LambdaComponent;
import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.persistence.model.RuntimeConfig;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig.S3ManifestSplitService;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowState;
import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import software.amazon.awssdk.services.glue.model.StartJobRunResponse;
import com.amazon.tdm.s3a.service.requests.OrcaRequest;
import com.amazon.tdm.s3a.service.responses.OrcaResponse;
import com.amazon.tdm.s3a.service.responses.OrcaResponseBuilder;
import com.amazon.tdm.s3a.service.validator.OrcaRequestValidator;
import com.amazon.tdm.s3a.service.validator.WorkflowValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.arns.Arn;

import java.util.Optional;



/**
 * Lambda to split generated inventory report manifest into multiple manifest files.
 */
public class S3ManifestSplitLambda implements RequestHandler<OrcaRequest, OrcaResponse> {
    private static final Logger LOGGER = LogManager.getLogger(S3ManifestSplitLambda.class);

    private static final String S3A_GLUE_JOB_ROLE_NAME = "s3a-cross-account-glue-job-role";

    private static final String INVENTORY_REPORT_CONFIG_SETUP_ROLE_NAME = "s3a-inventory-report-permissions";

    private final WorkflowRepository workflowRepository;
    private final S3ClientFactory s3ClientFactory;
    private final S3ManifestSplitService s3ManifestSplitService;

    public S3ManifestSplitLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
        this.s3ManifestSplitService = lambdaComponent.getS3ManifestSplitService();
    }

    // Unit testing
    S3ManifestSplitLambda(final WorkflowRepository workflowRepository,
                          final S3ClientFactory s3ClientFactory,
                          final S3ManifestSplitService s3ManifestSplitService) {
        this.workflowRepository = workflowRepository;
        this.s3ClientFactory = s3ClientFactory;
        this.s3ManifestSplitService = s3ManifestSplitService;
    }

    @Override
    public OrcaResponse handleRequest(final OrcaRequest orcaRequest, final Context context) {

        final String glueJobRoleArn = String.format(
                "arn:aws:iam::%s:role/%s",
                System.getenv("AWS_ACCOUNT_ID"),
                S3A_GLUE_JOB_ROLE_NAME
        );

        OrcaRequestValidator.validateOrcaRequest(orcaRequest);
        final String workflowName = orcaRequest.getWorkflowName();
        final String namespaceID = orcaRequest.getNamespaceID();
        LOGGER.info("Orca Request: {} {}", workflowName, namespaceID);

        final WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);
        WorkflowValidator.validateWorkflowArns(workflowDetails);

        final String inventoryConfigLambdaRole = String.format(
                "arn:aws:iam::%s:role/%s",
                workflowDetails.getSourceAccountNumber(),
                INVENTORY_REPORT_CONFIG_SETUP_ROLE_NAME
        );

        final String glueRegion = workflowDetails.getSourceRegion();

        validateWorkflowConfig(workflowDetails);

        final String inventoryReportBucketName = Arn.fromString(workflowDetails.getRuntimeConfig()
                .getInventoryReportBucketArn()).resourceAsString();

        final String sourceBucketName = Arn.fromString(workflowDetails.getSourceBucketARN()).resourceAsString();

        try (
                final GlueClient glueClient = s3ClientFactory.createGlueClient(
                        glueJobRoleArn,
                        glueRegion);

                final S3Client s3Client = s3ClientFactory.createS3Client(
                        inventoryConfigLambdaRole,
                        workflowDetails.getSourceRegion());

        ) {

            // Remove the the S3A-created inventory configuration form the source bucket for clean up.
            // All the configurations that start with S3A-as these are assumed to be S3A-created.
            s3ManifestSplitService.removeInventoryConfiguration(s3Client, workflowDetails, sourceBucketName);

            final StartJobRunResponse response = s3ManifestSplitService.splitManifest(workflowDetails,
                    glueClient, inventoryReportBucketName);

            workflowDetails.setRuntimeConfig(
                    Optional.ofNullable(workflowDetails.getRuntimeConfig())
                            .map(RuntimeConfig::toBuilder)
                            .orElse(RuntimeConfig.builder())
                            .inventoryReportGlueJobRunId(response.jobRunId())
                            .build()
            );
            LOGGER.info("Workflow details to persist: {}", workflowDetails.toString());
            workflowDetails.setState(WorkflowState.PROCESSING_INVENTORY.name());
            workflowRepository.updateWorkflow(workflowDetails);

        }
        catch (AwsServiceException exception) {
            LOGGER.error("Exception {} while splitting manifest for workflowName: {}, namespaceID: {}",
                    exception.getClass().getName(),
                    workflowName,
                    namespaceID,
                    exception);
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowDetails.setState(WorkflowState.PROCESS_INVENTORY_FAILED.name());
            workflowRepository.updateWorkflow(workflowDetails);
            return OrcaResponseBuilder.buildServiceErrorResponse(workflowDetails, WorkflowStatus.FAILED, exception);
        }
        catch (RuntimeException exception) {
            LOGGER.error("RuntimeException while splitting manifest for workflowName: {}, namespaceID: {}",
                    workflowName,
                    namespaceID,
                    exception);
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowDetails.setState(WorkflowState.PROCESS_INVENTORY_FAILED.name());
            workflowRepository.updateWorkflow(workflowDetails);
            return OrcaResponseBuilder.buildRuntimeErrorResponse(workflowDetails, WorkflowStatus.FAILED, exception);
        }

        // Send Success Orca Response
        return OrcaResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.RUNNING);

    }

    private void validateWorkflowConfig(final WorkFlowModel workflowDetails) {
        if (workflowDetails.getRuntimeConfig() == null) {
            throw new IllegalArgumentException("Runtime Config cannot be null or empty");
        }

        if (workflowDetails.getRuntimeConfig().getInventoryReportBucketArn() == null
                || workflowDetails.getRuntimeConfig().getInventoryReportBucketArn().trim().isEmpty()) {
            throw new IllegalArgumentException("Inventory report bucket ARN cannot be null or empty");
        }

        if (workflowDetails.getRuntimeConfig().getInventoryReportManifestLocation() == null
                || workflowDetails.getRuntimeConfig().getInventoryReportManifestLocation().trim().isEmpty()) {
            throw new IllegalArgumentException("Inventory report manifest location cannot be null or empty");
        }
    }
}
