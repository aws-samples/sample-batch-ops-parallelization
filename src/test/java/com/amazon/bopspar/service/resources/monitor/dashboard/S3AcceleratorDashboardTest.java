package com.amazon.bopspar.service.resources.monitor.dashboard;

import com.amazon.bopspar.persistence.model.BopsJobDetails;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import com.amazon.bopspar.service.resources.monitor.MonitorConstants;
import com.amazon.bopspar.service.utils.JsonMapperUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.s3control.model.JobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

class S3AcceleratorDashboardTest {

    private WorkFlowModel workflowModel;
    private Instant fixedNowInstant;
    private Instant fixedCreationInstant;
    private Duration fixedDuration;

    private BOPSParallelDashboard bopsparDashboard;

    @BeforeEach
    void setUp() {
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

        fixedNowInstant = Instant.parse("2023-06-01T10:15:30Z");
        fixedCreationInstant = Instant.parse("2023-06-01T09:10:30Z");
        fixedDuration = Duration.ofHours(1).plusMinutes(5);
        bopsparDashboard = new BOPSParallelDashboard();
    }

    @Test
    void createDashboardBody_NoJobs() {
        String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);
        assertTrue(dashboardBody.contains("*No running jobs*"));
        assertTrue(dashboardBody.contains("*No pending jobs*"));
        assertTrue(dashboardBody.contains("*No completed jobs*"));
    }

    @Test
    void createDashboardBody_WithAllJobTypes() {
        setupJobsForAllTypes();

        try (MockedStatic<Instant> instantMockedStatic = mockStatic(Instant.class);
             MockedStatic<Duration> durationMockedStatic = mockStatic(Duration.class)) {
            setupMockedInstants(instantMockedStatic);
            setupMockedDuration(durationMockedStatic);

            String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);

            assertAllJobTypesPresent(dashboardBody);
        }
    }

    @Test
    void createDashboardBody_OnlyRunningJobs() {
        setupOnlyRunningJobs();

        try (MockedStatic<Instant> instantMockedStatic = mockStatic(Instant.class)) {
            setupMockedInstants(instantMockedStatic);

            String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);

            assertTrue(dashboardBody.contains("### Running Jobs"));
            assertFalse(dashboardBody.contains("*No running jobs*"));
            assertTrue(dashboardBody.contains("*No pending jobs*"));
            assertTrue(dashboardBody.contains("*No completed jobs*"));
        }
    }

    @Test
    void createDashboardBody_OnlyPendingJobs() {
        setupOnlyPendingJobs();

        try (MockedStatic<Instant> instantMockedStatic = mockStatic(Instant.class);
             MockedStatic<Duration> durationMockedStatic = mockStatic(Duration.class)) {
            setupMockedInstants(instantMockedStatic);
            setupMockedDuration(durationMockedStatic);

            String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);

            assertTrue(dashboardBody.contains("*No running jobs*"));
            assertTrue(dashboardBody.contains("### Pending Jobs"));
            assertFalse(dashboardBody.contains("*No pending jobs*"));
            assertTrue(dashboardBody.contains("*No completed jobs*"));
        }
    }

    @Test
    void createDashboardBody_OnlyCompletedJobs() {
        setupOnlyCompletedJobs();

        try (MockedStatic<Instant> instantMockedStatic = mockStatic(Instant.class)) {
            setupMockedInstants(instantMockedStatic);

            String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);

            assertTrue(dashboardBody.contains("*No running jobs*"));
            assertTrue(dashboardBody.contains("*No pending jobs*"));
            assertTrue(dashboardBody.contains("### Completed Jobs"));
            assertFalse(dashboardBody.contains("*No completed jobs*"));
        }
    }

    @Test
    void createDashboardBody_ThrowsRuntimeException() {
        setupOnlyCompletedJobs();

        try (MockedStatic<JsonMapperUtil> jsonMapperUtilMockedStatic = mockStatic(JsonMapperUtil.class)) {
            jsonMapperUtilMockedStatic.when(JsonMapperUtil::createObjectNode).thenCallRealMethod();
            jsonMapperUtilMockedStatic.when(() -> JsonMapperUtil.writeValueAsString(any()))
                    .thenThrow(new JsonProcessingException("Test failure") {});

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> bopsparDashboard.createDashboardBody(workflowModel));

            assertEquals("Failed to create dashboard JSON", thrown.getMessage());
            assertTrue(thrown.getCause() instanceof JsonProcessingException);
        }
    }

    @Test
    void createDashboardBody_NullTasks() {
        workflowModel.setBopsJobDetails(Map.of(
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.ACTIVE, null, null, null),
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.READY, null, null, null),
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.COMPLETE, null, null, null)
        ));

        try (MockedStatic<Instant> instantMockedStatic = mockStatic(Instant.class);
             MockedStatic<Duration> durationMockedStatic = mockStatic(Duration.class)) {
            setupMockedInstants(instantMockedStatic);
            setupMockedDuration(durationMockedStatic);

            String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);

            assertAllJobTypesPresent(dashboardBody);
        }
    }

    @Test
    void createDashboardBody_NullWorkflowModel() {
        assertThrows(NullPointerException.class, () -> bopsparDashboard.createDashboardBody(null));
    }

    @Test
    void createDashboardBody_NullBopsJobDetails() {
        workflowModel.setBopsJobDetails(null);
        String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);
        assertTrue(dashboardBody.contains("*No running jobs*"));
        assertTrue(dashboardBody.contains("*No pending jobs*"));
        assertTrue(dashboardBody.contains("*No completed jobs*"));
    }

    @Test
    void createDashboardBody_NullStatus() {
        workflowModel.setBopsJobDetails(Map.of(
                UUID.randomUUID().toString(), BopsJobDetails.builder()
                        .numOfTotalTasks(null)
                        .numOfTasksFailed(null)
                        .numOfTasksSucceeded(null)
                        .percentProgress(null)
                        .creationTime(100L)
                        .terminationTime(null)
                        .elapsedTimeInActiveSeconds(null)
                        .sdkJobStatus(null)
                        .jobStatus(null)
                        .jobCompletionReportUrl(null)
                        .build()
        ));
        try (MockedStatic<Instant> instantMockedStatic = mockStatic(Instant.class);
             MockedStatic<Duration> durationMockedStatic = mockStatic(Duration.class)) {
            setupMockedInstants(instantMockedStatic);
            setupMockedDuration(durationMockedStatic);

            String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);

            assertTrue(dashboardBody.contains("*No running jobs*"));
            assertTrue(dashboardBody.contains("### Pending Jobs"));
            assertFalse(dashboardBody.contains("*No pending jobs*"));
            assertTrue(dashboardBody.contains("*No completed jobs*"));
        }
    }

    @Test
    void createDashboardBody_NullTimes() {
        workflowModel.setBopsJobDetails(Map.of(
                UUID.randomUUID().toString(), BopsJobDetails.builder()
                        .numOfTotalTasks(null)
                        .numOfTasksFailed(null)
                        .numOfTasksSucceeded(null)
                        .percentProgress(null)
                        .creationTime(null)
                        .terminationTime(null)
                        .elapsedTimeInActiveSeconds(null)
                        .sdkJobStatus(String.valueOf(JobStatus.COMPLETE))
                        .jobStatus(null)
                        .jobCompletionReportUrl(null)
                        .build()
        ));
        try (MockedStatic<Instant> instantMockedStatic = mockStatic(Instant.class);
             MockedStatic<Duration> durationMockedStatic = mockStatic(Duration.class)) {
            setupMockedInstants(instantMockedStatic);
            setupMockedDuration(durationMockedStatic);

            String dashboardBody = bopsparDashboard.createDashboardBody(workflowModel);

            assertTrue(dashboardBody.contains("*No running jobs*"));
            assertTrue(dashboardBody.contains("### Completed Jobs"));
            assertTrue(dashboardBody.contains("*No pending jobs*"));
            assertFalse(dashboardBody.contains("*No completed jobs*"));
        }
    }

    private void setupJobsForAllTypes() {
        workflowModel.setBopsJobDetails(Map.of(
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.ACTIVE, 50L, 25L, 0L),
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.READY, 100L, 0L, 0L),
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.COMPLETE, 100L, 100L, 0L)
        ));
    }

    private void setupOnlyRunningJobs() {
        workflowModel.setBopsJobDetails(Map.of(
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.ACTIVE, 50L, 25L, 0L)
        ));
    }

    private void setupOnlyPendingJobs() {
        workflowModel.setBopsJobDetails(Map.of(
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.READY, 100L, 0L, 0L)
        ));
    }

    private void setupOnlyCompletedJobs() {
        workflowModel.setBopsJobDetails(Map.of(
                UUID.randomUUID().toString(), createBopsJobDetails(JobStatus.COMPLETE, 100L, 100L, 0L)
        ));
    }

    private BopsJobDetails createBopsJobDetails(JobStatus status, Long totalTasks, Long succeededTasks, Long failedTasks) {
        return BopsJobDetails.builder()
                .numOfTotalTasks(totalTasks)
                .numOfTasksFailed(failedTasks)
                .numOfTasksSucceeded(succeededTasks)
                .percentProgress((succeededTasks != null && totalTasks != null && totalTasks > 0)
                        ? (succeededTasks.doubleValue() / totalTasks.doubleValue()) * 100.0
                        : 0.0)
                .creationTime(fixedCreationInstant.toEpochMilli())
                .terminationTime(status == JobStatus.COMPLETE ? fixedNowInstant.toEpochMilli() : null)
                .elapsedTimeInActiveSeconds(100L)
                .sdkJobStatus(status.toString())
                .jobStatus(status == JobStatus.COMPLETE ? MonitorConstants.BOPSStatus.BOPS_FINISHED.name() : MonitorConstants.BOPSStatus.BOPS_RUNNING.name())
                .jobCompletionReportUrl(status == JobStatus.COMPLETE ? "test-url" : null)
                .build();
    }

    private void setupMockedInstants(MockedStatic<Instant> instantMockedStatic) {
        instantMockedStatic.when(Instant::now).thenReturn(fixedNowInstant);
        instantMockedStatic.when(() -> Instant.ofEpochMilli(anyLong())).thenReturn(fixedCreationInstant);
    }

    private void setupMockedDuration(MockedStatic<Duration> durationMockedStatic) {
        durationMockedStatic.when(() -> Duration.between(any(Instant.class), any(Instant.class)))
                .thenReturn(fixedDuration);
    }

    private void assertAllJobTypesPresent(String dashboardBody) {
        assertTrue(dashboardBody.contains("### Running Jobs"));
        assertFalse(dashboardBody.contains("*No running jobs*"));
        assertTrue(dashboardBody.contains("### Pending Jobs"));
        assertFalse(dashboardBody.contains("*No pending jobs*"));
        assertTrue(dashboardBody.contains("### Completed Jobs"));
        assertFalse(dashboardBody.contains("*No completed jobs*"));
    }
}