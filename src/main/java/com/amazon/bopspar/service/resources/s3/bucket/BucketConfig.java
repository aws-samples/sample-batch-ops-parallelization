package com.amazon.bopspar.service.resources.s3.bucket;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class BucketConfig {
    private String bucketName;
    private String iamRoleArn;
    private String bucketRegion;
}

