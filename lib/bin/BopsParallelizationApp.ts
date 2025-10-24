#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { BopsParallelizationInfraStack } from '../bops-parallelization-cdk/bopsParallelizationInfraStack';

const app = new cdk.App();

const bopsParallelizationApp = new BopsParallelizationInfraStack(app, 'BOPSParallelizationStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION
  }
});
