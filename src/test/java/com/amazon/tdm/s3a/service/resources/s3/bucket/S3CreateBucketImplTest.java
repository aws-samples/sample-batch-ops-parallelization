package com.amazon.tdm.s3a.service.resources.s3.bucket;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertAll;

public class S3CreateBucketImplTest {

    private final S3CreateBucketImpl s3CreateBucketImpl = new S3CreateBucketImpl();

    @Test
    public void testBuildCreateBucketConfig() {
        String roleARN = "arn:aws:iam::123456789012:role/myRole";
        String region = "us-west-2";
        String destBucketName = "my-bucket";

        CreateBucketRequest createBucketRequest = s3CreateBucketImpl.buildCreateBucketConfig(roleARN, region, destBucketName);

        assertAll(
                () -> assertNotNull(createBucketRequest, "CreateBucketRequest should not be null"),
                () -> assertEquals(destBucketName, createBucketRequest.bucket(), "Bucket name should match"),
                () -> assertNotNull(createBucketRequest.createBucketConfiguration(), "CreateBucketConfiguration should not be null"),
                () -> assertEquals(region, createBucketRequest.createBucketConfiguration().locationConstraint().toString(), "Region should match")
        );
    }
}