package com.amazon.bopspar.service.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class OrcaResponse {
    //FAILED, FINSIHED, CREATED
    private String status;
    //200, 500, or s3Exception.statusCode()
    private int statusCode;
    private String workflowName;
    private String namespaceID;
    private String sourceBucketName;
    private String destinationBucketName;
    private ErrorDetails errorDetails;

    @Builder
    @AllArgsConstructor
    @Data
    public static class ErrorDetails {
        private final String errorClass;
        private final String errorCode;
        private final String serviceMessage;
    }

}





