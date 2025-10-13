import { DynamoDBResources } from '../persistence/resources/dynamodb';
import { Stack, StackProps } from 'aws-cdk-lib';
import { LambdaResource } from './resources/lambda';
import {
  INVENTORY_CONFIG_SETUP_LAMBDA,
  MANIFEST_SPLIT_LAMBDA,
  POLL_FOR_GLUE_JOB_LAMBDA,
  POLL_FOR_INVENTORY_REPORT_MANIFEST_LAMBDA,
} from './constants';
import { Effect, Policy, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { Vpc } from 'aws-cdk-lib/aws-ec2';

export interface InventoryReportConfigStackProps extends StackProps {
  vpc: Vpc;
  dbResources: DynamoDBResources;
}

export interface LambdaManifestSplitMap {
  s3InventoryConfigSetupLambda: LambdaResource;
  s3PollForInventoryReportManifestLambda: LambdaResource;
  manifestSplitLambda: LambdaResource;
  s3PollForGlueJobLambda: LambdaResource;
}


export class InventoryReportConfigStack extends Stack {
  readonly manifestlambdaRoleArn: string;
  readonly pollForGlueJobRoleArn: string;
  readonly pollForInventoryReportManifestRoleArn: string;
  readonly inventoryConfigLambdaRoleArn: string;
  readonly manifestSplitLambdas: LambdaManifestSplitMap;

  constructor(scope: Construct, id: string, props: InventoryReportConfigStackProps) {
    super(scope, id, props);

    const s3InventoryConfigSetupLambda = new LambdaResource(this, INVENTORY_CONFIG_SETUP_LAMBDA.lambdaName, {
      componentName: INVENTORY_CONFIG_SETUP_LAMBDA.componentName,
      functionName: INVENTORY_CONFIG_SETUP_LAMBDA.lambdaName,
      handler: INVENTORY_CONFIG_SETUP_LAMBDA.handler,
      roleName: `${INVENTORY_CONFIG_SETUP_LAMBDA.lambdaName}Role`,
      vpc: props.vpc,
      dbResources: props.dbResources,
      env: {
        AWS_ACCOUNT_ID: props.env?.account as string,
      },
    });

    const s3PollForInventoryReportManifestLambda = new LambdaResource(
      this,
      POLL_FOR_INVENTORY_REPORT_MANIFEST_LAMBDA.lambdaName,
      {
        componentName: POLL_FOR_INVENTORY_REPORT_MANIFEST_LAMBDA.componentName,
        functionName: POLL_FOR_INVENTORY_REPORT_MANIFEST_LAMBDA.lambdaName,
        handler: POLL_FOR_INVENTORY_REPORT_MANIFEST_LAMBDA.handler,
        roleName: `${POLL_FOR_INVENTORY_REPORT_MANIFEST_LAMBDA.lambdaName}Role`,
        vpc: props.vpc,
        dbResources: props.dbResources,
        env: {
          AWS_ACCOUNT_ID: props.env?.account as string,
        },
      },
    );

    const manifestSplitLambda = new LambdaResource(this, MANIFEST_SPLIT_LAMBDA.lambdaName, {
      componentName: MANIFEST_SPLIT_LAMBDA.componentName,
      functionName: MANIFEST_SPLIT_LAMBDA.lambdaName,
      handler: MANIFEST_SPLIT_LAMBDA.handler,
      roleName: `${MANIFEST_SPLIT_LAMBDA.lambdaName}Role`,
      vpc: props.vpc,
      dbResources: props.dbResources,
      env: {
        AWS_ACCOUNT_ID: props.env?.account as string,
      },
    });

    const s3PollForGlueJobLambda = new LambdaResource(this, POLL_FOR_GLUE_JOB_LAMBDA.lambdaName, {
      componentName: POLL_FOR_GLUE_JOB_LAMBDA.componentName,
      functionName: POLL_FOR_GLUE_JOB_LAMBDA.lambdaName,
      handler: POLL_FOR_GLUE_JOB_LAMBDA.handler,
      roleName: `${POLL_FOR_GLUE_JOB_LAMBDA.lambdaName}Role`,
      vpc: props.vpc,
      dbResources: props.dbResources,
      env: {
        AWS_ACCOUNT_ID: props.env?.account as string,
      },
    });

    const policyStatement = new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ['sts:AssumeRole'],
      resources: ['*'],
    });

    const policy = new Policy(this, 'AssumeRolePolicy', {
      policyName: 'assume-role-access',
      statements: [policyStatement],
    });

    s3InventoryConfigSetupLambda.lambdaRole.attachInlinePolicy(policy);
    manifestSplitLambda.lambdaRole.attachInlinePolicy(policy);
    s3PollForInventoryReportManifestLambda.lambdaRole.attachInlinePolicy(policy);
    s3PollForGlueJobLambda.lambdaRole.attachInlinePolicy(policy);

    this.manifestlambdaRoleArn = manifestSplitLambda.lambdaRole.roleArn;
    this.pollForInventoryReportManifestRoleArn = s3PollForInventoryReportManifestLambda.lambdaRole.roleArn;
    this.pollForGlueJobRoleArn = s3PollForGlueJobLambda.lambdaRole.roleArn;
    this.inventoryConfigLambdaRoleArn = s3InventoryConfigSetupLambda.lambdaRole.roleArn;

    this.manifestSplitLambdas = {
      s3InventoryConfigSetupLambda,
      s3PollForInventoryReportManifestLambda,
      manifestSplitLambda,
      s3PollForGlueJobLambda
    }
  }
}
