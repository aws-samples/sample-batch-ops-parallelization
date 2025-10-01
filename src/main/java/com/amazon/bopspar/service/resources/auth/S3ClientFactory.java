package com.amazon.bopspar.service.resources.auth;

import com.amazon.bopspar.model.AWSServiceException;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

/**
 * This client Factory class is needed to dynamically inject S3 client into Lambda
 * handler function. Without this it is not possible to create S3 client with
 * roleArn and region as parameters. This also facilitates mocking S3 client in
 * unit tests.
 *
 */
public class S3ClientFactory {

    public S3Client createS3Client(final String roleArn, final String region) {
        // Use the roleArn and region to configure and return a new S3Client
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(getAwsSessionCredentials(roleArn)))
                .build();
    }

    //Create S3 Control client
    public S3ControlClient createS3ControlClient(final String roleArn, final String region) {
        // Use the roleArn and region to configure and return a new S3Client
        return S3ControlClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(getAwsSessionCredentials(roleArn)))
            .region(Region.of(region))
            .build();
    }

    //Create CloudWatch client
    public CloudWatchClient createCloudwatchClient(final String roleArn, final String region) {
        // Use the roleArn and region to configure and return a new Cloudwatch client
        return CloudWatchClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(getAwsSessionCredentials(roleArn)))
            .region(Region.of(region))
            .build();            
    }

    public KmsClient createKmsClient(final String roleArn, final String region) {
        // Use the roleArn and region to configure and return a new KMS client
        if (roleArn == null || roleArn.isEmpty() || region == null || region.isEmpty()) {
            throw new IllegalArgumentException("Role ARN and region must not be null or empty");
        }
        try {
            return KmsClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(getAwsSessionCredentials(roleArn)))
                .region(Region.of(region))
                .build();
        } catch (Exception e) {
            throw new AWSServiceException("Failed to create KMS client", e);
        }
    }

    public GlueClient createGlueClient(final String roleArn, final String region) {
        // Use the roleArn and region to configure and return a new Glue client
        if (roleArn == null || roleArn.isEmpty() || region == null || region.isEmpty()) {
            throw new IllegalArgumentException("Role ARN and region must not be null or empty");
        }
        try {
            return GlueClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(getAwsSessionCredentials(roleArn)))
                .region(Region.of(region))
                .build();
        } catch (Exception e) {
            throw new AWSServiceException("Failed to create Glue client", e);
        }
    }

    private AwsSessionCredentials getAwsSessionCredentials(final String roleArn) {
        // Assume role logic using STS and return AwsSessionCredentials
        StsClient stsClient = StsClient.builder().region(Region.AWS_GLOBAL).build();
        AssumeRoleResponse response = stsClient.assumeRole(AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("session-" + System.currentTimeMillis())
                .build());
        return AwsSessionCredentials.create(response.credentials().accessKeyId(),
                response.credentials().secretAccessKey(),
                response.credentials().sessionToken());
    }
}

