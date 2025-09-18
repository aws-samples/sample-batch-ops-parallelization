package com.amazon.tdm.s3a.service.resources.monitor.dashboard;

import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.DASHBOARD_PREFIX;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.DASHBOARD_URL_FORMAT;
import static com.amazon.tdm.s3a.service.resources.monitor.dashboard.DashboardConstants.DEFAULT_REGION;

/**
 * Manager to create CloudWatch dashboard per workflow.
 */
public class CloudWatchDashboardManager {

    private static final Logger LOGGER = LogManager.getLogger(CloudWatchDashboardManager.class);
    private final S3AcceleratorDashboard s3AcceleratorDashboard;

    public CloudWatchDashboardManager(final S3AcceleratorDashboard s3AcceleratorDashboard) {
        this.s3AcceleratorDashboard = s3AcceleratorDashboard;
    }

    /**
     * Create or update CW Dashboard per bucket.
     *
     * @param s3aCloudWatchClient CW client for operations in the S3A account.
     * @return url of the CW dashboard
     */
    public String createCloudWatchDashboard(final CloudWatchClient s3aCloudWatchClient,
                                            final WorkFlowModel workflowModel) {
        String dashboardName = buildDashboardName(workflowModel);
        String dashboardBody = s3AcceleratorDashboard.createDashboardBody(workflowModel);

        try {
            PutDashboardRequest dashboardRequest = PutDashboardRequest.builder()
                    .dashboardName(dashboardName)
                    .dashboardBody(dashboardBody)
                    .build();

            PutDashboardResponse response = s3aCloudWatchClient.putDashboard(dashboardRequest);
            LOGGER.info("Dashboard created/updated successfully: {} ", response.dashboardValidationMessages());
        } catch (CloudWatchException e) {
            LOGGER.error("Could not create/update dashboard: {}", e.toString());
            return null;
        }
        return buildDashboardUrl(dashboardName);
    }

    private String buildDashboardUrl(final String dashboardName) {
        String region = Optional.ofNullable(System.getenv("AWS_REGION"))
                       .orElse(DEFAULT_REGION);
        String encodedRegion = URLEncoder.encode(region, StandardCharsets.UTF_8);
        String encodedDashboardName = URLEncoder.encode(dashboardName, StandardCharsets.UTF_8);

        return String.format(DASHBOARD_URL_FORMAT,
                encodedRegion,
                encodedRegion,
                encodedDashboardName);
    }

    private String buildDashboardName(final WorkFlowModel workflowModel) {
        return String.format("%s-%s-%s-%s",
                DASHBOARD_PREFIX,
                workflowModel.getSourceAccountNumber(),
                workflowModel.getNamespaceID(),
                workflowModel.getWorkflowName());
    }
}
