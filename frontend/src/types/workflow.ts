/**
 * Workflow related interfaces
 */

// Define workflow interface
export interface Workflow {
  namespaceID: string;
  workflowName: string;
  workflowType: string;
  sourceBucketARN: string;
  destBucketARN: string;
  sourceRoleARN: string;
  destRoleARN: string;
  sourceAccountNumber: string;
  destAccountNumber: string;
  sourceRegion: string;
  destRegion: string;
  status: string;
  createdAt?: string;
  startedAt?: string;
  backfillCompletedAt?: string;
  completedAt?: string;
  runtimeConfig?: RuntimeConfig;
}

export interface RuntimeConfig {
  dashboardUrl?: string;
  isReplicationTimeControlEnabled?: boolean;
}

export interface CreateWorkflowRequest {
  sourceBucketName: string;
  destBucketName: string;
  sourceAccountNumber: string;
  destAccountNumber: string;
  sourceRegion: string;
  destRegion: string;
  runtimeConfig: {
    isReplicationTimeControlEnabled: boolean;
  };
}

export interface WorkflowView {
  namespaceID: string;
  workflowName: string;
  sourceBucketName: string;
  destBucketName: string;
  sourceAccountNumber: string;
  destAccountNumber: string;
  sourceRegion: string;
  destRegion: string;
  sourceRoleARN: string;
  destRoleARN: string;
  status: string;
  createdAt?: string;
  startedAt?: string;
  backfillCompletedAt?: string;
  completedAt?: string;
  runtimeConfig?: {
    dashboardUrl?: string;
    isReplicationTimeControlEnabled?: boolean;
  };
}

export interface GetWorkflowResponse {
  workflow: Workflow;
}

export interface ListWorkflowsResponse {
  workflows: Workflow[];
}
