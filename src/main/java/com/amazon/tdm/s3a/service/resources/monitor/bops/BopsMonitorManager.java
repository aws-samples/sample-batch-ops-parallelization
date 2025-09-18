package com.amazon.tdm.s3a.service.resources.monitor.bops;

import com.amazon.tdm.s3a.persistence.model.BopsJobDetails;
import com.amazon.tdm.s3a.persistence.model.MonitoringDetails;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.resources.monitor.MonitorConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.DescribeJobRequest;
import software.amazon.awssdk.services.s3control.model.DescribeJobResponse;
import software.amazon.awssdk.services.s3control.model.JobDescriptor;
import software.amazon.awssdk.services.s3control.model.JobStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * BOPS Monitor Manager class used to monitor BOPS jobs.
 */
public class BopsMonitorManager {
    private static final Logger LOGGER = LogManager.getLogger(BopsMonitorManager.class);

    /**
     * Get individual BOPS job details.
     * @param s3ControlClient s3ControlClient
     */
    public void getIndividualBopsJobDetails(final S3ControlClient s3ControlClient, final WorkFlowModel workflowModel) {
        Map<String, BopsJobDetails> jobDetailsMap = new HashMap<>();
        String sourceAccount = workflowModel.getSourceAccountNumber();

        // For each BOPS job ID, calculate and store its details
        for (String jobId : workflowModel.getBopsJobIds()) {
            BopsJobDetails details = calculateBopsJobDetails(
                    workflowModel,
                    s3ControlClient,
                    sourceAccount,
                    jobId
            );
            jobDetailsMap.put(jobId, details);
        }

        workflowModel.setBopsJobDetails(jobDetailsMap);
    }

    /**
     * Calculate aggregate monitoring details.
     * This function is used to calculate the aggregate monitoring details
     * for the entire workflow. It is called after all the individual BOPS job
     * details have been calculated.
     */
    public void calculateAggregateMonitoringDetails(final WorkFlowModel workflowModel) {
        Long numTotalTasks = 0L;
        Long numTasksSuccess = 0L;
        Long numTasksFailed = 0L;
        Long earliestCreationTime = Long.MAX_VALUE;
        Long latestTerminationTime = 0L;
        Set<MonitorConstants.BOPSStatus> activeStatuses = new HashSet<>();

        for (Map.Entry<String, BopsJobDetails> entry : workflowModel.getBopsJobDetails().entrySet()) {
            BopsJobDetails bopsJobDetails = entry.getValue();

            if (bopsJobDetails.getCreationTime() != null) {
                earliestCreationTime = Math.min(earliestCreationTime, bopsJobDetails.getCreationTime());
            }
            if (bopsJobDetails.getTerminationTime() != null) {
                latestTerminationTime = Math.max(latestTerminationTime, bopsJobDetails.getTerminationTime());
            }

            // Aggregate counts
            numTotalTasks += (bopsJobDetails.getNumOfTotalTasks() != null
                    ? bopsJobDetails.getNumOfTotalTasks() : 0L);
            numTasksSuccess += (bopsJobDetails.getNumOfTasksSucceeded() != null
                    ? bopsJobDetails.getNumOfTasksSucceeded() : 0L);
            numTasksFailed += (bopsJobDetails.getNumOfTasksFailed() != null
                    ? bopsJobDetails.getNumOfTasksFailed() : 0L);

            activeStatuses.add(MonitorConstants.BOPSStatus.valueOf(bopsJobDetails.getJobStatus()));
        }

        // Determine overall status after checking all jobs
        MonitorConstants.BOPSStatus jobStatus = determineOverallBopsStatus(activeStatuses);

        if (earliestCreationTime == Long.MAX_VALUE) {
            earliestCreationTime = 0L;
        }
        if (jobStatus == MonitorConstants.BOPSStatus.BOPS_RUNNING) {
            latestTerminationTime = 0L;
        }

        Double percentProgress = numTotalTasks > 0
                ? ((double) (numTasksSuccess + numTasksFailed) / numTotalTasks) * 100.0 : 0.0;

        MonitoringDetails monitoringDetails = MonitoringDetails.builder()
                .bopsJobStatus(String.valueOf(jobStatus))
                .bopsNumOfTasksFailed(numTasksFailed)
                .bopsNumOfTasksSucceeded(numTasksSuccess)
                .bopsNumOfTotalTasks(numTotalTasks)
                .bopsPercentProgress(percentProgress)
                .creationTime(earliestCreationTime)
                .terminationTime(latestTerminationTime)
                .build();
        workflowModel.setMonitoringDetails(monitoringDetails);
        workflowModel.setBopsJobDuration(getMigrationTimeInMinutes(earliestCreationTime, latestTerminationTime));
    }

    /**
     * Get S3 BOPS Job status.
     * @param s3ControlClient The SDK S3 Control client
     * @param accountId The AWS source account ID
     * @param jobId The Batch Ops Replicate Job ID
     */
    private BopsJobDetails calculateBopsJobDetails(final WorkFlowModel workFlowModel,
                                                   final S3ControlClient s3ControlClient,
                                                   final String accountId,
                                                   final String jobId) {
        MonitorConstants.BOPSStatus bopsStatus = MonitorConstants.BOPSStatus.BOPS_RUNNING;
        BopsJobDetails.BopsJobDetailsBuilder bopsJobDetailsBuilder = BopsJobDetails.builder();
        try {
            DescribeJobRequest describeJobRequest = DescribeJobRequest.builder()
                    .accountId(accountId)
                    .jobId(jobId)
                    .build();
            DescribeJobResponse describeJobResponse = s3ControlClient.describeJob(describeJobRequest);
            JobDescriptor jobDescriptor = describeJobResponse.job();
            if (jobDescriptor.creationTime() != null) {
                bopsJobDetailsBuilder.creationTime(jobDescriptor.creationTime().toEpochMilli());
            }
            LOGGER.info("Job Response: {}", describeJobResponse);

            final Long numTasksFailed = describeJobResponse.job().progressSummary().numberOfTasksFailed();
            final Long numTasksSuccess = describeJobResponse.job().progressSummary().numberOfTasksSucceeded();
            final Long numTotalTasks = describeJobResponse.job().progressSummary().totalNumberOfTasks();

            bopsJobDetailsBuilder.sdkJobStatus(describeJobResponse.job().status().toString());
            bopsJobDetailsBuilder.numOfTasksFailed(numTasksFailed);
            bopsJobDetailsBuilder.numOfTasksSucceeded(numTasksSuccess);
            bopsJobDetailsBuilder.numOfTotalTasks(numTotalTasks);
            Long currentTasksProcessed = numTasksFailed + numTasksSuccess;
            Double currentPercentCompleted = 0.0;
            if (numTotalTasks > 0) {
                currentPercentCompleted = ((double) currentTasksProcessed / numTotalTasks) * 100.0;
            }
            bopsJobDetailsBuilder.percentProgress(currentPercentCompleted);
            if (numTasksFailed > 0 || isFailingStatus(describeJobResponse.job().status())) {
                bopsStatus = MonitorConstants.BOPSStatus.BOPS_FAILED;
                if (jobDescriptor.terminationDate() != null) {
                    bopsJobDetailsBuilder.terminationTime(jobDescriptor.terminationDate().toEpochMilli());
                }
            } else if (describeJobResponse.job().status() == JobStatus.COMPLETE
                    && numTasksFailed == 0) {
                bopsStatus = MonitorConstants.BOPSStatus.BOPS_FINISHED;
                if (jobDescriptor.terminationDate() != null) {
                    bopsJobDetailsBuilder.terminationTime(jobDescriptor.terminationDate().toEpochMilli());
                }
            }
        } catch (S3Exception exception) {
            LOGGER.error("Error getting status for workflowId: {}, namespaceId: {}, "
                            + "accountId: {}, jobId: {}, Exception: {}",
                    workFlowModel.getWorkflowName(),
                    workFlowModel.getNamespaceID(),
                    accountId,
                    jobId,
                    exception);
            bopsStatus = MonitorConstants.BOPSStatus.BOPS_STATUS_ERROR;
        }
        bopsJobDetailsBuilder.jobStatus(bopsStatus.name());
        return bopsJobDetailsBuilder.build();
    }

    private MonitorConstants.BOPSStatus determineOverallBopsStatus(
            final Set<MonitorConstants.BOPSStatus> activeStatuses) {
        if (activeStatuses.contains(MonitorConstants.BOPSStatus.BOPS_RUNNING)) {
            return MonitorConstants.BOPSStatus.BOPS_RUNNING;
        } else if (activeStatuses.contains(MonitorConstants.BOPSStatus.BOPS_FAILED)) {
            return MonitorConstants.BOPSStatus.BOPS_FAILED;
        } else if (activeStatuses.contains(MonitorConstants.BOPSStatus.BOPS_STATUS_ERROR)) {
            return MonitorConstants.BOPSStatus.BOPS_STATUS_ERROR;
        } else {
            return MonitorConstants.BOPSStatus.BOPS_FINISHED;
        }
    }

    private String getMigrationTimeInMinutes(final Long creationTime, final Long terminationTime) {
        if (creationTime == 0L) {
            return "0";
        }

        long endTime = terminationTime != 0L
                ? terminationTime : System.currentTimeMillis();
        long activeTime = Math.max(0, (endTime - creationTime) / 60000);

        return String.valueOf(activeTime);
    }

    private static boolean isFailingStatus(final JobStatus jobStatus) {
        return jobStatus == JobStatus.FAILED
                || jobStatus == JobStatus.FAILING
                || jobStatus == JobStatus.CANCELLED
                || jobStatus == JobStatus.CANCELLING
                || jobStatus == JobStatus.SUSPENDED
                || jobStatus == JobStatus.UNKNOWN_TO_SDK_VERSION;
    }
}
