package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.service.dagger.DaggerLambdaComponent;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.dagger.LambdaComponent;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.bopspar.service.resources.s3.inventoryreportconfig.S3InventoryReportConfigService;
import com.amazon.bopspar.service.resources.workflow.WorkflowState;
import com.amazon.bopspar.service.requests.OrcaRequest;
import com.amazon.bopspar.service.responses.OrcaResponse;
import com.amazon.bopspar.service.responses.OrcaResponseBuilder;
import com.amazon.bopspar.service.validator.OrcaRequestValidator;
import com.amazon.bopspar.service.validator.WorkflowValidator;
import com.amazonaws.arn.Arn;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.Optional;

/**
 * Handles the lambda request to poll for S3 inventory manifest files.
 */
public class S3PollManifestLambda implements RequestHandler<OrcaRequest, OrcaResponse> {

    private static final Logger LOGGER = LogManager.getLogger(S3PollManifestLambda.class);
    private static final String S3A_GLUE_JOB_ROLE_NAME = "s3a-cross-account-glue-job-role";
    private static final String INVENTORY_REPORTS_FOLDER_PREFIX = "S3A-";
    private static final String MANIFEST_FILE_NAME = "manifest.json";

    private final WorkflowRepository workflowRepository;
    private final S3CreateBucketService s3CreateBucketService;
    private final S3InventoryReportConfigService s3InventoryReportConfigService;
    private final S3ClientFactory s3ClientFactory;

    public S3PollManifestLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.s3CreateBucketService = lambdaComponent.getS3CreateBucketService();
        this.s3InventoryReportConfigService = lambdaComponent.getS3InventoryReportConfigService();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
    }

    //for unit testing
    S3PollManifestLambda(final WorkflowRepository workflowRepository,
                         final S3CreateBucketService s3CreateBucketService,
                         final S3InventoryReportConfigService s3InventoryReportConfigService,
                         final S3ClientFactory s3ClientFactory) {
        this.workflowRepository = workflowRepository;
        this.s3CreateBucketService = s3CreateBucketService;
        this.s3InventoryReportConfigService = s3InventoryReportConfigService;
        this.s3ClientFactory = s3ClientFactory;
    }

    @Override
    public OrcaResponse handleRequest(final OrcaRequest orcaRequest, final Context context) {
        OrcaRequestValidator.validateOrcaRequest(orcaRequest);
        final String workflowName = orcaRequest.getWorkflowName();
        final String namespaceID = orcaRequest.getNamespaceID();
        LOGGER.info("Orca Request: {} {}", workflowName, namespaceID);

        final WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);
        WorkflowValidator.validateWorkflowArns(workflowDetails);

        final String inventoryReportBucketArn = workflowDetails.getRuntimeConfig().getInventoryReportBucketArn();
        final String inventoryReportBucketName = Arn.fromString(inventoryReportBucketArn).getResourceAsString();

        final String glueJobRoleArn = String.format(
            "arn:aws:iam::%s:role/%s",
            System.getenv("AWS_ACCOUNT_ID"),
            S3A_GLUE_JOB_ROLE_NAME
        );

        try (
            final S3Client sourceRegionS3Client = s3ClientFactory.createS3Client(
                glueJobRoleArn,
                workflowDetails.getSourceRegion());
        ) {
            ListObjectsResponse listResponse = sourceRegionS3Client.listObjects(ListObjectsRequest.builder()
                .bucket(inventoryReportBucketName)
                .prefix(INVENTORY_REPORTS_FOLDER_PREFIX)
                .build()
            );

            if (findManifestAndUpdateStatus(listResponse, workflowDetails, inventoryReportBucketName)) {
                return OrcaResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
            } else {
                return OrcaResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.RUNNING);
            }
        } catch (AwsServiceException awsServiceException) {
            LOGGER.error("Exception {} polling inventory reports bucket for workflow: {}, namespaceId: {}",
                    awsServiceException.getClass().getName(), workflowName, namespaceID, awsServiceException);
            workflowDetails.setState(WorkflowState.AWAIT_MANIFEST_FAILED.name());
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            // Send Failed Orca Response
            return OrcaResponseBuilder.buildServiceErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, awsServiceException);
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Error polling inventory reports bucket for workflow: {}, namespaceId: {}",
                    workflowName, namespaceID, runtimeException);
            workflowDetails.setState(WorkflowState.AWAIT_MANIFEST_FAILED.name());
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            // Send Failed Orca Response
            return OrcaResponseBuilder.buildRuntimeErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, runtimeException);
        }
    }

    private String buildS3FolderPath(final String manifestKey, final String inventoryReportBucketName) {
        String folderPath = manifestKey.substring(0, manifestKey.lastIndexOf("/") + 1);
        return "s3://" + inventoryReportBucketName + "/" + folderPath;
    }

    private boolean findManifestAndUpdateStatus(final ListObjectsResponse listResponse,
                                                  final WorkFlowModel workflowDetails,
                                                  final String inventoryReportBucketName) {
        if (listResponse.contents() == null || listResponse.contents().isEmpty()) {
            return false;
        }
        Optional<S3Object> manifestFile = listResponse.contents().stream()
                .filter(obj -> obj.key().endsWith(MANIFEST_FILE_NAME))
                .findFirst();
        if (manifestFile.isPresent()) {
            final String fullS3FolderPath = buildS3FolderPath(manifestFile.get().key(), inventoryReportBucketName);
            workflowDetails.getRuntimeConfig().setInventoryReportManifestLocation(fullS3FolderPath);
            workflowDetails.setState(WorkflowState.PROCESSING_INVENTORY.toString());
            workflowRepository.updateWorkflow(workflowDetails);
            return true;
        } else {
            return false;
        }
    }
}
