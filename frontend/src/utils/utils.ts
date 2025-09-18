import { format } from "date-fns-tz";
import type { CreateWorkflowRequest, Workflow, WorkflowView } from "../types";
import { v4 } from 'uuid';

export const getStatusIndicator = (status: string) => {
    switch (status) {
        case 'RUNNING': return 'in-progress';
        case 'FINISHED': return 'success';
        case 'FAILED': return 'error';
        case 'WAITING': return 'warning';
        default: return 'info';
    }
}

export const getStatusPopoverContent = (status: string) => {
    switch (status) {
        case 'WAITING': return 'Stop traffic to your source bucket and acknowledge to continue your migration.';
        default: return;
    }
}

export const getStatusPopoverHeader = (status: string) => {
    switch (status) {
        case 'WAITING': return 'Migration waiting';
        default: return;
    }
}


export const toWorkflowFromCreateWorkflowRequest = (request: CreateWorkflowRequest): Workflow => {
    return {
        namespaceID: `ns-${getShortRandomUUID()}`,
        workflowName: `wn-${getShortRandomUUID()}`,
        workflowType: "S3_MIGRATOR",
        sourceBucketARN: getS3BucketArn(request.sourceBucketName),
        destBucketARN: getS3BucketArn(request.destBucketName),
        sourceRoleARN: getRoleArn(request.sourceAccountNumber),
        destRoleARN: getRoleArn(request.destAccountNumber),
        sourceAccountNumber: request.sourceAccountNumber,
        destAccountNumber: request.destAccountNumber,
        sourceRegion: request.sourceRegion,
        destRegion: request.destRegion,
        status: "READY",
        runtimeConfig: {
            isReplicationTimeControlEnabled: request.runtimeConfig.isReplicationTimeControlEnabled
        }
    }
}

export const toWorkflowViewFromWorkflow = (workflow: Workflow): WorkflowView => {
    return {
        namespaceID: workflow.namespaceID,
        workflowName: workflow.workflowName,
        sourceBucketName: getS3BucketName(workflow.sourceBucketARN),
        destBucketName: getS3BucketName(workflow.destBucketARN),
        sourceRoleARN: workflow.sourceRoleARN,
        destRoleARN: workflow.destRoleARN,
        sourceAccountNumber: workflow.sourceAccountNumber,
        destAccountNumber: workflow.destAccountNumber,
        sourceRegion: workflow.sourceRegion,
        destRegion: workflow.destRegion,
        status: workflow.status,
        createdAt: formatDateTime(workflow.createdAt),
        startedAt: formatDateTime(workflow.startedAt),
        backfillCompletedAt: formatDateTime(workflow.backfillCompletedAt),
        completedAt: formatDateTime(workflow.completedAt),
        runtimeConfig: {
            dashboardUrl: workflow.runtimeConfig?.dashboardUrl,
            isReplicationTimeControlEnabled: workflow.runtimeConfig?.isReplicationTimeControlEnabled
        }
    }
}

const getShortRandomUUID = () => {
    const uuid = v4();
    return uuid.substring(uuid.lastIndexOf('-') + 1);
}

const getS3BucketArn = (bucketName : string) => {
    return `arn:aws:s3:::${bucketName}`
}

const getS3BucketName = (bucketArn : string) => {
    return bucketArn.split('arn:aws:s3:::')[1];
}

const getRoleArn = (awsAccountNumber : string) => {
    return `arn:aws:iam::${awsAccountNumber}:role/s3a-bucket-permissions`
}

const formatDateTime = (date: string | undefined) => {
    if(!date){
        return "N/A";
    }
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const d = new Date(date);
    
    return format(d, 'MMMM d, yyyy, h:mm a zzz', { timeZone });
}