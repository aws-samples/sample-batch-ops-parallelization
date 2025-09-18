package com.amazon.tdm.s3a.service.resources.replication.bucket;

import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * This class is responsible for managing the versioning of S3 buckets.
 */
public class S3BucketVersioningManager {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(S3BucketVersioningManager.class);

    /**
     * This method enables versioning on the source bucket.
     * @param workflow is the workflow model
     * @param s3Client is the S3 client
     * @return the response from the S3 client
     */
    public PutBucketVersioningResponse enableBucketVersioning(final WorkFlowModel workflow, final S3Client s3Client) {
        try {
            // Log the start of the versioning process
            final String bucketArn = workflow.getSourceRoleARN();
            final String workflowName = workflow.getWorkflowName();

            log.info("Enabling versioning for source bucket: {} associated with workflow: {}",
                bucketArn, workflowName);

            // Call the S3 client's putBucketVersioning method
            final PutBucketVersioningResponse response =
                s3Client.putBucketVersioning(createEnableBucketVersioningRequest(workflow));

            // Log a successful response
            log.info("Versioning enabled successfully for bucket: {} associated with workflow",
                bucketArn, workflowName, response);

            return response;
        } catch (S3Exception s3Exception) {
            final String errorMessage = s3Exception.awsErrorDetails().errorMessage();
            log.error("S3Exception while enabling bucket versioning for workflowName: {}, namespaceID: {}",
                workflow.getWorkflowName(), workflow.getNamespaceID(), s3Exception);
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * This method creates the request to enable bucket versioning.
     * @param workflow is the workflow model
     * @return the request to enable bucket versioning
     */
    private PutBucketVersioningRequest createEnableBucketVersioningRequest(final WorkFlowModel workflow) {
        final PutBucketVersioningRequest request = PutBucketVersioningRequest.builder()
            .bucket(Arn.fromString(workflow.getSourceBucketARN()).resourceAsString())
            .versioningConfiguration(
                VersioningConfiguration.builder()
                    .status(BucketVersioningStatus.ENABLED).build())
            .build();
        log.info("PutBucketVersioningRequest: {}", request);
        return request;
    }
}
