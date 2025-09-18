package com.amazon.tdm.s3a.persistence.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDBDocument
public class BopsJobDetails implements Serializable {
    private Long numOfTotalTasks;
    private Long numOfTasksFailed;
    private Long numOfTasksSucceeded;
    private Double percentProgress;
    private Long creationTime;
    private Long terminationTime;
    private Long elapsedTimeInActiveSeconds;
    /**
     * AWS SDK-specific BOPS job status from describeJob API call.
     * Used in deriving S3A-specific BOPS job status.
     * (ACTIVE, FAILED, COMPLETE, etc.)
     */
    private String sdkJobStatus;
    /**
     * S3A-specific BOPS job status used in workflow status calculation.
     * (RUNNING, FAILED, FINISHED, STATUS_ERROR)
     */
    private String jobStatus;
    private String jobCompletionReportUrl;
}