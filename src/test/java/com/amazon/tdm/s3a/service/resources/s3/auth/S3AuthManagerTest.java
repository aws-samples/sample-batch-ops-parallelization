package com.amazon.tdm.s3a.service.resources.s3.auth;

import com.amazon.tdm.s3a.service.resources.auth.S3AuthManager;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

public class S3AuthManagerTest {
    private S3AuthManager s3AuthManager;

    @Mock
    private StsClient stsClientMock;

    @Mock
    private AssumeRoleResponse assumeRoleResponseMock;

    @Mock
    private AwsSessionCredentials credentialsMock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        s3AuthManager = Mockito.mock(S3AuthManager.class);

        // Mocking the AWS credentials
        when(credentialsMock.accessKeyId()).thenReturn("mockAccessKeyId");
        when(credentialsMock.secretAccessKey()).thenReturn("mockSecretAccessKey");
        when(credentialsMock.sessionToken()).thenReturn("mockSessionToken");


        // Mocking the StsClient assumeRole method
        when(stsClientMock.assumeRole(any(AssumeRoleRequest.class))).thenReturn(assumeRoleResponseMock);

    }

    @Test
    void testDefaultConstructor() {


        s3AuthManager = S3AuthManager.builder()
                .iamRoleArn("arn:aws:iam::123456789012:role/mockRole")
                .region("us-west-2")
                .build();
        Assertions.assertNotNull(s3AuthManager);
    }

    @Test
    void testgetAwsSessionCredentials_success(){
        when(s3AuthManager.createStsClient()).thenReturn(stsClientMock);
        when(stsClientMock.assumeRole(any(AssumeRoleRequest.class))).thenReturn(assumeRoleResponseMock);
        when(s3AuthManager.getAwsSessionCredentials()).thenReturn(credentialsMock);
        AwsSessionCredentials awsSessionCredentials = s3AuthManager.getAwsSessionCredentials();
        assertEquals("mockAccessKeyId", awsSessionCredentials.accessKeyId());
    }
}