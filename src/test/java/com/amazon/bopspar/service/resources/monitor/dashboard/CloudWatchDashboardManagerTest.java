package com.amazon.bopspar.service.resources.monitor.dashboard;

import com.amazon.bopspar.persistence.model.WorkFlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.model.GetDashboardRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardResponse;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudWatchDashboardManagerTest {

    private static final String DASHBOARD_URL = "https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards/dashboard/TDMS3MigrationAccelerator-123456789-test-namespace-test-workflow";

    @Mock
    private CloudWatchClient cloudWatchClientMock;

    @Mock
    private S3AcceleratorDashboard s3AcceleratorDashboardMock;

    private WorkFlowModel workflowModel;

    private CloudWatchDashboardManager cloudWatchDashboardManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        workflowModel = WorkFlowModel.builder()
                .workflowName("test-workflow")
                .namespaceID("test-namespace")
                .sourceAccountNumber("123456789")
                .sourceBucketARN("arn:aws:s3:::test-bucket")
                .destBucketARN("arn:aws:s3:::test-bucket2")
                .bopsJobIds(Collections.singletonList("test-bops-job-id"))
                .sourceRoleARN("test-role")
                .sourceRegion("us-east-1")
                .destRoleARN("test-role")
                .destRegion("us-east-1")
                .build();
        cloudWatchDashboardManager = new CloudWatchDashboardManager(s3AcceleratorDashboardMock);
    }

    @Test
    void createCloudWatchDashboard_Success() {
        // Set up mocks
        when(s3AcceleratorDashboardMock.createDashboardBody(workflowModel))
                .thenReturn("test-dashboard-body");

        when(cloudWatchClientMock.getDashboard(any(GetDashboardRequest.class)))
                .thenThrow(CloudWatchException.class);
        when(cloudWatchClientMock.putDashboard(any(PutDashboardRequest.class)))
                .thenReturn(PutDashboardResponse.builder().build());

        String dashboardUrl = assertDoesNotThrow(() -> 
            cloudWatchDashboardManager.createCloudWatchDashboard(cloudWatchClientMock, workflowModel));
        
        assertEquals(DASHBOARD_URL, dashboardUrl);
        verify(cloudWatchClientMock, times(1)).putDashboard(any(PutDashboardRequest.class));
        verify(s3AcceleratorDashboardMock, times(1)).createDashboardBody(workflowModel);
    }

    @Test
    void createCloudWatchDashboard_Fails_ReturnsNull() {
        // Set up mocks
        when(s3AcceleratorDashboardMock.createDashboardBody(workflowModel))
                .thenReturn("test-dashboard-body");

        when(cloudWatchClientMock.getDashboard(any(GetDashboardRequest.class)))
                .thenThrow(CloudWatchException.class);
        when(cloudWatchClientMock.putDashboard(any(PutDashboardRequest.class)))
                .thenThrow(CloudWatchException.class);

        String dashboardUrl = assertDoesNotThrow(() -> 
            cloudWatchDashboardManager.createCloudWatchDashboard(cloudWatchClientMock, workflowModel));
        
        assertNull(dashboardUrl);
        verify(cloudWatchClientMock, times(1)).putDashboard(any(PutDashboardRequest.class));
        verify(s3AcceleratorDashboardMock, times(1)).createDashboardBody(workflowModel);
    }
}