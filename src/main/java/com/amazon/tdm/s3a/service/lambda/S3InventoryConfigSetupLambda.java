package com.amazon.tdm.s3a.service.lambda;

import com.amazon.tdm.s3a.persistence.manager.WorkflowStatus;
import com.amazon.tdm.s3a.persistence.model.RuntimeConfig;
import com.amazon.tdm.s3a.service.dagger.DaggerLambdaComponent;
import com.amazon.tdm.s3a.service.dagger.LambdaComponent;
import com.amazon.tdm.s3a.service.requests.OrcaRequest;
import com.amazon.tdm.s3a.service.responses.OrcaResponse;
import com.amazon.tdm.s3a.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig.S3InventoryReportConfigServiceUtils;
import com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig.S3InventoryReportConfigService;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowState;
import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.validator.OrcaRequestValidator;
import com.amazon.tdm.s3a.service.validator.WorkflowValidator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazon.tdm.s3a.service.responses.OrcaResponseBuilder;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.policybuilder.iam.IamStatement;
import software.amazon.awssdk.policybuilder.iam.IamEffect;
import software.amazon.awssdk.policybuilder.iam.IamPrincipal;
import software.amazon.awssdk.policybuilder.iam.IamPrincipalType;
import software.amazon.awssdk.policybuilder.iam.IamAction;
import software.amazon.awssdk.policybuilder.iam.IamResource;
import software.amazon.awssdk.policybuilder.iam.IamConditionOperator;
import software.amazon.awssdk.policybuilder.iam.IamConditionKey;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Lambda handler for setting up S3 inventory report configuration on customer source buckets.
 * This class implements the AWS Lambda RequestHandler interface to process OrcaRequest events.
 */
public class S3InventoryConfigSetupLambda implements RequestHandler<OrcaRequest, OrcaResponse> {

    private static final Logger LOGGER = LogManager.getLogger(S3InventoryConfigSetupLambda.class);
    private static final String S3_INVENTORY_REPORTS_BUCKET_NAME_PREFIX = "s3a-migration-reports-bucket";
    private static final long ONE_BILLION = 1_000_000_000L;
    private static final String INVENTORY_REPORT_CONFIG_SETUP_ROLE_NAME = "s3a-inventory-report-permissions";
    private static final String CLOUDWATCH_ROLE_NAME = "s3a-cloudwatch-permissions";
    private static final String S3A_GLUE_JOB_ROLE_NAME = "s3a-cross-account-glue-job-role";

    private final WorkflowRepository workflowRepository;
    private final S3CreateBucketService s3CreateBucketService;
    private final S3InventoryReportConfigService s3InventoryReportConfigService;
    private final S3ClientFactory  s3ClientFactory;

    public S3InventoryConfigSetupLambda() {
        LambdaComponent lambdaComponent = DaggerLambdaComponent.create();
        this.workflowRepository = lambdaComponent.getWorkflowRepository();
        this.s3CreateBucketService = lambdaComponent.getS3CreateBucketService();
        this.s3InventoryReportConfigService = lambdaComponent.getS3InventoryReportConfigService();
        this.s3ClientFactory = lambdaComponent.getS3ClientFactory();
    }

    //for unit testing
    S3InventoryConfigSetupLambda(final WorkflowRepository workflowRepository,
                         final S3CreateBucketService s3CreateBucketService,
                         final S3InventoryReportConfigService s3InventoryReportConfigService,
                         final S3ClientFactory s3ClientFactory) {
        this.workflowRepository = workflowRepository;
        this.s3CreateBucketService = s3CreateBucketService;
        this.s3InventoryReportConfigService = s3InventoryReportConfigService;
        this.s3ClientFactory = s3ClientFactory;
    }

    /**
     *  Handles creation of S3 inventory report config for customer source bucket
     *  First it will check the number of objects in the source bucket using CloudWatch.
     *  Then we have 2 scenarios:
     *  1) If the number of objects is less than 1 billion than we return FINISHED status to the Orca workflow
     *  and continue to the normal workflow.
     *  2) If the number of objects is more than 1 billion,then we create the inventory reports bucket and attach
     *  the bucket policy to the bucket for the inventory report config and the glue job role.
     *  Then we return RUNNING status to the Orca workflow.
     * @param orcaRequest Contains workflow name and namespaceID
     * @return OrcaResponse success/failure message
     */
    @Override
    public OrcaResponse handleRequest(final OrcaRequest orcaRequest, final Context context) {
        OrcaRequestValidator.validateOrcaRequest(orcaRequest);
        final String workflowName = orcaRequest.getWorkflowName();
        final String namespaceID = orcaRequest.getNamespaceID();
        LOGGER.info("Orca Request: {} {}", workflowName, namespaceID);

        WorkFlowModel workflowDetails = workflowRepository.getWorkflow(workflowName, namespaceID);
        WorkflowValidator.validateWorkflowArns(workflowDetails);

        final String sourceBucketArn = workflowDetails.getSourceBucketARN();
        final String inventoryConfigLambdaRole = String.format(
                "arn:aws:iam::%s:role/%s",
                workflowDetails.getSourceAccountNumber(),
                INVENTORY_REPORT_CONFIG_SETUP_ROLE_NAME
        );
        final String cloudWatchRoleArn = String.format(
                "arn:aws:iam::%s:role/%s",
                workflowDetails.getSourceAccountNumber(),
                CLOUDWATCH_ROLE_NAME
        );

        try (
                // The source bucket and the inventory reports bucket should exist in the same region.
                final S3Client sourceRegionS3Client = s3ClientFactory.createS3Client(
                        inventoryConfigLambdaRole,
                        workflowDetails.getSourceRegion());

                final CloudWatchClient sourceRegionCloudWatchClient = s3ClientFactory.createCloudwatchClient(
                        cloudWatchRoleArn,
                        workflowDetails.getSourceRegion());
        ) {
            final long numberOfObjectsInSourceBucket = s3InventoryReportConfigService
                    .getNumberOfObjectsForBucketViaCloudWatch(sourceRegionCloudWatchClient, sourceBucketArn);

            if (numberOfObjectsInSourceBucket < ONE_BILLION) {
                return OrcaResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.FINISHED);
            }

            final String glueJobRoleArn = String.format(
                    "arn:aws:iam::%s:role/%s",
                    System.getenv("AWS_ACCOUNT_ID"),
                    S3A_GLUE_JOB_ROLE_NAME
            );

            final String inventoryReportBucketName =
                    s3CreateBucketService.generateBucketName(S3_INVENTORY_REPORTS_BUCKET_NAME_PREFIX);
            final String inventoryReportBucketArn = String.format("arn:aws:s3:::%s", inventoryReportBucketName);

            setupInventoryConfigOnSourceBucket(
                    sourceRegionS3Client,
                    workflowDetails,
                    inventoryConfigLambdaRole,
                    glueJobRoleArn,
                    sourceBucketArn,
                    inventoryReportBucketName,
                    inventoryReportBucketArn
            );

            if (workflowDetails.getRuntimeConfig() == null) {
                RuntimeConfig runtimeConfig = RuntimeConfig.builder().build();
                workflowDetails.setRuntimeConfig(runtimeConfig);
            }

            workflowDetails.getRuntimeConfig().setInventoryReportBucketArn(inventoryReportBucketArn);
            workflowDetails.setState(WorkflowState.CONFIGURING_INVENTORY.name());
            workflowRepository.updateWorkflow(workflowDetails);

            return OrcaResponseBuilder.buildSuccessResponse(workflowDetails, WorkflowStatus.RUNNING);
        } catch (AwsServiceException awsServiceException) {
            LOGGER.error("Exception {} setting up inventory config for workflow: {}, namespaceId: {}",
                    awsServiceException.getClass().getName(), workflowName, namespaceID, awsServiceException);
            workflowDetails.setState(WorkflowState.CONFIGURE_INVENTORY_FAILED.name());
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            // Send Failed Orca Response
            return OrcaResponseBuilder.buildServiceErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, awsServiceException);
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Error setting up inventory config for workflow: {}, namespaceId: {}",
                    workflowName, namespaceID, runtimeException);
            workflowDetails.setState(WorkflowState.CONFIGURE_INVENTORY_FAILED.name());
            workflowDetails.setStatus(String.valueOf(WorkflowStatus.FAILED));
            workflowRepository.updateWorkflow(workflowDetails);
            // Send Failed Orca Response
            return OrcaResponseBuilder.buildRuntimeErrorResponse(workflowDetails,
                    WorkflowStatus.FAILED, runtimeException);
        }
    }

    /**
     * Sets up the inventory configuration on the source bucket and creates necessary resources.
     *
     * @param sourceRegionS3Client S3 client for the source region
     * @param workflowDetails Workflow model containing configuration details
     * @param inventoryConfigLambdaRole IAM role ARN for inventory configuration
     * @param glueJobRoleArn IAM role ARN for Glue job
     * @param sourceBucketArn ARN of the source bucket
     * @param inventoryReportBucketName Name of the bucket to store inventory reports
     * @param inventoryReportBucketArn ARN of the inventory reports bucket
     */
    private void setupInventoryConfigOnSourceBucket(
            final S3Client sourceRegionS3Client,
            final WorkFlowModel workflowDetails,
            final String inventoryConfigLambdaRole,
            final String glueJobRoleArn,
            final String sourceBucketArn,
            final String inventoryReportBucketName,
            final String inventoryReportBucketArn
    ) {
        s3CreateBucketService.createS3Bucket(sourceRegionS3Client,
                inventoryConfigLambdaRole,
                workflowDetails.getSourceRegion(),
                inventoryReportBucketName);
        IamStatement inventoryReportConfigDestinationBucketStatement =
                getInventoryReportConfigDestinationBucketStatement(
                        sourceBucketArn,
                        inventoryReportBucketArn,
                        workflowDetails.getSourceAccountNumber()
                );
        s3InventoryReportConfigService.addBucketStatementToBucketPolicy(sourceRegionS3Client,
                inventoryReportBucketArn,
                inventoryReportConfigDestinationBucketStatement);
        s3InventoryReportConfigService.addBucketStatementToBucketPolicy(sourceRegionS3Client,
                inventoryReportBucketArn,
                getGlueJobPolicyStatement(inventoryReportBucketArn, glueJobRoleArn));
        s3InventoryReportConfigService.createS3InventoryReportConfig(sourceRegionS3Client,
                sourceBucketArn,
                inventoryReportBucketArn,
                S3InventoryReportConfigServiceUtils.generateInventoryConfigPath(
                        sourceBucketArn,
                        workflowDetails.getSourceRegion()
                )
        );
    }

    /**
     * Creates an IAM policy statement for the inventory report configuration destination bucket.
     * This statement allows S3 to write inventory reports to the destination bucket.
     *
     * @param sourceBucketArn ARN of the source bucket
     * @param inventoryReportBucketArn ARN of the inventory reports bucket
     * @param sourceAccountNumber AWS account number of the source bucket
     * @return IamStatement policy statement for inventory report configuration
     */
    private IamStatement getInventoryReportConfigDestinationBucketStatement(
            final String sourceBucketArn,
            final String inventoryReportBucketArn,
            final String sourceAccountNumber
    ) {
        return IamStatement.builder()
                .effect(IamEffect.ALLOW)
                .sid("S3AMigrationInventoryReportsPolicy")
                .addPrincipal(IamPrincipal
                        .create(IamPrincipalType.SERVICE, "s3.amazonaws.com"))
                .addAction(IamAction.create("s3:PutObject"))
                .addResource(IamResource.create(inventoryReportBucketArn + "/*"))
                .addCondition(
                        IamConditionOperator.STRING_EQUALS,
                        IamConditionKey.create("aws:SourceAccount"),
                        sourceAccountNumber
                )
                .addCondition(
                        IamConditionOperator.STRING_EQUALS,
                        IamConditionKey.create("s3:x-amz-acl"),
                        "bucket-owner-full-control"
                )
                .addCondition(
                        IamConditionOperator.ARN_LIKE,
                        IamConditionKey.create("aws:SourceArn"),
                        sourceBucketArn
                )
                .build();
    }

    /**
     * Creates an IAM policy statement for the Glue job to access the inventory reports bucket.
     * This statement grants necessary S3 permissions to the Glue job role.
     *
     * @param inventoryReportBucketArn ARN of the inventory reports bucket
     * @param glueJobRoleArn ARN of the Glue job role
     * @return IamStatement policy statement for Glue job access
     */
    private IamStatement getGlueJobPolicyStatement(final String inventoryReportBucketArn, final String glueJobRoleArn) {
        return IamStatement.builder()
                .effect(IamEffect.ALLOW)
                .sid("S3AMigrationCrossAccountGlueJobStatement")
                .addPrincipal(IamPrincipal.create(IamPrincipalType.AWS, glueJobRoleArn))
                .addAction(IamAction.create("s3:GetObject"))
                .addAction(IamAction.create("s3:PutObject"))
                .addAction(IamAction.create("s3:ListBucket"))
                .addAction(IamAction.create("s3:DeleteObject"))
                .addAction(IamAction.create("s3:GetBucketPolicy"))
                .addAction(IamAction.create("s3:PutBucketPolicy"))
                .addAction(IamAction.create("s3:DeleteBucketPolicy"))
                .addResource(IamResource.create(inventoryReportBucketArn))
                .addResource(IamResource.create(inventoryReportBucketArn + "/*"))
                .build();
    }
}
