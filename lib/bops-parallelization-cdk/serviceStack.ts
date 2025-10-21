import { Construct } from 'constructs';

import { Code, Function, Runtime } from 'aws-cdk-lib/aws-lambda';
import { 
  Cors, 
  LambdaIntegration, 
  RestApi, 
  MethodLoggingLevel
} from 'aws-cdk-lib/aws-apigateway';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import { Effect, Policy, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { CfnOutput, Duration, Fn, RemovalPolicy, Stack, StackProps } from 'aws-cdk-lib';
import { WORKFLOW_TABLE_PROPS } from './persistence/constants';
import { Table } from 'aws-cdk-lib/aws-dynamodb';
import {IVpc, Vpc} from 'aws-cdk-lib/aws-ec2';
import {StateMachine} from "aws-cdk-lib/aws-stepfunctions";

export interface ServiceStackProps extends StackProps {
  vpc: Vpc;
  workflowStateMachine: StateMachine;
  manifestSplitStateMachine: StateMachine;
}

export class ServiceStack extends Stack {

  readonly vpc: IVpc;  
  
  constructor(scope: Construct, id: string, props: ServiceStackProps) {
    super(scope, id, props);

    // Create CloudWatch log group for Lambda
    const lambdaLogGroup = new LogGroup(this, 'LambdaLogGroup', {
        retention: RetentionDays.ONE_WEEK,
        logGroupName: '/aws/lambda/bops-parallelization-service-lambda',
        removalPolicy: RemovalPolicy.DESTROY
    });

    this.vpc = props.vpc;

    const tableExportName = WORKFLOW_TABLE_PROPS.EXPORT_NAME_PREFIX;
    const dynamoDbTableArn = Fn.importValue(tableExportName);
    const dynamoDbTable = Table.fromTableArn(this, 'DynamoDbTable', dynamoDbTableArn);

    // Create a Lambda function that will handle API requests
    const apiHandler = new Function(this, 'BOPSParallelMainHandler', {
        vpc: this.vpc,
        functionName: 'BOPSParallelMainHandler',
        runtime: Runtime.JAVA_17,
        handler: 'com.amazon.bopspar.service.LambdaMain::handleRequest',
        code: Code.fromAsset('../build/libs/BOPSParallelization-1.0-SNAPSHOT-all.jar'),
        timeout: Duration.seconds(30),
        memorySize: 1024,
        // Explicitly define the log group for this Lambda function
        logGroup: lambdaLogGroup,
        environment: {
          DYNAMODB_TABLE_NAME: dynamoDbTable.tableName,
          DYNAMMODB_TABLE_ARN: dynamoDbTableArn,
          WORKFLOW_STATE_MACHINE_ARN: props.workflowStateMachine.stateMachineArn,
          MANIFEST_SPLIT_STATE_MACHINE_ARN: props.manifestSplitStateMachine.stateMachineArn
        }
    });    

    dynamoDbTable.grantReadWriteData(apiHandler);

    //Create necessary permissions for assuming role (Needed for running S3 validations on customer accounts)
    const policyStatement = new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ['sts:AssumeRole'],
      resources: ['*'],
    });

    const crossAccountRoleAssumePolicy = new Policy(this, 'AssumeRolePolicy', {
      policyName: 'cross-account-s3-access',
      statements: [policyStatement],
    });

    crossAccountRoleAssumePolicy.attachToRole(apiHandler.role!);

    const stepFunctionPolicy = new Policy(this, 'StepFunctionPolicy', {
      policyName: 'step-function-policy',
      statements: [
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: [
            'states:SendTaskSuccess',
            'states:StartExecution'
          ],
          resources: [
            props.workflowStateMachine.stateMachineArn,
            props.manifestSplitStateMachine.stateMachineArn
          ]
        })
      ]
    });
    apiHandler.role?.attachInlinePolicy(stepFunctionPolicy);

    // Output the API URL
    new CfnOutput(this, 'BOPS-Parallel-Handler', {
      value: apiHandler.functionArn,
      description: 'BOPS Parallelization start workflow handler',
    });
  }
}