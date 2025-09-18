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
  public readonly restApi: RestApi;
  
  constructor(scope: Construct, id: string, props: ServiceStackProps) {
    super(scope, id, props);

    // Create CloudWatch log group for Lambda
    const lambdaLogGroup = new LogGroup(this, 'LambdaLogGroup', {
        retention: RetentionDays.ONE_WEEK,
        logGroupName: '/aws/lambda/s3-externalized-service-lambda',
        removalPolicy: RemovalPolicy.DESTROY
    });

    this.vpc = props.vpc;

    const tableExportName = WORKFLOW_TABLE_PROPS.EXPORT_NAME_PREFIX;
    const dynamoDbTableArn = Fn.importValue(tableExportName);
    const dynamoDbTable = Table.fromTableArn(this, 'DynamoDbTable', dynamoDbTableArn);

    // Create a Lambda function that will handle API requests
    const apiHandler = new Function(this, 'ApiHandler', {
      vpc: this.vpc,
        runtime: Runtime.JAVA_17,
        handler: 'com.amazon.tdm.s3a.service.LambdaMain::handleRequest',
        code: Code.fromAsset('../build/libs/S3AExternalization-1.0-SNAPSHOT-all.jar'),
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

    // Create an API Gateway REST API
    this.restApi = new RestApi(this, 'S3ExternalizedApi', {
      restApiName: 'S3 Externalized Service',
      description: 'This API serves the S3 Externalization functionality',
      deployOptions: {
        stageName: 'dev',
        metricsEnabled: true,
        // Less verbose logging configuration
        dataTraceEnabled: false,
        loggingLevel: MethodLoggingLevel.INFO,
        methodOptions: {
          '/*/*': {
            dataTraceEnabled: false,
          }
        }
      },
      defaultCorsPreflightOptions: {
        allowOrigins: Cors.ALL_ORIGINS,
        allowMethods: Cors.ALL_METHODS,
      },
    });

    // Create Lambda integration
    const lambdaIntegration = new LambdaIntegration(apiHandler, {
      proxy: true,
      requestTemplates: {
        'application/json': '{ "statusCode": 200 }',
      },
    });

    dynamoDbTable.grantReadWriteData(apiHandler);

    // Create workflow resources based on the LambdaMain routes
    
    // Create workflow endpoint
    const createWorkflowResource = this.restApi.root.addResource('createWorkflow');
    createWorkflowResource.addMethod('POST', lambdaIntegration);
    
    // Get workflow endpoint
    const getWorkflowResource = this.restApi.root.addResource('getWorkflow');
    getWorkflowResource.addMethod('POST', lambdaIntegration);
    
    // Start workflow endpoint
    const startWorkflowResource = this.restApi.root.addResource('startWorkflow');
    startWorkflowResource.addMethod('POST', lambdaIntegration);

        // Start workflow endpoint
    const startManifestSplitWorkflowResource = this.restApi.root.addResource('startManifestSplitWorkflow');
    startManifestSplitWorkflowResource.addMethod('POST', lambdaIntegration);
    
    // Delete workflow endpoint
    const deleteWorkflowResource = this.restApi.root.addResource('deleteWorkflow');
    deleteWorkflowResource.addMethod('POST', lambdaIntegration);

    // SendControlCommand workflow endpoint
    const sendControlCommandResource = this.restApi.root.addResource('sendControlCommand');
    sendControlCommandResource.addMethod('POST', lambdaIntegration);

    // ListWorkflows endpoint
    const listWorkflowsResource = this.restApi.root.addResource('listWorkflows');
    listWorkflowsResource.addMethod('POST', lambdaIntegration);

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
    new CfnOutput(this, 'ApiUrl', {
      value: this.restApi.url,
      description: 'URL of the API Gateway',
    });
  }
}