package com.amazon.bopspar.service.resources.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
* S3A Monitor Response. 
*
*/
@Log4j2
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class S3MonitorDetails {
    private String bopsJobStatus;
    private String crrStatus;
    private String workflowStatus;
    private String bopsSdkJobStatus;
    private Long bopsNumTasksFailed;
    private Long bopsNumTasksSucceeded;
    private Long bopsTotalTasks;
    private Double bopsPctProgress;
    private Long crrReplicationLatency;
    private Long crrBytesPendingReplication;
    private Long lastCRRCheckTimestamp;
    private Long creationTime;
    private Long terminationTime;
}
