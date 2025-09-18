package com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig;

import com.amazon.tdm.s3a.model.InvalidInputException;
import software.amazon.awssdk.arns.Arn;

/**
 * Utility class providing helper methods for S3 Inventory Report Configuration operations.
 * This class contains static methods to generate inventory configuration IDs and paths.
 */
public class S3InventoryReportConfigServiceUtils {

    private static final String INVENTORY_CONFIG_NAME_PREFIX = "S3A-";
    private static final Integer S3_BUCKET_NAME_TRUNCATION_LENGTH = 57;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private S3InventoryReportConfigServiceUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates an inventory config id based on the inventory reports bucket name.
     * The inventory config name must be less than 63 characters, and the S3 bucket name can be up to
     * 63 characters long. We need to truncate the bucket name to 57 characters to allow for the
     * inventory config name to be less than 63 characters long.
     * @param inventoryReportsBucketArn The ARN of the inventory reports bucket
     * @return The generated inventory config ID
     * @throws InvalidInputException if the provided ARN is null, empty, or invalid
     */
    public static String generateInventoryConfigReportId(final String inventoryReportsBucketArn) {
        if (inventoryReportsBucketArn == null || inventoryReportsBucketArn.isEmpty()) {
            throw new InvalidInputException("Inventory reports bucket ARN cannot be null or empty");
        }
        try {
            final String inventoryReportConfigName = Arn.fromString(inventoryReportsBucketArn).resourceAsString();
            return INVENTORY_CONFIG_NAME_PREFIX + inventoryReportConfigName.substring(0,
                    Math.min(inventoryReportConfigName.length(), S3_BUCKET_NAME_TRUNCATION_LENGTH)
            );
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid ARN format: " + inventoryReportsBucketArn, e);
        }
    }

    /**
     * Generates an inventory configuration path by combining the source bucket ARN and region.
     * The path is used to organize inventory reports in the destination bucket.
     *
     * @param sourceBucketArn The ARN of the source bucket being inventoried
     * @param region The AWS region where the inventory configuration is being created
     * @return A string containing the generated inventory configuration path
     * @throws InvalidInputException if the source bucket ARN or region is null, empty, or invalid
     */
    public static String generateInventoryConfigPath(final String sourceBucketArn, final String region) {
        if (sourceBucketArn == null || sourceBucketArn.isEmpty()) {
            throw new InvalidInputException("Inventory reports bucket ARN cannot be null or empty");
        }
        if (region == null || region.isEmpty()) {
            throw new InvalidInputException("Region cannot be null or empty");
        }
        try {
            final String sourceBucketName = Arn.fromString(sourceBucketArn).resourceAsString();
            return INVENTORY_CONFIG_NAME_PREFIX + sourceBucketName + "-" + region;
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid ARN format: " + sourceBucketArn, e);
        }
    }
}
