package com.amazon.tdm.s3a.service.resources.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

import javax.inject.Singleton;

@Builder
@Data
@AllArgsConstructor
@Singleton
public class S3AuthManager {

    final String iamRoleArn;
    final String region;

    public AwsSessionCredentials getAwsSessionCredentials() {

        final StsClient stsClient = createStsClient();
        final String roleSessionName = "session-" + System.currentTimeMillis();

        final AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                .roleArn(this.iamRoleArn)
                .roleSessionName(roleSessionName)
                .build();

        final AssumeRoleResponse assumeRoleResponse = stsClient.assumeRole(assumeRoleRequest);

        return AwsSessionCredentials.create(
            assumeRoleResponse.credentials().accessKeyId(),
            assumeRoleResponse.credentials().secretAccessKey(),
            assumeRoleResponse.credentials().sessionToken());
    }

    public StsClient createStsClient() {
        return StsClient.builder()
            .region(Region.of(this.region))
            .build();
    }

    public S3Client createS3Client() {

        return S3Client.builder()
                .region(Region.of(this.region))
                .credentialsProvider(StaticCredentialsProvider.create(getAwsSessionCredentials()))
                .build();
    }
}