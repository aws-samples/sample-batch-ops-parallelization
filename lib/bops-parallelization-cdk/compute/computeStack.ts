import { DynamoDBResources } from '../persistence/resources/dynamodb';
import {App, Stack, StackProps} from 'aws-cdk-lib';
import { LambdaResource } from './resources/lambda';
import {
  CONFIGURE_BUCKET_LAMBDA,
  CREATE_BUCKET_LAMBDA,
  SETUP_REPLICATION_LAMBDA,
  MONITOR_LAMBDA,
  POST_REPLICATION_LAMBDA,
  SETUP_ROLLBACK_LAMBDA,
  WAIT_FOR_CUSTOMER_ACK_LAMBDA,
} from '../compute/constants';
import { Effect, Policy, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import {Vpc} from "aws-cdk-lib/aws-ec2";
import { Construct } from 'constructs';

export interface ComputeStackProps extends StackProps {
  vpc: Vpc;
  dbResources: DynamoDBResources;
}

export interface LambdaMap {
  createS3BucketLambda: LambdaResource;
  configureS3BucketLambda: LambdaResource;
  s3MonitorLambda: LambdaResource;
  s3ReplicationSetupLambda: LambdaResource;
  s3RollbackSetupLambda: LambdaResource;
  s3PostReplicationLambda: LambdaResource;
  waitForCustomerAckLambda: LambdaResource;
}

export class ComputeStack extends Stack {
  readonly disambiguator: string;
  readonly dynamoDBResources: DynamoDBResources;
  readonly lambdas: LambdaMap;

  constructor(scope: Construct, id: string, props: ComputeStackProps) {
    super(scope, id, props);

    const vpc = props.vpc;
    this.dynamoDBResources = props.dbResources;

    const createS3BucketLambda = new LambdaResource(this, CREATE_BUCKET_LAMBDA.lambdaName, {
      componentName: CREATE_BUCKET_LAMBDA.componentName,
      functionName: CREATE_BUCKET_LAMBDA.lambdaName,
      handler: CREATE_BUCKET_LAMBDA.handler,
      roleName: `${CREATE_BUCKET_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: this.dynamoDBResources
    });

    const configureS3BucketLambda = new LambdaResource(this, CONFIGURE_BUCKET_LAMBDA.lambdaName, {
      componentName: CONFIGURE_BUCKET_LAMBDA.componentName,
      functionName: CONFIGURE_BUCKET_LAMBDA.lambdaName,
      handler: CONFIGURE_BUCKET_LAMBDA.handler,
      roleName: `${CONFIGURE_BUCKET_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: this.dynamoDBResources,
    });

    const s3MonitorLambda = new LambdaResource(this, MONITOR_LAMBDA.lambdaName, {
      componentName: MONITOR_LAMBDA.componentName,
      functionName: MONITOR_LAMBDA.lambdaName,
      handler: MONITOR_LAMBDA.handler,
      roleName: `${MONITOR_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: this.dynamoDBResources,
    });

    const s3ReplicationSetupLambda = new LambdaResource(this, SETUP_REPLICATION_LAMBDA.lambdaName, {
      componentName: SETUP_REPLICATION_LAMBDA.componentName,
      functionName: SETUP_REPLICATION_LAMBDA.lambdaName,
      handler: SETUP_REPLICATION_LAMBDA.handler,
      roleName: `${SETUP_REPLICATION_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: this.dynamoDBResources
    });

    const s3RollbackSetupLambda = new LambdaResource(this, SETUP_ROLLBACK_LAMBDA.lambdaName, {
      componentName: SETUP_ROLLBACK_LAMBDA.componentName,
      functionName: SETUP_ROLLBACK_LAMBDA.lambdaName,
      handler: SETUP_ROLLBACK_LAMBDA.handler,
      roleName: `${SETUP_ROLLBACK_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: this.dynamoDBResources,
    });

    const s3PostReplicationLambda = new LambdaResource(this, POST_REPLICATION_LAMBDA.lambdaName, {
      componentName: POST_REPLICATION_LAMBDA.componentName,
      functionName: POST_REPLICATION_LAMBDA.lambdaName,
      handler: POST_REPLICATION_LAMBDA.handler,
      roleName: `${POST_REPLICATION_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: this.dynamoDBResources,
    });

    const waitForCustomerAckLambda = new LambdaResource(this, WAIT_FOR_CUSTOMER_ACK_LAMBDA.lambdaName, {
      componentName: WAIT_FOR_CUSTOMER_ACK_LAMBDA.componentName,
      functionName: WAIT_FOR_CUSTOMER_ACK_LAMBDA.lambdaName,
      handler: WAIT_FOR_CUSTOMER_ACK_LAMBDA.handler,
      roleName: `${WAIT_FOR_CUSTOMER_ACK_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: this.dynamoDBResources
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

    createS3BucketLambda.lambdaRole.attachInlinePolicy(policy);
    configureS3BucketLambda.lambdaRole.attachInlinePolicy(policy);
    s3MonitorLambda.lambdaRole.attachInlinePolicy(policy);
    s3ReplicationSetupLambda.lambdaRole.attachInlinePolicy(policy);
    s3RollbackSetupLambda.lambdaRole.attachInlinePolicy(policy);
    s3PostReplicationLambda.lambdaRole.attachInlinePolicy(policy);
    waitForCustomerAckLambda.lambdaRole.attachInlinePolicy(policy);

    //2024-11-04: This policy is to allow MonitorLambda to create CW Alarms and Publish custom metrics
    //2024-11-27: Adding permissions to create CW dashboards w/SDK
    const monitorPolicyStatement = new PolicyStatement({
      effect: Effect.ALLOW,
      actions: [
        'cloudwatch:PutMetricAlarm',
        'cloudwatch:PutMetricData',
        'cloudwatch:DescribeAlarms',
        'cloudwatch:SetAlarmState',
        'cloudwatch:PutCompositeAlarm',
        'cloudwatch:PutDashboard',
        'cloudwatch:ListDashboards',
        'cloudwatch:GetDashboard',
        'cloudwatch:DeleteDashboards',
      ],
      resources: ['*'],
    });

    const monitorPolicy = new Policy(this, 'S3AMonitorCloudWatchPolicy', {
      policyName: 'S3AMonitorCloudWatchPolicy',
      statements: [monitorPolicyStatement],
    });
    s3MonitorLambda.lambdaRole.attachInlinePolicy(monitorPolicy);

    this.lambdas = {
      createS3BucketLambda,
      configureS3BucketLambda,
      s3MonitorLambda,
      s3ReplicationSetupLambda,
      s3RollbackSetupLambda,
      s3PostReplicationLambda,
      waitForCustomerAckLambda,
    };
  }
}