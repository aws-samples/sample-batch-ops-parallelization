#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { ServiceStack } from '../bops-parallelization-cdk/serviceStack';
import { PersistenceStack } from '../bops-parallelization-cdk/persistence/persistence_stack';
import { ComputeStack } from '../bops-parallelization-cdk/compute/computeStack';
import { S3AResourcesIAMStack } from '../bops-parallelization-cdk/iam/iamStack';
import { S3AMonitorStack } from '../bops-parallelization-cdk/monitor/s3aMonitorStack';
import { VpcStack } from '../bops-parallelization-cdk/compute/vpcStack';
import { WorkflowStack } from "../bops-parallelization-cdk/workflow/workflowStack";
import { ManifestSplitWorkflowStack } from "../bops-parallelization-cdk/workflow/manifestSplitWorkflowStack";
import { FrontendStack } from '../bops-parallelization-cdk/frontend/frontendStack';
import { InventoryReportConfigStack } from "../bops-parallelization-cdk/compute/inventoryReportConfigStack";

const app = new cdk.App();

const persistenceStack = new PersistenceStack(app, `PersistenceStack`, {
});

const vpcStack = new VpcStack(app, `VpcStack`, {
});

const computeStack = new ComputeStack(app, `ComputeStack`, {
  vpc: vpcStack.vpc,
  dbResources: persistenceStack.dynamoDBResources
});

computeStack.addDependency(vpcStack);
computeStack.addDependency(persistenceStack);

const workflowStack = new WorkflowStack(app, `WorkflowStack`, {
  lambdas: computeStack.lambdas
})

const inventoryReportConfigStack = new InventoryReportConfigStack(app, "InventoryReportConfigStack", {
  vpc: vpcStack.vpc,
  dbResources: persistenceStack.dynamoDBResources
})

const manifestSplitWorkflowStack = new ManifestSplitWorkflowStack(app, 'ManifestSplitWorkflowStack', {
  lambdas: { ...inventoryReportConfigStack.manifestSplitLambdas, ...computeStack.lambdas },
  workflowStateMachine: workflowStack.stateMachine
})

workflowStack.addDependency(computeStack);

const iamStack = new S3AResourcesIAMStack(app, `IAMStack`, {
  manifestlambdaRoleArn: inventoryReportConfigStack.manifestlambdaRoleArn,
  pollForGlueJobLambdaRoleArn: inventoryReportConfigStack.pollForGlueJobRoleArn,
  pollForInventoryReportManifestLambdaRoleArn: inventoryReportConfigStack.pollForInventoryReportManifestRoleArn,
  inventoryConfigLambdaArn: inventoryReportConfigStack.inventoryConfigLambdaRoleArn
});

const monitorStack = new S3AMonitorStack(app, `MonitorStack`, {
});

const serviceStack = new ServiceStack(app, `ServiceStack`, {
  vpc: vpcStack.vpc,
  workflowStateMachine: workflowStack.stateMachine,
  manifestSplitStateMachine: manifestSplitWorkflowStack.stateMachine
});

serviceStack.addDependency(vpcStack);
serviceStack.addDependency(workflowStack);
serviceStack.addDependency(iamStack);

const frontendStack = new FrontendStack(app, `FrontendStack`, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION
    }
});

frontendStack.addDependency(serviceStack);
