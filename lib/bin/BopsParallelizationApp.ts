#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { BopsParallelizationInfraStack } from '../bops-parallelization-cdk/bopsParallelizationInfraStack';
import { S3AResourcesIAMStack } from '../bops-parallelization-cdk/iam/iamStack';
import { GlueStack } from '../bops-parallelization-cdk/glue/glueStack';
import { Aspects } from "aws-cdk-lib";
import { AwsSolutionsChecks, NagReportFormat } from "cdk-nag";

const app = new cdk.App();

// Shared environment so every stack deploys to the same account/region.
const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION
};

const bopsParallelizationApp = new BopsParallelizationInfraStack(app, 'BOPSParallelizationStack', {
  env
});

// Deploy the IAM stack with the required Lambda role ARNs
const iamStack = new S3AResourcesIAMStack(app, 'BOPSParallelizationIAMStack', {
  env,
  // These will be the Lambda role ARNs from the main stack
  manifestlambdaRoleArn: `arn:aws:iam::${process.env.CDK_DEFAULT_ACCOUNT}:role/S3ManifestSplitLambdaRole`,
  pollForGlueJobLambdaRoleArn: `arn:aws:iam::${process.env.CDK_DEFAULT_ACCOUNT}:role/S3PollGlueJobLambdaRole`,
  pollForInventoryReportManifestLambdaRoleArn: `arn:aws:iam::${process.env.CDK_DEFAULT_ACCOUNT}:role/S3PollManifestLambdaRole`,
  inventoryConfigLambdaArn: `arn:aws:iam::${process.env.CDK_DEFAULT_ACCOUNT}:role/S3InventoryConfigSetupLambdaRole`
});

// Deploy the Glue stack that defines the manifest-split-glue-job. It uses the
// Glue role created by the IAM stack and deploys to the same region as everything else.
// rowLimit (max rows per output manifest file) is read from CDK context so it can be
// configured per deployment via cdk.json or `-c rowLimit=...`; GlueStack defaults it to
// 5B and clamps it to the supported 1B-5B range.
const rowLimitContext = app.node.tryGetContext('rowLimit');
const glueStack = new GlueStack(app, 'BOPSParallelizationGlueStack', {
  env,
  glueJobRole: iamStack.glueJobRole,
  ...(rowLimitContext !== undefined ? { rowLimit: Number(rowLimitContext) } : {})
});
glueStack.addDependency(iamStack);

Aspects.of(bopsParallelizationApp).add(new AwsSolutionsChecks({
  verbose: true,
  reports: true,
  reportFormats: [NagReportFormat.CSV]
}));

Aspects.of(iamStack).add(new AwsSolutionsChecks({
  verbose: true,
  reports: true,
  reportFormats: [NagReportFormat.CSV]
}));

Aspects.of(glueStack).add(new AwsSolutionsChecks({
  verbose: true,
  reports: true,
  reportFormats: [NagReportFormat.CSV]
}));

app.synth();