package com.amazon.bopspar.persistence.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDBDocument
public class MonitoringDetails implements Serializable {
    private String bopsJobStatus;
    private Long bopsNumOfTasksFailed;
    private Long bopsNumOfTasksSucceeded;
    private Long bopsNumOfTotalTasks;
    private Double bopsPercentProgress;
    private String crrStatus;
    private Long crrLatency;
    private Long crrBytesPendingReplication;
    private Long lastCRRCheckTimestamp;
    private Long creationTime;
    private Long terminationTime;
}
