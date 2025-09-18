package com.amazon.tdm.s3a.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ListWorkflowsResponse {

    private List<Workflow> workflows;
}
