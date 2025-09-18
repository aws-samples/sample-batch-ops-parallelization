package com.amazon.tdm.s3a.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Workflow details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {
    /**
     * ID being provided by createWorkflow API to S3A
     */
    private String namespaceID;
    
    /**
     * Name of the Workflow
     */
    private String workflowName;
    
    /**
     * Type of workflow
     */
    private String workflowType;
    
    /**
     * Status of the Workflow
     */
    private String status;
    
    private String sourceBucketARN;
    private String destBucketARN;
    private String sourceRoleARN;
    private String destRoleARN;
    private Map<String, String> workflowConfig;
    private RuntimeConfig runtimeConfig;
    private String sourceAccountNumber;
    private String destAccountNumber;
    private String sourceRegion;
    private String destRegion;
    private Map<String, String> sourceBucketConfig;
    private Map<String, String> destBucketConfig;
    private String bopsJobID;
    private String state;
    private String ackedNotification;
    private String sentNotification;
    private String jobReportBucketARN;
    
    // Metadata
    private String createdAt;
    private String startedAt;
    private String backfillCompletedAt;
    private String completedAt;
}