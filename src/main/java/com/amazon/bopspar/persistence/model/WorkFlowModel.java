package com.amazon.bopspar.persistence.model;

import com.amazon.bopspar.model.Workflow;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDBTable(tableName = "S3A_WORKFLOWS")

public class WorkFlowModel implements Serializable {

    @DynamoDBHashKey(attributeName = "workflowName")
    private String workflowName;

    @DynamoDBRangeKey(attributeName = "namespaceID")
    private String namespaceID;

    @DynamoDBAttribute(attributeName = "status")
    private String status;

    @DynamoDBAttribute(attributeName = "state")
    private String state;

    @DynamoDBAttribute(attributeName = "workflowType")
    private String workflowType;

    @DynamoDBAttribute(attributeName = "sourceBucketARN")
    private String sourceBucketARN;

    @DynamoDBAttribute(attributeName = "destBucketARN")
    private String destBucketARN;

    @DynamoDBAttribute(attributeName = "sourceRoleARN")
    private String sourceRoleARN;

    @DynamoDBAttribute(attributeName = "destRoleARN")
    private String destRoleARN;

    @DynamoDBAttribute(attributeName = "sourceAccountNumber")
    private String sourceAccountNumber;

    @DynamoDBAttribute(attributeName = "destAccountNumber")
    private String destAccountNumber;

    @DynamoDBAttribute(attributeName = "sourceRegion")
    private String sourceRegion;

    @DynamoDBAttribute(attributeName = "destRegion")
    private String destRegion;

    @DynamoDBAttribute(attributeName = "bopsJobID")
    private String bopsJobID;

    @DynamoDBAttribute(attributeName = "workflowConfig")
    private Map<String, String> workflowConfig;

    @DynamoDBAttribute(attributeName = "runtimeConfig")
    private RuntimeConfig runtimeConfig;

    @DynamoDBAttribute(attributeName = "sourceBucketConfig")
    private Map<String, String> sourceBucketConfig;

    @DynamoDBAttribute(attributeName = "destBucketConfig")
    private Map<String, String> destBucketConfig;

    @DynamoDBAttribute(attributeName = "ackedNotification")
    private String ackedNotification;

    @DynamoDBAttribute(attributeName = "sentNotification")
    private String sentNotification;

    @DynamoDBAttribute(attributeName = "jobReportBucketARN")
    private String jobReportBucketARN;

    @DynamoDBAttribute(attributeName = "createdAt")
    private String createdAt;

    @DynamoDBAttribute(attributeName = "startedAt")
    private String startedAt;

    @DynamoDBAttribute(attributeName = "backfillCompletedAt")
    private String backfillCompletedAt;

    @DynamoDBAttribute(attributeName = "completedAt")
    private String completedAt;

    @DynamoDBAttribute(attributeName = "bopsJobDuration")
    private String bopsJobDuration;

    @DynamoDBAttribute(attributeName = "manifestLocation")
    private String manifestLocation;

    @DynamoDBAttribute(attributeName = "bopsJobIds")
    private List<String> bopsJobIds;

    @DynamoDBAttribute(attributeName = "bopsJobDetails")
    private Map<String, BopsJobDetails> bopsJobDetails;

    @DynamoDBAttribute(attributeName = "monitoringDetails")
    private MonitoringDetails monitoringDetails;

    @DynamoDBAttribute(attributeName = "taskToken")
    private String taskToken;

    public Workflow toWorkflow() {
        return Workflow.builder()
                .workflowName(this.getWorkflowName())
                .namespaceID(this.getNamespaceID())
                .state(this.getState())
                .workflowType(this.getWorkflowType())
                .status(this.getStatus())
                .sourceRegion(this.getSourceRegion())
                .destRegion(this.getDestRegion())
                .sourceRoleARN(this.getSourceRoleARN())
                .destRoleARN(this.getDestRoleARN())
                .sourceAccountNumber(this.getSourceAccountNumber())
                .destAccountNumber(this.getDestAccountNumber())
                .bopsJobID(this.getBopsJobID())
                .workflowConfig(this.getWorkflowConfig())
                .runtimeConfig(this.getRuntimeConfig() != null ? this.getRuntimeConfig().toSmithyModel() : null)
                .sourceBucketConfig(this.getSourceBucketConfig())
                .sourceBucketARN(this.getSourceBucketARN())
                .destBucketARN(this.getDestBucketARN())
                .ackedNotification(this.getAckedNotification())
                .sentNotification(this.getSentNotification())
                .destBucketConfig(this.getDestBucketConfig())
                .jobReportBucketARN(this.getJobReportBucketARN())
                .createdAt(this.getCreatedAt())
                .startedAt(this.getStartedAt())
                .backfillCompletedAt(this.getBackfillCompletedAt())
                .completedAt(this.getCompletedAt())
                .build();
    }
}
