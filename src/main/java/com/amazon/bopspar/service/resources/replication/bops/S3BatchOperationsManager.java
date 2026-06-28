package com.amazon.bopspar.service.resources.replication.bops;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.CreateJobRequest;
import software.amazon.awssdk.services.s3control.model.CreateJobResponse;
import software.amazon.awssdk.services.s3control.model.JobManifestGenerator;
import software.amazon.awssdk.services.s3control.model.JobManifestGeneratorFilter;
import software.amazon.awssdk.services.s3control.model.JobOperation;
import software.amazon.awssdk.services.s3control.model.JobReport;
import software.amazon.awssdk.services.s3control.model.JobReportScope;
import software.amazon.awssdk.services.s3control.model.RequestedJobStatus;
import software.amazon.awssdk.services.s3control.model.S3ControlException;
import software.amazon.awssdk.services.s3control.model.S3JobManifestGenerator;
import software.amazon.awssdk.services.s3control.model.S3ReplicateObjectOperation;
import software.amazon.awssdk.services.s3control.model.UpdateJobStatusRequest;

import java.util.ArrayList;
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
                                                              final String bopsRole) {
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

        JobManifestGeneratorFilter jobManifestGeneratorFilter = JobManifestGeneratorFilter.builder()
                .eligibleForReplication(true)
                .build();

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
            CreateJobRequest createJobRequest = createBatchReplicationJobRequest(workflow, bopsRole);
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
