package com.amazon.bopspar.service.requests;

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
public class WorkflowRequest {
    private String  workflowName;
    private String  namespaceID;
    private String taskToken;
}
