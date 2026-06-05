package com.amazon.bopspar.service.lambda;

import com.amazon.bopspar.model.InvalidInputException;
import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.model.MonitoringDetails;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.requests.WorkflowRequest;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.monitor.S3MonitorManager;
import com.amazon.bopspar.service.resources.monitor.S3MonitorManagerFactory;
import com.amazon.bopspar.service.resources.monitor.dashboard.CloudWatchDashboardManager;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.responses.WorkflowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClientBuilder;
import software.amazon.awssdk.services.s3control.S3ControlClient;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BOPSParallelMonitorLambdaTest {

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private S3MonitorManagerFactory monitorManagerFactory;
    @Mock
    private S3ClientFactory s3ClientFactory;
    @Mock
    private S3MonitorManager monitorManager;
    @Mock
    private CloudWatchDashboardManager cloudWatchDashboardManager;
    @Mock
    private S3ControlClient s3ControlClient;
    @Mock
    private CloudWatchClient cloudwatchClient;
    @Mock
    private WorkflowStatusManager workflowStatusManager;

    private BOPSParallelMonitorLambda s3MonitorLambda;

    private static final String MONITOR_ROLE = "arn:aws:iam::123456789:role/s3a-cloudwatch-permissions";

    @BeforeEach
    void setUp() {
        s3MonitorLambda = new BOPSParallelMonitorLambda(workflowRepository, monitorManagerFactory, cloudWatchDashboardManager,
                s3ClientFactory, workflowStatusManager);
    }

    @Test
    void testDefaultConstructor() {
        s3MonitorLambda = new BOPSParallelMonitorLambda();
        assertNotNull(s3MonitorLambda);
    }

    @Test
    void testHandleRequest_MissingWorkflowName() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName(null); // Invalid input
        workflowRequest.setNamespaceID("namespaceID");

        assertThrows(InvalidInputException.class, () -> s3MonitorLambda.handleRequest(workflowRequest, null));
    }

    @Test
    void testHandleRequest_MissingNamespaceID() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("worflowName");
        workflowRequest.setNamespaceID(null); // Invalid input

        assertThrows(InvalidInputException.class, () -> s3MonitorLambda.handleRequest(workflowRequest, null));
    }

    @Test
    void testHandleRequest_NullInput() {
        WorkflowRequest workflowRequest = null;
        assertThrows(NullPointerException.class, () -> s3MonitorLambda.handleRequest(workflowRequest, null));
        verify(monitorManager, never()).getIndividualBopsJobDetails(s3ControlClient);
        verify(monitorManager, never()).calculateAggregateMonitoringDetails();
        verify(monitorManager, never()).calculateCrrMonitoringDetails(any(CloudWatchClient.class));
    }

    @Test
    void testHandleRequestWithValidInput() {
        WorkflowRequest workflowRequest = getTestWorkflowRequest();
        WorkFlowModel workflowModel = getTestWorkflowModel();
        CloudWatchClient mockLocalCloudWatchClient = mock(CloudWatchClient.class);
        CloudWatchClientBuilder mockBuilder = mock(CloudWatchClientBuilder.class);

        try (MockedStatic<CloudWatchClient> cloudWatchClientMockedStatic = mockStatic(CloudWatchClient.class)) {
            cloudWatchClientMockedStatic.when(CloudWatchClient::builder).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockLocalCloudWatchClient);

            when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
            when(workflowStatusManager.shouldContinueMonitoring(workflowModel)).thenReturn(true);
            when(monitorManagerFactory.create(workflowModel)).thenReturn(monitorManager);
            when(s3ClientFactory.createS3ControlClient("source-role-arn", "us-west-2")).thenReturn(s3ControlClient);
            when(s3ClientFactory.createCloudwatchClient(MONITOR_ROLE, "us-east-1")).thenReturn(cloudwatchClient);

            WorkflowResponse workflowResponse = s3MonitorLambda.handleRequest(workflowRequest, null);

            assertEquals("testWorkflow", workflowResponse.getWorkflowName());
            assertEquals("testNamespace", workflowResponse.getNamespaceID());
            assertEquals("RUNNING", workflowResponse.getStatus());
            verify(monitorManager, times(1)).getIndividualBopsJobDetails(s3ControlClient);
            verify(monitorManager, times(1)).calculateAggregateMonitoringDetails();
            verify(monitorManager, times(1)).calculateCrrMonitoringDetails(any(CloudWatchClient.class));
        }
    }

    @Test
    void testHandleRequestWithStoppingWorkflow_ContinuesMonitoring() {
        WorkflowRequest workflowRequest = getTestWorkflowRequest();
        WorkFlowModel workflowModel = getTestWorkflowModel();
        workflowModel.setStatus(WorkflowStatus.STOPPING.name());
        CloudWatchClient mockLocalCloudWatchClient = mock(CloudWatchClient.class);
        CloudWatchClientBuilder mockBuilder = mock(CloudWatchClientBuilder.class);

        try (MockedStatic<CloudWatchClient> cloudWatchClientMockedStatic = mockStatic(CloudWatchClient.class)) {
            cloudWatchClientMockedStatic.when(CloudWatchClient::builder).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockLocalCloudWatchClient);

            when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
            when(workflowStatusManager.shouldContinueMonitoring(workflowModel)).thenReturn(true);
            when(monitorManagerFactory.create(workflowModel)).thenReturn(monitorManager);
            when(s3ClientFactory.createS3ControlClient("source-role-arn", "us-west-2")).thenReturn(s3ControlClient);
            when(s3ClientFactory.createCloudwatchClient(MONITOR_ROLE, "us-east-1")).thenReturn(cloudwatchClient);

            WorkflowResponse workflowResponse = s3MonitorLambda.handleRequest(workflowRequest, null);

            assertEquals("testWorkflow", workflowResponse.getWorkflowName());
            assertEquals("testNamespace", workflowResponse.getNamespaceID());
            assertEquals("STOPPING", workflowResponse.getStatus());
            verify(monitorManager, times(1)).getIndividualBopsJobDetails(s3ControlClient);
            verify(monitorManager, times(1)).calculateAggregateMonitoringDetails();
            verify(monitorManager, times(1)).calculateCrrMonitoringDetails(any(CloudWatchClient.class));
        }
    }

    @Test
    void testHandleRequestWithFailedWorkflow_ExitsMonitoring() {
        WorkflowRequest workflowRequest = getTestWorkflowRequest();
        WorkFlowModel workflowModel = getTestWorkflowModel();
        workflowModel.setStatus(WorkflowStatus.FAILED.name());

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        when(workflowStatusManager.shouldContinueMonitoring(workflowModel)).thenReturn(false);

        WorkflowResponse workflowResponse = s3MonitorLambda.handleRequest(workflowRequest, null);

        assertEquals("testWorkflow", workflowResponse.getWorkflowName());
        assertEquals("testNamespace", workflowResponse.getNamespaceID());
        assertEquals("FAILED", workflowResponse.getStatus());
        verify(monitorManager, never()).getIndividualBopsJobDetails(s3ControlClient);
        verify(monitorManager, never()).calculateAggregateMonitoringDetails();
        verify(monitorManager, never()).calculateCrrMonitoringDetails(any(CloudWatchClient.class));
    }

    @Test
    void testHandleRequestThrowException() {
        WorkflowRequest workflowRequest = getTestWorkflowRequest();
        WorkFlowModel workflowModel = getTestWorkflowModel();

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace"))
                .thenReturn(workflowModel);
        when(workflowStatusManager.shouldContinueMonitoring(workflowModel)).thenReturn(true);
        when(s3ClientFactory.createS3ControlClient(
                eq(workflowModel.getSourceRoleARN()),
                eq(workflowModel.getSourceRegion()))).thenThrow(AwsServiceException.class);
        WorkflowResponse workflowResponse = s3MonitorLambda.handleRequest(workflowRequest, null);

        assertEquals("testWorkflow", workflowResponse.getWorkflowName());
        assertEquals("testNamespace", workflowResponse.getNamespaceID());
        assertEquals("FAILED", workflowResponse.getStatus());
    }

    @Test
    void testHandleRequestWithValidInputNotRunning() {
        WorkflowRequest workflowRequest = getTestWorkflowRequest();
        WorkFlowModel workflowModel = getTestWorkflowModel();
        workflowModel.setStatus("FAILED");

        when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
        WorkflowResponse workflowResponse = s3MonitorLambda.handleRequest(workflowRequest, null);

        assertEquals("testWorkflow", workflowResponse.getWorkflowName());
        assertEquals("testNamespace", workflowResponse.getNamespaceID());
        assertEquals("FAILED", workflowResponse.getStatus());

    }

    @Test
    void testHandleRequest_CreatesDashboard_Success() {
        WorkflowRequest workflowRequest = getTestWorkflowRequest();
        WorkFlowModel workflowModel = getTestWorkflowModel();
        CloudWatchClient mockLocalCloudWatchClient = mock(CloudWatchClient.class);
        CloudWatchClientBuilder mockBuilder = mock(CloudWatchClientBuilder.class);

        try (MockedStatic<CloudWatchClient> cloudWatchClientMockedStatic = mockStatic(CloudWatchClient.class)) {
            cloudWatchClientMockedStatic.when(CloudWatchClient::builder).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockLocalCloudWatchClient);

            when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
            when(workflowStatusManager.shouldContinueMonitoring(workflowModel)).thenReturn(true);
            when(monitorManagerFactory.create(workflowModel)).thenReturn(monitorManager);
            when(s3ClientFactory.createS3ControlClient("source-role-arn", "us-west-2")).thenReturn(s3ControlClient);
            when(s3ClientFactory.createCloudwatchClient(MONITOR_ROLE, "us-east-1")).thenReturn(cloudwatchClient);
            when(monitorManager.publishCWMetrics(mockLocalCloudWatchClient)).thenReturn(true);
            doNothing().when(monitorManager).createCloudWatchAlarm(mockLocalCloudWatchClient);
            when(cloudWatchDashboardManager.createCloudWatchDashboard(mockLocalCloudWatchClient,
                    workflowModel)).thenReturn("testUrl");

            WorkflowResponse workflowResponse = s3MonitorLambda.handleRequest(workflowRequest, null);

            assertEquals("testWorkflow", workflowResponse.getWorkflowName());
            assertEquals("testNamespace", workflowResponse.getNamespaceID());
            assertEquals("RUNNING", workflowResponse.getStatus());
            assertNotNull(workflowModel.getRuntimeConfig());
            assertEquals("testUrl", workflowModel.getRuntimeConfig().getDashboardUrl());
        }
    }

    @Test
    void testHandleRequest_NoDashboard_Success() {
        WorkflowRequest workflowRequest = getTestWorkflowRequest();
        WorkFlowModel workflowModel = getTestWorkflowModel();
        CloudWatchClient mockLocalCloudWatchClient = mock(CloudWatchClient.class);
        CloudWatchClientBuilder mockBuilder = mock(CloudWatchClientBuilder.class);

        try (MockedStatic<CloudWatchClient> cloudWatchClientMockedStatic = mockStatic(CloudWatchClient.class)) {
            cloudWatchClientMockedStatic.when(CloudWatchClient::builder).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockLocalCloudWatchClient);

            when(workflowRepository.getWorkflow("testWorkflow", "testNamespace")).thenReturn(workflowModel);
            when(workflowStatusManager.shouldContinueMonitoring(workflowModel)).thenReturn(true);
            when(monitorManagerFactory.create(workflowModel)).thenReturn(monitorManager);
            when(s3ClientFactory.createS3ControlClient("source-role-arn", "us-west-2")).thenReturn(s3ControlClient);
            when(s3ClientFactory.createCloudwatchClient(MONITOR_ROLE, "us-east-1")).thenReturn(cloudwatchClient);
            when(monitorManager.publishCWMetrics(mockLocalCloudWatchClient)).thenReturn(true);

            WorkflowResponse workflowResponse = s3MonitorLambda.handleRequest(workflowRequest, null);

            assertEquals("testWorkflow", workflowResponse.getWorkflowName());
            assertEquals("testNamespace", workflowResponse.getNamespaceID());
            assertEquals("RUNNING", workflowResponse.getStatus());
            assertNull(workflowModel.getRuntimeConfig());
        }
    }

    private WorkFlowModel getTestWorkflowModel() {
        WorkFlowModel workflowModel = new WorkFlowModel();
        workflowModel.setSourceRoleARN("source-role-arn");
        workflowModel.setSourceAccountNumber("123456789");
        workflowModel.setDestAccountNumber("123456789");
        workflowModel.setWorkflowName("testWorkflow");
        workflowModel.setNamespaceID("testNamespace");
        workflowModel.setSourceRegion("us-west-2");
        workflowModel.setDestRoleARN("dest-role-arn");
        workflowModel.setDestRegion("us-east-1");
        workflowModel.setBopsJobIds(Collections.singletonList("jobId123"));
        workflowModel.setMonitoringDetails(MonitoringDetails.builder().build());
        workflowModel.setStatus("RUNNING");
        return workflowModel;
    }

    private WorkflowRequest getTestWorkflowRequest() {
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setWorkflowName("testWorkflow");
        workflowRequest.setNamespaceID("testNamespace");
        return workflowRequest;
    }
}
