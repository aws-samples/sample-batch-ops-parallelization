package com.amazon.bopspar.service.resources.replication.rule;

import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.replication.bucket.S3Bucket;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import software.amazon.awssdk.services.s3.model.ReplicationRule;

import java.util.List;

@Builder
@Getter
/**
 * Represents the configuration for replication operations.
 * This class holds the necessary configuration parameters for handling replication
 * between source and destination buckets.
 */
public class ReplicationConfig {
    @NonNull  // Makes workflow mandatory
    private final WorkFlowModel workflow;

    @NonNull  // Makes existingRules mandatory
    private final List<ReplicationRule> existingRules;

    private S3Bucket sourceBucket;
    private S3Bucket destBucket;
}
