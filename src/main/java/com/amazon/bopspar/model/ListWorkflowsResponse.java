package com.amazon.bopspar.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ListWorkflowsResponse {

    private List<Workflow> workflows;
}
