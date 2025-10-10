package com.amazon.bopspar.service.responses;

import com.amazon.bopspar.persistence.manager.WorkflowStatus;
import com.amazon.bopspar.persistence.model.WorkFlowModel;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;


/**
 * Builder for different types of WorkflowResponse objects based on success/error.
 */

@Log4j2
public class WorkflowResponseBuilder {

    /**
     * Builds a success Workflow response.
     *
     * @param workflowDetails The WorkflowModel containing required details
     * @param status The status of the workflow
     * @return An WorkflowResponse object with success details
     */
    public static WorkflowResponse buildSuccessResponse(final WorkFlowModel workflowDetails,
                                                        final WorkflowStatus status) {
        return WorkflowResponse.builder()
                .statusCode(200)
                .namespaceID(workflowDetails.getNamespaceID())
                .workflowName(workflowDetails.getWorkflowName())
                .sourceBucketName(getBucketNameOrNull(workflowDetails.getSourceBucketARN()))
                .destinationBucketName(getBucketNameOrNull(workflowDetails.getDestBucketARN()))
                .status(String.valueOf(status))
                .build();
    }

    /**
     * Builds an Workflow response for AWS service-related exceptions, including S3Exceptions.
     *
     * @param workflowDetails The workflow model containing required details
     * @param status The status of the workflow
     * @param exception The AWS service exception that occurred
     * @return An WorkflowResponse object with AWS service error details
     */
    public static WorkflowResponse buildServiceErrorResponse(final WorkFlowModel workflowDetails,
                                                             final WorkflowStatus status,
                                                             final AwsServiceException exception) {
        AwsErrorDetails errorDetailsObj = exception.awsErrorDetails();
        WorkflowResponse.ErrorDetails errorDetails = WorkflowResponse.ErrorDetails
                .builder()
                .errorCode(errorDetailsObj != null ? errorDetailsObj.errorCode() : null)
                .serviceMessage(errorDetailsObj != null ? errorDetailsObj.errorMessage() : null)
                .errorClass(exception.getClass().getName())
                .build();

        return WorkflowResponse.builder()
                .namespaceID(workflowDetails.getNamespaceID())
                .workflowName(workflowDetails.getWorkflowName())
                .sourceBucketName(getBucketNameOrNull(workflowDetails.getSourceBucketARN()))
                .destinationBucketName(getBucketNameOrNull(workflowDetails.getDestBucketARN()))
                .status(String.valueOf(status))
                .statusCode(exception.statusCode())
                .errorDetails(errorDetails)
                .build();
    }

    /**
     * Builds an Workflow response for runtime exceptions with HTTP 500 status code.
     *
     * @param workflowDetails The workflow model containing required details
     * @param status The status of the workflow
     * @param exception The runtime exception that occurred
     * @return An WorkflowResponse object with runtime error details
     */
    public static WorkflowResponse buildRuntimeErrorResponse(final WorkFlowModel workflowDetails,
                                                             final WorkflowStatus status,
                                                             final RuntimeException exception) {
        WorkflowResponse.ErrorDetails errorDetails = WorkflowResponse.ErrorDetails
                .builder()
                .errorCode("INTERNAL_ERROR")
                .serviceMessage(exception.getMessage())
                .errorClass(exception.getClass().getName())
                .build();

        return WorkflowResponse.builder()
                .namespaceID(workflowDetails.getNamespaceID())
                .workflowName(workflowDetails.getWorkflowName())
                .sourceBucketName(getBucketNameOrNull(workflowDetails.getSourceBucketARN()))
                .destinationBucketName(getBucketNameOrNull(workflowDetails.getDestBucketARN()))
                .status(String.valueOf(status))
                .statusCode(500)
                .errorDetails(errorDetails)
                .build();
    }

    private static String getBucketNameOrNull(final String bucketArn) {
        if (bucketArn == null) {
            return null;
        }
        return Arn.fromString(bucketArn).resourceAsString();
    }
}
