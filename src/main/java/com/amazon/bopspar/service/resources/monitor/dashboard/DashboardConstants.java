package com.amazon.bopspar.service.resources.monitor.dashboard;

public final class DashboardConstants {

    private DashboardConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final String DASHBOARD_PREFIX = "BOPSParallel";
    public static final String METRICS_NAMESPACE = "BOPSParallel";
    public static final String DEFAULT_REGION = "us-west-2";

    // CloudWatch Dashboard Body Structure and Syntax: https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/CloudWatch-Dashboard-Body-Structure.html

    // Default widget dimensions
    public static final int DEFAULT_WIDGET_WIDTH = 5;
    public static final int DEFAULT_WIDGET_HEIGHT = 5;

    // Specific widget dimensions
    public static final int HEADER_WIDTH = 20;
    public static final int HEADER_HEIGHT = 3;
    public static final int SECTION_HEADER_HEIGHT = 1;
    public static final int TIME_SERIES_WIDTH = 10;
    public static final int TIME_SERIES_HEIGHT = 6;
    public static final int MAX_TABLE_WIDTH = 20;
    public static final int TABLE_WIDTH = 10;
    public static final int TABLE_HEIGHT = 6;

    // Dashboard URL

    public static final String BASE_URL = "https://%s.console.aws.amazon.com/cloudwatch/home";
    public static final String REGION_PARAM = "region";
    public static final String DASHBOARD_FRAGMENT = "#dashboards/dashboard/";

    /**
     * Format string for constructing the full dashboard URL.
     */
    public static final String DASHBOARD_URL_FORMAT = BASE_URL + "?"
            + REGION_PARAM + "=%s"
            + DASHBOARD_FRAGMENT + "%s";
}
