package com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.s3.model.ListBucketInventoryConfigurationsResponse;
import software.amazon.awssdk.services.s3.model.ListBucketInventoryConfigurationsRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketInventoryConfigurationRequest;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import software.amazon.awssdk.services.s3.S3Client;
import com.amazon.tdm.s3a.model.AWSServiceException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.glue.model.StartJobRunRequest;
import software.amazon.awssdk.services.glue.model.StartJobRunResponse;

import java.util.Map;


/**
 * Service class responsible for handling S3 manifest splitting operations and inventory configuration management.
 */
public class S3ManifestSplitService {

    private static final String INVENTORY_CONFIG_PREFIX = "S3A-";
    private static final String S3A_GLUE_JOB_NAME = "manifest-split-glue-job";

    private static final Logger LOGGER = LogManager.getLogger(S3ManifestSplitService.class);

    /**
     * Splits a manifest file by initiating a Glue job with specified parameters.
     *
     * @param workflowDetails The workflow model
     * @param glueClient The AWS Glue client to execute the job
     * @param bucketName The name of the S3 bucket containing the manifest
     * @return StartJobRunResponse The response from the Glue job execution
     * @throws AWSServiceException if there's an error during manifest splitting
     */
    public StartJobRunResponse splitManifest(final WorkFlowModel workflowDetails,
                                             final GlueClient glueClient,
                                             final String bucketName) {
        try {
            final Map<String, String> jobArguments = Map.of(
                    "--workflow_name", workflowDetails.getWorkflowName(),
                    "--workflow_namespace_id", workflowDetails.getNamespaceID(),
                    "--inventory_bucket", bucketName,
                    "--inventory_report_manifest_location",
                        workflowDetails.getRuntimeConfig().getInventoryReportManifestLocation()
            );

            final StartJobRunRequest runRequest = StartJobRunRequest.builder()
                    .arguments(jobArguments)
                    .jobName(S3A_GLUE_JOB_NAME)
                    .build();

            LOGGER.info("Starting Glue job with arguments: {}", jobArguments);

            return glueClient.startJobRun(runRequest);

        } catch (S3Exception e) {
            LOGGER.error("Error splitting manifest", e);
            throw new AWSServiceException("Error splitting manifest", e);
        }
    }

    /**
     * Removes inventory configuration from an S3 bucket that match the specified prefix.
     * This method lists all inventory configurations and removes those that start with the
     * INVENTORY_CONFIG_PREFIX.
     *
     * @param s3Client The AWS S3 client to perform operations
     * @param workflowDetails The workflow model
     * @param bucketName The name of the S3 bucket to remove inventory configuration from
     * @throws AWSServiceException if there's an error removing inventory configuration
     */
    public void removeInventoryConfiguration(final S3Client s3Client,
                                             final WorkFlowModel workflowDetails,
                                             final String bucketName) {

        final ListBucketInventoryConfigurationsRequest listRequest = ListBucketInventoryConfigurationsRequest.builder()
                .bucket(bucketName)
                .build();

        final ListBucketInventoryConfigurationsResponse listResponse =
                s3Client.listBucketInventoryConfigurations(listRequest);
        LOGGER.info("inventory confgirurations for {} : {}", bucketName, listResponse.inventoryConfigurationList());


        if (listResponse.inventoryConfigurationList() != null || !listResponse.inventoryConfigurationList().isEmpty()) {
            listResponse.inventoryConfigurationList().stream()
                    .filter(config -> config.id().startsWith(INVENTORY_CONFIG_PREFIX))
                    .forEach(config -> {
                        DeleteBucketInventoryConfigurationRequest deleteRequest =
                                DeleteBucketInventoryConfigurationRequest.builder()
                                        .bucket(bucketName)
                                        .id(config.id())
                                        .build();
                        try {
                            s3Client.deleteBucketInventoryConfiguration(deleteRequest);
                            LOGGER.info("Successfully removed inventory configuration with ID {} from bucket {}",
                                    config.id(), bucketName);
                        } catch (S3Exception e) {
                            LOGGER.error("Failed to remove inventory configuration with ID {} from bucket {}: {}",
                                    config.id(), bucketName, e.getMessage());
                            throw new AWSServiceException("Failed to remove inventory configuration", e);
                        }
                    });
        }
    }

}
