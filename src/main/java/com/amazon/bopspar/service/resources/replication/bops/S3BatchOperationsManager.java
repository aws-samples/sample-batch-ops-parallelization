package com.amazon.bopspar.service.resources.replication.bops;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.CreateJobRequest;
import software.amazon.awssdk.services.s3control.model.CreateJobResponse;
import software.amazon.awssdk.services.s3control.model.JobManifest;
import software.amazon.awssdk.services.s3control.model.JobManifestFormat;
import software.amazon.awssdk.services.s3control.model.JobManifestGenerator;
import software.amazon.awssdk.services.s3control.model.JobManifestGeneratorFilter;
import software.amazon.awssdk.services.s3control.model.JobManifestLocation;
import software.amazon.awssdk.services.s3control.model.JobManifestSpec;
import software.amazon.awssdk.services.s3control.model.JobOperation;
import software.amazon.awssdk.services.s3control.model.JobReport;
import software.amazon.awssdk.services.s3control.model.JobReportScope;
import software.amazon.awssdk.services.s3control.model.RequestedJobStatus;
import software.amazon.awssdk.services.s3control.model.S3ControlException;
import software.amazon.awssdk.services.s3control.model.S3JobManifestGenerator;
import software.amazon.awssdk.services.s3control.model.S3ReplicateObjectOperation;
import software.amazon.awssdk.services.s3control.model.UpdateJobStatusRequest;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.amazon.bopspar.service.resources.replication.ReplicationConstants.JOB_REPORT_FORMAT;


/**
 * S3A Batch Operations Manager.
 * This class is responsible for managing batch operations for S3A.
 */
public class S3BatchOperationsManager {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(S3BatchOperationsManager.class);

    private static final int jobPriority = 0;

    /**
     * Creates a batch replication job request for the given workflow model and BOPS role.
     * @param workflowModel The workflow object
     * @param bopsRole The BOPS role ARN to be used for the batch replication job
     * @return CreateJobRequest
     */
    private CreateJobRequest createBatchReplicationJobRequest(final WorkFlowModel workflowModel,
                                                              final String bopsRole,
                                                              final boolean isMoreThan1b) {
        String srcBucketARN = workflowModel.getSourceBucketARN();
        String destinationBucketARN = workflowModel.getDestBucketARN();
        String workflowName = workflowModel.getWorkflowName();
        String jobDescription =
            String.format("Replicate objects for workflowName: %s, srcBucketARN: %s, destinationBucketARN: %s",
                workflowName, srcBucketARN, destinationBucketARN);
        jobDescription = jobDescription.length() > 255 ? jobDescription.substring(0, 255) : jobDescription;
        String jobReportPrefix = String.format("batch-job-report-%s",workflowName);

        String jobReportBucketARN = workflowModel.getJobReportBucketARN();

        JobOperation jobOperation = JobOperation.builder()
            .s3ReplicateObject(S3ReplicateObjectOperation.builder().build())
            .build();

        JobManifestGeneratorFilter.Builder jobManifestGeneratorFilterBuilder = JobManifestGeneratorFilter.builder()
                .eligibleForReplication(true);

        if (isMoreThan1b && workflowModel.getRuntimeConfig() != null
                && workflowModel.getRuntimeConfig().getManifestLocation() != null
                && workflowModel.getRuntimeConfig().getInventoryReportConfigStartedAt() != null) {
            Instant migrationStartTime = Instant.parse(
                    workflowModel.getRuntimeConfig().getInventoryReportConfigStartedAt()
            );
            jobManifestGeneratorFilterBuilder
                    .createdAfter(migrationStartTime.minus(3, ChronoUnit.DAYS))
                    .createdBefore(migrationStartTime.plus(1, ChronoUnit.DAYS));
        }
        JobManifestGeneratorFilter jobManifestGeneratorFilter = jobManifestGeneratorFilterBuilder.build();

        S3JobManifestGenerator s3JobManifestGenerator = S3JobManifestGenerator.builder()
            .enableManifestOutput(false)
            .filter(jobManifestGeneratorFilter)
            .sourceBucket(srcBucketARN)
            .build();

        JobManifestGenerator jobManifestGenerator = JobManifestGenerator.builder()
            .s3JobManifestGenerator(s3JobManifestGenerator)
            .build();

        JobReport jobReport = JobReport.builder()
            .bucket(jobReportBucketARN)
            .prefix(jobReportPrefix)
            .format(JOB_REPORT_FORMAT)
            .enabled(true)
            .reportScope(JobReportScope.FAILED_TASKS_ONLY)
            .build();

        return CreateJobRequest.builder()
            .accountId(workflowModel.getSourceAccountNumber())
            .operation(jobOperation)
            .manifestGenerator(jobManifestGenerator)
            .priority(jobPriority)
            .roleArn(bopsRole)
            .description(jobDescription)
            .confirmationRequired(false)
            .report(jobReport)
            .build();
    }

    /**
     * Creates a batch replication job request with the given manifest file metadata.
     *
     * @param s3Client The S3 client to be used for S3 operations
     * @param workflowModel The workflow object
     * @param manifestBucket The manifest bucket to be used for S3 operations
     * @param manifestMetadata The manifest file metadata to be used for S3 operations
     * @param bopsRole The BOPS role ARN to be used for the batch replication job
     * @return CreateJobRequest The created batch replication job request
     */
    private CreateJobRequest createBatchReplicationJobRequest(final S3Client s3Client,
                                                              final WorkFlowModel workflowModel,
                                                              final String manifestBucket,
                                                              final ManifestFileMetadata manifestMetadata,
                                                              final String bopsRole) {
        String srcBucketARN = workflowModel.getSourceBucketARN();
        String destinationBucketARN = workflowModel.getDestBucketARN();
        String workflowName = workflowModel.getWorkflowName();

        String jobDescription =
            String.format("[MF] Replicate objects for workflowName: %s, srcBucketARN: %s, destinationBucketARN: %s",
                workflowName, srcBucketARN, destinationBucketARN);
        jobDescription = jobDescription.length() > 255 ? jobDescription.substring(0, 255) : jobDescription;
        String jobReportPrefix = String.format("batch-job-report-%s",workflowName);

        String jobReportBucketARN = workflowModel.getJobReportBucketARN();

        JobOperation jobOperation = JobOperation.builder()
            .s3ReplicateObject(S3ReplicateObjectOperation.builder().build())
            .build();

        String manifestKey = manifestMetadata.getKey();
        String etag = manifestMetadata.getEtag();
        String objectArn = String.format("arn:aws:s3:::%s/%s", manifestBucket, manifestKey);

        JobReport jobReport = JobReport.builder()
            .bucket(jobReportBucketARN)
            .prefix(jobReportPrefix)
            .format(JOB_REPORT_FORMAT)
            .enabled(true)
            .reportScope(JobReportScope.FAILED_TASKS_ONLY)
            .build();
        log.info("Manifest object ARN: {} - etag: {}", objectArn, etag);

        JobManifest jobManifest = JobManifest.builder()
            .spec(JobManifestSpec.builder()
                .format(JobManifestFormat.S3_BATCH_OPERATIONS_CSV_20180820)
                .fieldsWithStrings(Arrays.asList("Bucket", "Key", "VersionId"))
                .build())
            .location(JobManifestLocation.builder()
                .eTag(etag)
                .objectArn(objectArn)
                .build())
            .build();
        log.info("Job Manifest: {} ", jobManifest.toString());

        return CreateJobRequest.builder()
            .accountId(workflowModel.getSourceAccountNumber())
            .operation(jobOperation)
            .manifest(jobManifest)
            .priority(jobPriority)
            .roleArn(bopsRole)
            .description(jobDescription)
            .confirmationRequired(false)
            .report(jobReport)
            .build();
    }

    /**
     * Sets up a batch replication job for the given workflow model using the provided S3Control client,
     * workflow repository, and BOPS role.
     *
     * @param workflow The workflow object
     * @param s3ControlClient The S3Control client to be used for S3Control operations
     * @param workflowRepository The workflow repository to be used for workflow operations
     * @param bopsRole The BOPS role ARN to be used for the batch replication job
     * @return CreateJobResponse The created batch replication job response
     */
    public CreateJobResponse setupBOPSJob(final WorkFlowModel workflow, final S3ControlClient s3ControlClient,
                                          final WorkflowRepository workflowRepository, final String bopsRole) {
        try {
            CreateJobRequest createJobRequest = createBatchReplicationJobRequest(workflow, bopsRole, false);
            CreateJobResponse response = s3ControlClient.createJob(createJobRequest);
            updateWorkflowBopsJobId(workflow, workflowRepository, response);
            return response;
        } catch (S3ControlException s3ControlException) {
            String errorMessage = s3ControlException.awsErrorDetails().errorMessage();
            log.error("S3Exception while setting up bops job for workflowName: {}, namespaceId: {}",
                workflow.getWorkflowName(), workflow.getNamespaceID(), s3ControlException);
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * When S3 object manifest files are provided, this method will configure and start
     * the BOPS jobs by reading the manifest filenames from the provided manifestLocation.
     *
     * @param workflow The workflow details containing the destination bucket ARN
     * @param s3Client The S3Client for the source bucket
     * @param s3ControlClient  The S3 Control client for the source bucket
     * @param workflowRepository Used for DynamoDB persistence
     * @param bopsRole The IAM role needed by the BOPS jobs to be able to do their work
     */
    public CreateJobResponse setupBOPSJob(final WorkFlowModel workflow,
                                          final S3Client s3Client,
                                          final S3ControlClient s3ControlClient,
                                          final WorkflowRepository workflowRepository,
                                          final String bopsRole) {

        log.info("Received manifest URI: {}", workflow.getRuntimeConfig().getManifestLocation());
        // parse bucket and prefix from the manifest location and get a list of manifest files
        final S3Uri manifestLocationUri = s3Client.utilities().parseUri(URI.create(
                workflow
                    .getRuntimeConfig()
                    .getManifestLocation()
            )
        );
        final String manifestBucket = manifestLocationUri.bucket().orElse("");
        final String manifestPrefix = manifestLocationUri.key().orElse("");
        final List<ManifestFileMetadata>  manifestList = readManifests(manifestBucket, manifestPrefix, s3Client);
        CreateJobResponse response = null;
        List<String> jobIds = new ArrayList<>();
        // for each manifest, configure and start a BOPS job
        List<String> errorMessages = new ArrayList<>();
        for (ManifestFileMetadata manifestMetadata : manifestList) {
            try {
                CreateJobRequest createJobRequest = createBatchReplicationJobRequest(s3Client,
                    workflow, manifestBucket, manifestMetadata, bopsRole);
                response = s3ControlClient.createJob(createJobRequest);
                log.info("BOPS Job Created: {}", response.toString());
                jobIds.add(response.jobId());
            } catch (S3ControlException s3ControlException) {
                errorMessages.add(String.format("Error configuring BOPS job for manifest: %s -> %s",
                    manifestMetadata.getKey(),
                    s3ControlException.awsErrorDetails().errorMessage()));
                log.error("S3Exception while setting up bops job for workflowName: {}, namespaceId: {}, manifest: {}",
                    workflow.getWorkflowName(),
                    workflow.getNamespaceID(),
                    manifestMetadata.getKey(),
                    s3ControlException);
            }
        }

        // Since Inventory Report Config has a 1-2 delay in data after initially attaching the config to the bucket,
        // create a new BOPS job with a 4 day gap to ensure that the source and dest buckets are in sync.
        log.info("Starting BOPS job with 4 day gap to cover inventory report config data delay");
        try {
            CreateJobRequest createJobRequest = createBatchReplicationJobRequest(workflow, bopsRole, true);
            response = s3ControlClient.createJob(createJobRequest);
            jobIds.add(response.jobId());
        } catch (S3ControlException s3ControlException) {
            errorMessages.add(String.format("Error configuring BOPS job for 4 day gap: %s ",
                    s3ControlException.awsErrorDetails().errorMessage()));
            log.error(
                    "S3Exception in bops job setup for 4 day gap",
                    s3ControlException);
        }

        workflow.setBopsJobID(String.join(",", jobIds)); // for backward compatibility
        workflow.setBopsJobIds(jobIds); // this is for monitoring
        workflow.setStatus(WorkflowStatus.RUNNING.name());
        workflowRepository.updateWorkflow(workflow);

        if (!errorMessages.isEmpty()) {
            throw new RuntimeException(errorMessages.toString());
        }
        return response;

    }

    /**
     * Helper method to read the manifest list (manifestLocation = s3://bucket/prefix).
     *
     * @param manifestBucket The manifest bucket to be used for S3 operations
     * @param manifestPrefix The manifest prefix to be used for S3 operations
     * @param s3Client The S3 client to be used for S3 operations
     * @return List of ManifestFileMetadata
     */
    private List<ManifestFileMetadata> readManifests(final String manifestBucket,
                                                     final String manifestPrefix,
                                                     final S3Client s3Client) {

        List<ManifestFileMetadata> manifestList = new ArrayList<>();

        try {
            // List all objects with the given prefix
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(manifestBucket)
                .prefix(manifestPrefix)
                .build();

            ListObjectsV2Response listResponse;
            do {
                listResponse = s3Client.listObjectsV2(listRequest);

                //  Add CSV file
                listResponse.contents().stream()
                    .filter(obj -> obj.key().toLowerCase().endsWith(".csv"))
                    .forEach(obj -> {
                        ManifestFileMetadata fileData = ManifestFileMetadata.builder()
                            .key(obj.key())
                            .etag(obj.eTag().replaceAll("\"", ""))
                            .build();
                        manifestList.add(fileData);
                    });

                // Prepare next request with continuation token
                listRequest = ListObjectsV2Request.builder()
                    .bucket(manifestBucket)
                    .prefix(manifestPrefix)
                    .continuationToken(listResponse.nextContinuationToken())
                    .build();

            }
            while (listResponse.isTruncated());

        } catch (S3Exception e) {
            throw new RuntimeException("Error reading from S3: " + e.getMessage(), e);
        }
        if (manifestList.isEmpty()) {
            throw new RuntimeException("No CSV files found in manifest location");
        }
        return manifestList;
    }

    /**
     *  Updates the workflow with the BOPS job ID and status.
     *
     * @param workflow The workflow object
     * @param workflowRepository The workflow repository to be used for workflow operations
     */
    private void updateWorkflowBopsJobId(final WorkFlowModel workflow, final WorkflowRepository workflowRepository,
                                         final CreateJobResponse response) {
        String jobId = response.jobId();
        workflow.setBopsJobID(jobId);
        workflow.setBopsJobIds(Collections.singletonList(jobId));
        workflow.setStatus(WorkflowStatus.RUNNING.name());
        workflowRepository.updateWorkflow(workflow);
    }

    /**
     *  Updates the batch job status.
     *
     * @param s3ControlClient The S3Control client to be used for S3Control operations
     * @param workFlowModel The workflow object
     * @param requestedJobStatus The requested job status to be updated
     */
    public void batchUpdateJobStatus(final S3ControlClient s3ControlClient,
                                     final WorkFlowModel workFlowModel,
                                     final RequestedJobStatus requestedJobStatus) {
        final List<String> bopsJobIds = workFlowModel.getBopsJobIds();

        if (bopsJobIds.isEmpty()) {
            log.info("No BOPS jobs found for workflowName: {}, namespaceId: {}",
                workFlowModel.getWorkflowName(),
                workFlowModel.getNamespaceID());
            return;
        }

        log.info("Attempting to update status to {} for BOPS jobIds: {}", requestedJobStatus, bopsJobIds);

        for (String bopsJobId : bopsJobIds) {
            try {
                final UpdateJobStatusRequest updateJobStatusRequest = UpdateJobStatusRequest.builder()
                    .accountId(workFlowModel.getSourceAccountNumber())
                    .jobId(bopsJobId)
                    .requestedJobStatus(requestedJobStatus)
                    .build();
                s3ControlClient.updateJobStatus(updateJobStatusRequest);
            } catch (S3ControlException exception) {
                if (exception.statusCode() == 400
                    && exception.getMessage().contains("Requested job status forbidden")) {
                    log.warn("Cannot update status for job {}, likely already completed.",
                        bopsJobId);
                } else {
                    log.error("Failed to update status for job {}: {}",
                        bopsJobId, exception);
                    throw new RuntimeException("Error updating job status", exception);
                }
            }
        }
        log.info("Successfully updated job status for all BOPS jobs: {}",
            bopsJobIds);
    }
}
