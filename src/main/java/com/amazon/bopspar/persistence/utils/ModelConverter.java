package com.amazon.bopspar.persistence.utils;

import com.amazon.bopspar.model.CreateWorkflowRequest;
import com.amazon.bopspar.persistence.model.RuntimeConfig;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;

@Log4j2
/**
 * Helper class to convert between different data models.
 */
public final class ModelConverter {

    private ModelConverter() {
        throw new IllegalStateException("Utility class");
    }

    public static WorkFlowModel convertWorkflowFromCoralToDdbModel(final CreateWorkflowRequest request) {

        WorkFlowModel.WorkFlowModelBuilder builder = WorkFlowModel.builder()
            .workflowName(request.getWorkflow().getWorkflowName())
            .runtimeConfig(setRuntimeConfig(request.getWorkflow().getRuntimeConfig()))
            .namespaceID(request.getWorkflow().getNamespaceID())
            .state(request.getWorkflow().getState())
            .status(request.getWorkflow().getStatus())
            .workflowType(request.getWorkflow().getWorkflowType())
            .sourceBucketARN(request.getWorkflow().getSourceBucketARN())
            .destBucketARN(request.getWorkflow().getDestBucketARN())
            .sourceRoleARN(request.getWorkflow().getSourceRoleARN())
            .destRoleARN(request.getWorkflow().getDestRoleARN())
            .sourceAccountNumber(request.getWorkflow().getSourceAccountNumber())
            .destAccountNumber(request.getWorkflow().getDestAccountNumber())
            .sourceRegion(request.getWorkflow().getSourceRegion())
            .destRegion(request.getWorkflow().getDestRegion())
            .bopsJobID(request.getWorkflow().getBopsJobID())
            .workflowConfig(request.getWorkflow().getWorkflowConfig())
            .sourceBucketConfig(request.getWorkflow().getSourceBucketConfig())
            .destBucketConfig(request.getWorkflow().getDestBucketConfig())
            .ackedNotification(request.getWorkflow().getAckedNotification())
            .sentNotification(request.getWorkflow().getSentNotification())
            .jobReportBucketARN(request.getWorkflow().getJobReportBucketARN());

        return builder.build();
    }

    private static RuntimeConfig setRuntimeConfig(final com.amazon.bopspar.model.RuntimeConfig runtimeConfig) {
        if (runtimeConfig != null) {
            RuntimeConfig.RuntimeConfigBuilder runtimeConfigBuilder = RuntimeConfig.builder();
            if (runtimeConfig.getManifestLocation() != null) {
                Optional.ofNullable(runtimeConfig)
                        .map(com.amazon.bopspar.model.RuntimeConfig::getManifestLocation)
                        .ifPresent(runtimeConfigBuilder::manifestLocation);
            }
            runtimeConfigBuilder.skipBucketOwnershipValidationAndCopy(
                    runtimeConfig.isSkipBucketOwnershipValidationAndCopy() != null
                            ? runtimeConfig.isSkipBucketOwnershipValidationAndCopy()
                            : false
            );
            runtimeConfigBuilder.isReplicationTimeControlEnabled(
                runtimeConfig.isReplicationTimeControlEnabled() != null
                    ? runtimeConfig.isReplicationTimeControlEnabled()
                    : false
            );
            return runtimeConfigBuilder.build();
        }
        return null;
    }
}

