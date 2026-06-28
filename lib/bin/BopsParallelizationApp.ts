#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { BopsParallelizationInfraStack } from '../bops-parallelization-cdk/bopsParallelizationInfraStack';
import { S3AResourcesIAMStack } from '../bops-parallelization-cdk/iam/iamStack';
import { Aspects } from "aws-cdk-lib";
import { AwsSolutionsChecks, NagReportFormat } from "cdk-nag";

const app = new cdk.App();

const bopsParallelizationApp = new BopsParallelizationInfraStack(app, 'BOPSParallelizationStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION
  }
});

const iamStack = new S3AResourcesIAMStack(app, 'BOPSParallelizationIAMStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION
  }
});

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

app.synth();