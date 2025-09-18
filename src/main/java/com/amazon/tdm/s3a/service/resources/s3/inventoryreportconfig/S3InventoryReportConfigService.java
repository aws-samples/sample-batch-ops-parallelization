package com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.policybuilder.iam.IamPolicy;
import software.amazon.awssdk.policybuilder.iam.IamStatement;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutBucketInventoryConfigurationRequest;
import software.amazon.awssdk.services.s3.model.InventoryConfiguration;
import software.amazon.awssdk.services.s3.model.InventorySchedule;
import software.amazon.awssdk.services.s3.model.InventoryDestination;
import software.amazon.awssdk.services.s3.model.InventoryFrequency;
import software.amazon.awssdk.services.s3.model.InventoryFormat;
import software.amazon.awssdk.services.s3.model.InventoryIncludedObjectVersions;
import software.amazon.awssdk.services.s3.model.InventoryS3BucketDestination;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

/**
 * Service class responsible for managing S3 Inventory Report configurations.
 * This class provides functionality to create and manage S3 inventory reports,
 * handle bucket policies, and retrieve object metrics via CloudWatch.
 * The service supports:
 * - Creating S3 inventory report configurations
 * - Managing cross-account bucket policies
 * - Retrieving object count metrics from CloudWatch
 */
public class S3InventoryReportConfigService {

    private static final Logger LOGGER = LogManager.getLogger(S3InventoryReportConfigService.class);

    /**
     * Creates an S3 inventory report configuration for a source bucket.
     * Configures daily inventory reports in Parquet format to be delivered to a specified destination.
     *
     * @param s3Client The S3 client to use for creating the inventory configuration
     * @param sourceBucketArn The ARN of the source bucket to inventory
     * @param reportsBucketArn The ARN of the destination bucket where inventory reports will be stored
     * @param pathToSaveInventoryReport The prefix path within the destination bucket for storing reports
     * @throws S3Exception if the inventory configuration creation fails
     */
    public void createS3InventoryReportConfig(final S3Client s3Client,
                                              final String sourceBucketArn,
                                              final String reportsBucketArn,
                                              final String pathToSaveInventoryReport
    ) {
        final String sourceBucketName = Arn.fromString(sourceBucketArn).resourceAsString();
        final String inventoryReportConfigId =
                S3InventoryReportConfigServiceUtils.generateInventoryConfigReportId(reportsBucketArn);
        InventoryConfiguration inventoryConfiguration = InventoryConfiguration.builder()
                .id(inventoryReportConfigId)
                .isEnabled(true)
                .schedule(InventorySchedule.builder().frequency(InventoryFrequency.DAILY).build())
                .includedObjectVersions(InventoryIncludedObjectVersions.ALL)
                .destination(InventoryDestination.builder()
                        .s3BucketDestination(InventoryS3BucketDestination.builder()
                                .bucket(reportsBucketArn)
                                .format(InventoryFormat.PARQUET)
                                .prefix(pathToSaveInventoryReport)
                                .build())
                        .build())
                .build();
        PutBucketInventoryConfigurationRequest putBucketInventoryConfigurationRequest =
                PutBucketInventoryConfigurationRequest.builder()
                        .bucket(sourceBucketName)
                        .id(inventoryReportConfigId)
                        .inventoryConfiguration(inventoryConfiguration)
                        .build();

        s3Client.putBucketInventoryConfiguration(putBucketInventoryConfigurationRequest);
    }

    /**
     * Adds or updates a bucket policy statement.
     * If no policy exists, creates a new one. If a policy exists, adds the new statement
     * while preserving existing statements.
     *
     * @param s3Client The S3 client to use for policy operations
     * @param bucketArn The ARN of the bucket whose policy needs to be updated
     * @param statement The IAM statement to add to the bucket policy
     * @throws S3Exception if the policy update fails
     */
    public void addBucketStatementToBucketPolicy(final S3Client s3Client,
                                                 final String bucketArn,
                                                 final IamStatement statement) {
        final String reportsBucketName = Arn.fromString(bucketArn).resourceAsString();
        IamPolicy.Builder policy;
        try {
            String existingPolicyJson = s3Client.getBucketPolicy(GetBucketPolicyRequest.builder()
                            .bucket(reportsBucketName)
                            .build())
                    .policy();
            policy = IamPolicy.fromJson(existingPolicyJson).toBuilder();
        } catch (S3Exception e) {
            if ("NoSuchBucketPolicy".equals(e.awsErrorDetails().errorCode())) {
                policy = IamPolicy.builder();
            } else {
                throw e;
            }
        }
        policy = policy.addStatement(statement);
        PutBucketPolicyRequest putBucketPolicyRequest = PutBucketPolicyRequest.builder()
                .bucket(reportsBucketName)
                .policy(policy.build().toJson())
                .build();
        s3Client.putBucketPolicy(putBucketPolicyRequest);
    }

    /**
     * Retrieves the number of objects in a bucket using CloudWatch metrics.
     * Queries the NumberOfObjects metric for the specified bucket over the last 2 days
     * and returns the maximum value.
     *
     * @param cloudWatchClient The CloudWatch client to use for metric queries
     * @param bucketArn The ARN of the bucket to query metrics for
     * @return The number of objects in the bucket
     * @throws CloudWatchException if unable to retrieve the metric data or if no data is available
     */
    public long getNumberOfObjectsForBucketViaCloudWatch(final CloudWatchClient cloudWatchClient,
                                                         final String bucketArn) {
        String bucketName = Arn.fromString(bucketArn).resourceAsString();

        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(2, ChronoUnit.DAYS);

        MetricDataQuery metricDataQuery = MetricDataQuery.builder()
            .id("numberOfObjects")
            .metricStat(MetricStat.builder()
                .metric(Metric.builder()
                    .namespace("AWS/S3")
                    .metricName("NumberOfObjects")
                    .dimensions(Arrays.asList(Dimension.builder()
                        .name("BucketName")
                        .value(bucketName)
                        .build(),
                        Dimension.builder()
                            .name("StorageType")
                            .value("AllStorageTypes")
                            .build()
                            ))
                    .build())
                .stat("Maximum")
                .period(86400)
                .build())
            .returnData(true)
            .build();

        GetMetricDataRequest getMetricDataRequest = GetMetricDataRequest.builder()
            .startTime(oneDayAgo)
            .endTime(now)
            .metricDataQueries(metricDataQuery)
            .build();

        GetMetricDataResponse response = cloudWatchClient.getMetricData(getMetricDataRequest);

        long numberOfObjects = Optional.ofNullable(response)
            .map(GetMetricDataResponse::metricDataResults)
            .filter(results -> !results.isEmpty())
            .map(results -> results.get(0))
            .map(MetricDataResult::values)
            .filter(values -> !values.isEmpty())
            .map(values -> values.get(0))
            .map(value -> Math.round(value))
            .orElse(0L);
        return numberOfObjects;
    }
}
