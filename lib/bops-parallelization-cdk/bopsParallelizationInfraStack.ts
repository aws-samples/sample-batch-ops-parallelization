import { Construct } from 'constructs';
import { aws_ec2 as ec2, Stack, StackProps } from 'aws-cdk-lib';
import { DynamoDBResources } from './persistence/resources/dynamodb';
import { LambdaResource } from './compute/resources/lambda';
import { Vpc } from 'aws-cdk-lib/aws-ec2';
import {
  StateMachine,
  DefinitionBody,
  StateMachineType,
  Chain,
  Choice,
  Condition,
  Pass,
  Wait,
  WaitTime,
  LogLevel
} from 'aws-cdk-lib/aws-stepfunctions';
import { LambdaInvoke } from 'aws-cdk-lib/aws-stepfunctions-tasks';
import { Function, Runtime, Code } from 'aws-cdk-lib/aws-lambda';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import { Effect, Policy, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { CfnOutput, Duration, RemovalPolicy } from 'aws-cdk-lib';
import { WORKFLOW_TABLE_PROPS } from './persistence/constants';
import {
  CONFIGURE_BUCKET_LAMBDA,
  CREATE_BUCKET_LAMBDA,
  SETUP_REPLICATION_LAMBDA,
  MONITOR_LAMBDA,
  POST_REPLICATION_LAMBDA,
  SETUP_ROLLBACK_LAMBDA,
} from './compute/constants';
import { NagSuppressions } from 'cdk-nag';

export class BopsParallelizationInfraStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    // 1. Create DynamoDB resources
    const dynamoDBResources = new DynamoDBResources(this, 'DynamoDBResources');

    // 2. Create VPC
    const vpc = new Vpc(this, 'Vpc', {
      maxAzs: 2,
      natGateways: 1,
    });

    const flowLogGroup = new LogGroup(this, 'VpcFlowLogGroup', {
      retention: RetentionDays.ONE_WEEK,
      removalPolicy: RemovalPolicy.DESTROY
    });

    new ec2.FlowLog(this, 'VpcFlowLog', {
      resourceType: ec2.FlowLogResourceType.fromVpc(vpc),
      destination: ec2.FlowLogDestination.toCloudWatchLogs(flowLogGroup),
      trafficType: ec2.FlowLogTrafficType.ALL
    });

    // 3. Create Lambda functions
    const createS3BucketLambda = new LambdaResource(this, CREATE_BUCKET_LAMBDA.lambdaName, {
      componentName: CREATE_BUCKET_LAMBDA.componentName,
      functionName: CREATE_BUCKET_LAMBDA.lambdaName,
      handler: CREATE_BUCKET_LAMBDA.handler,
      roleName: `${CREATE_BUCKET_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: dynamoDBResources
    });

    const configureS3BucketLambda = new LambdaResource(this, CONFIGURE_BUCKET_LAMBDA.lambdaName, {
      componentName: CONFIGURE_BUCKET_LAMBDA.componentName,
      functionName: CONFIGURE_BUCKET_LAMBDA.lambdaName,
      handler: CONFIGURE_BUCKET_LAMBDA.handler,
      roleName: `${CONFIGURE_BUCKET_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: dynamoDBResources,
    });

    const s3MonitorLambda = new LambdaResource(this, MONITOR_LAMBDA.lambdaName, {
      componentName: MONITOR_LAMBDA.componentName,
      functionName: MONITOR_LAMBDA.lambdaName,
      handler: MONITOR_LAMBDA.handler,
      roleName: `${MONITOR_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: dynamoDBResources,
    });

    const s3ReplicationSetupLambda = new LambdaResource(this, SETUP_REPLICATION_LAMBDA.lambdaName, {
      componentName: SETUP_REPLICATION_LAMBDA.componentName,
      functionName: SETUP_REPLICATION_LAMBDA.lambdaName,
      handler: SETUP_REPLICATION_LAMBDA.handler,
      roleName: `${SETUP_REPLICATION_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: dynamoDBResources
    });

    const s3RollbackSetupLambda = new LambdaResource(this, SETUP_ROLLBACK_LAMBDA.lambdaName, {
      componentName: SETUP_ROLLBACK_LAMBDA.componentName,
      functionName: SETUP_ROLLBACK_LAMBDA.lambdaName,
      handler: SETUP_ROLLBACK_LAMBDA.handler,
      roleName: `${SETUP_ROLLBACK_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: dynamoDBResources,
    });

    const s3PostReplicationLambda = new LambdaResource(this, POST_REPLICATION_LAMBDA.lambdaName, {
      componentName: POST_REPLICATION_LAMBDA.componentName,
      functionName: POST_REPLICATION_LAMBDA.lambdaName,
      handler: POST_REPLICATION_LAMBDA.handler,
      roleName: `${POST_REPLICATION_LAMBDA.lambdaName}Role`,
      vpc: vpc,
      dbResources: dynamoDBResources,
    });

    // 4. Create IAM policies
    const assumeRolePolicy = new Policy(this, 'AssumeRolePolicy', {
      policyName: 'assume-role-access',
      statements: [new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['sts:AssumeRole'],
        resources: ['*'],
      })],
    });

    const monitorPolicy = new Policy(this, 'S3AMonitorCloudWatchPolicy', {
      policyName: 'S3AMonitorCloudWatchPolicy',
      statements: [new PolicyStatement({
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
      })],
    });

    // Attach policies to Lambda roles
    [createS3BucketLambda, configureS3BucketLambda, s3MonitorLambda,
      s3ReplicationSetupLambda, s3RollbackSetupLambda, s3PostReplicationLambda]
      .forEach(lambda => lambda.lambdaRole.attachInlinePolicy(assumeRolePolicy));

    s3MonitorLambda.lambdaRole.attachInlinePolicy(monitorPolicy);

    // 5. Create Step Functions workflow
    const s3CreateBucketTask = new LambdaInvoke(this, 'S3CreateBucketTask', {
      stateName: 'CreateBucket',
      lambdaFunction: createS3BucketLambda.lambdaFunction,
      outputPath: '$.Payload'
    });

    const s3ConfigureBucketTask = new LambdaInvoke(this, 'S3ConfigureBucketTask', {
      stateName: 'ConfigureBucket',
      lambdaFunction: configureS3BucketLambda.lambdaFunction,
      outputPath: '$.Payload'
    });

    const s3ReplicationSetupTask = new LambdaInvoke(this, 'S3ReplicationSetupTask', {
      stateName: 'ReplicationSetup',
      lambdaFunction: s3ReplicationSetupLambda.lambdaFunction,
      outputPath: '$.Payload'
    });

    const s3MonitorBackfillTask = new LambdaInvoke(this, 'S3MonitorBackfillTask', {
      stateName: 'MonitorBackfill',
      lambdaFunction: s3MonitorLambda.lambdaFunction,
      outputPath: '$.Payload'
    });

    const s3PostReplicationTask = new LambdaInvoke(this, 'S3PostReplicationTask', {
      stateName: 'PostReplication',
      lambdaFunction: s3PostReplicationLambda.lambdaFunction,
      outputPath: '$.Payload'
    });

    const waitForBackfillComplete = new Wait(this, 'WaitForBackfillCompleteSignal', {
      time: WaitTime.duration(Duration.seconds(60))
    });

    const closeWorkflow = new Pass(this, 'CloseWorkflow');
    const createBucketChoice = new Choice(this, 'Check S3CreateBucket response');
    const configureLambdaChoice = new Choice(this, 'Check ConfigureLambda response');
    const replicationSetupChoice = new Choice(this, 'Check S3ReplicationSetupLambda response');
    const monitorChoice = new Choice(this, 'Check S3Monitor response');
    const startAtChoice = new Choice(this, 'Check startAt state');

    const mainPath = s3CreateBucketTask.next(createBucketChoice);
    const replicationPath = s3ReplicationSetupTask;

    const workflowChain = Chain.start(startAtChoice
      .when(Condition.and(
        Condition.isPresent('$.startAt'),
        Condition.stringEquals('$.startAt', "SetupReplication")
      ), replicationPath)
      .otherwise(mainPath)
    );

    createBucketChoice
      .when(Condition.stringEquals('$.status', 'CREATED'), s3ConfigureBucketTask)
      .otherwise(closeWorkflow);

    s3ConfigureBucketTask.next(configureLambdaChoice);

    configureLambdaChoice
      .when(Condition.stringEquals('$.status', 'FINISHED'), s3ReplicationSetupTask)
      .otherwise(closeWorkflow);

    s3ReplicationSetupTask.next(replicationSetupChoice);

    replicationSetupChoice
      .when(Condition.stringEquals('$.status', 'FINISHED'), s3MonitorBackfillTask)
      .otherwise(closeWorkflow);

    s3MonitorBackfillTask.next(monitorChoice);

    monitorChoice
      .when(Condition.or(
        Condition.stringEquals('$.status', 'RUNNING'),
        Condition.stringEquals('$.status', 'STOPPING')
      ), waitForBackfillComplete)
      .when(Condition.stringEquals('$.status', 'STOPPED'), s3PostReplicationTask)
      .when(Condition.stringEquals('$.status', 'WAITING'), s3PostReplicationTask)
      .otherwise(closeWorkflow);

    waitForBackfillComplete.next(s3MonitorBackfillTask);
    s3PostReplicationTask.next(closeWorkflow);

    const s3aWorkflowlogGroup = new LogGroup(this, 'S3AWorkflowLogGroup', {
      retention: RetentionDays.ONE_WEEK,
      removalPolicy: RemovalPolicy.DESTROY
    });

    const stateMachine = new StateMachine(this, 'S3AWorkflow', {
      stateMachineName: "S3AWorkflow",
      definitionBody: DefinitionBody.fromChainable(workflowChain),
      stateMachineType: StateMachineType.STANDARD,
      tracingEnabled: true,
      logs: {
        destination: s3aWorkflowlogGroup,
        level: LogLevel.ALL,
        includeExecutionData: true
      }
    });

    // 6. Create API Lambda
    const lambdaLogGroup = new LogGroup(this, 'LambdaLogGroup', {
      retention: RetentionDays.ONE_WEEK,
      logGroupName: '/aws/lambda/bops-parallelization-service-lambda',
      removalPolicy: RemovalPolicy.DESTROY
    });

    const dynamoDbTable = dynamoDBResources.executionTable;

    const apiHandler = new Function(this, 'BOPSParallelMainHandler', {
      vpc: vpc,
      functionName: 'BOPSParallelMainHandler',
      runtime: Runtime.JAVA_21,
      handler: 'com.amazon.bopspar.service.LambdaMain::handleRequest',
      code: Code.fromAsset('../build/libs/BOPSParallelization-1.0-SNAPSHOT-all.jar'),
      timeout: Duration.seconds(30),
      memorySize: 1024,
      logGroup: lambdaLogGroup,
      environment: {
        DYNAMODB_TABLE_NAME: dynamoDbTable.tableName,
        DYNAMMODB_TABLE_ARN: dynamoDbTable.tableArn,
        WORKFLOW_STATE_MACHINE_ARN: stateMachine.stateMachineArn,
      }
    });

    dynamoDbTable.grantReadWriteData(apiHandler);

    const stepFunctionPolicy = new Policy(this, 'StepFunctionPolicy', {
      policyName: 'step-function-policy',
      statements: [new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['states:SendTaskSuccess', 'states:StartExecution'],
        resources: [stateMachine.stateMachineArn]
      })]
    });

    apiHandler.role?.attachInlinePolicy(assumeRolePolicy);
    apiHandler.role?.attachInlinePolicy(stepFunctionPolicy);

    new CfnOutput(this, 'BOPS-Parallel-Handler', {
      value: apiHandler.functionArn,
      description: 'BOPS Parallelization start workflow handler',
    });

    // NAG Suppressions
    // Suppress wildcard permissions for AssumeRole policy - needed for cross-account access
    NagSuppressions.addResourceSuppressions(assumeRolePolicy, [
      {
        id: 'AwsSolutions-IAM5',
        reason: 'AssumeRole policy requires wildcard permissions to assume roles across different accounts and regions for S3 replication setup',
        appliesTo: ['Resource::*']
      }
    ]);

    // Suppress latest-runtime check for the Java API handler - JAVA_21 is the latest supported Java LTS runtime
    NagSuppressions.addResourceSuppressions(apiHandler, [
      {
        id: 'AwsSolutions-L1',
        reason: 'Function uses Runtime.JAVA_21, the latest Java LTS runtime supported by AWS Lambda.'
      }
    ]);

    // Suppress wildcard permissions for CloudWatch monitoring policy - needed for metric operations
    NagSuppressions.addResourceSuppressions(monitorPolicy, [
      {
        id: 'AwsSolutions-IAM5',
        reason: 'CloudWatch monitoring requires wildcard permissions to create and manage alarms and dashboards across different resources',
        appliesTo: ['Resource::*']
      }
    ]);

    // Suppress Step Functions wildcard permissions by path - needed for Lambda invocation
    NagSuppressions.addResourceSuppressionsByPath(this, '/BOPSParallelizationStack/S3AWorkflow/Role/DefaultPolicy/Resource', [
      {
        id: 'AwsSolutions-IAM5',
        reason: 'Step Functions requires wildcard permissions on Lambda function ARNs to invoke different versions and aliases'
      }
    ]);

    // Suppress AWS managed policies for main API handler - these are standard Lambda execution policies
    NagSuppressions.addResourceSuppressionsByPath(this, '/BOPSParallelizationStack/BOPSParallelMainHandler/ServiceRole/Resource', [
      {
        id: 'AwsSolutions-IAM4',
        reason: 'AWS managed policies AWSLambdaBasicExecutionRole and AWSLambdaVPCAccessExecutionRole are standard policies for Lambda functions in VPC',
        appliesTo: [
          'Policy::arn:<AWS::Partition>:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole',
          'Policy::arn:<AWS::Partition>:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole'
        ]
      }
    ]);
  }
}
