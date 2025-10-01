package com.amazon.bopspar.service.resources.s3.bucket;

import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

public class S3CreateBucketImpl {
    public CreateBucketRequest buildCreateBucketConfig(final String roleARN, final String region,
                                                       final String destBucketName) {

        BucketConfig config = new BucketConfig(destBucketName, roleARN, region);

        CreateBucketConfiguration bucketConfig;
        if (region.equals("us-east-1")) {
            // For us-east-1 region, do not set the location constraint
            bucketConfig = CreateBucketConfiguration.builder().build();
        } else {
            bucketConfig = CreateBucketConfiguration.builder()
                    .locationConstraint(BucketLocationConstraint.fromValue(config.getBucketRegion()))
                    .build();
        }

        return CreateBucketRequest.builder()
                .bucket(config.getBucketName())
                .createBucketConfiguration(bucketConfig)
                .build();
    }

}