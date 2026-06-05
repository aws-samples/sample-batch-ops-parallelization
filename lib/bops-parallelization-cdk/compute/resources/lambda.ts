import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as kms from 'aws-cdk-lib/aws-kms';
import { Code, Runtime } from 'aws-cdk-lib/aws-lambda';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import { Duration } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { Vpc } from 'aws-cdk-lib/aws-ec2';
import { DynamoDBResources } from '../../persistence/resources/dynamodb';
import {NagSuppressions} from "cdk-nag";

export interface LambdaProps {
  functionName: string;
  handler: string;
  componentName: string;
  roleName: string;
  vpc: Vpc;
  dbResources: DynamoDBResources;
  env?: { [key: string]: string };
}

export class LambdaResource extends Construct {
  readonly disambiguator: string;
  readonly lambdaFunction: lambda.Function;
  readonly dbResources: DynamoDBResources;
  lambdaRole: iam.Role;

  constructor(parent: Construct, id: string, props: LambdaProps) {
    super(parent, id);
    this.dbResources = props.dbResources;
    const account = cdk.Stack.of(this).account;
    const region = cdk.Stack.of(this).region;

    const logGroupName = `/aws/lambda/${props.functionName}`;

    const encryptionKey = new kms.Key(this, `EncryptionKey`, {
      enableKeyRotation: true,
      alias: this.getDefaultEncryptionKeyAlias(props.functionName),
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.lambdaRole = new iam.Role(this, `Role`, {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      roleName: props.roleName,
    });
    const cwPolicy = new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'logs:CreateLogGroup',
        'logs:CreateLogStream',
        'logs:PutLogEvents'
      ],
      resources: [
        `arn:aws:logs:${region}:${account}:log-group:${logGroupName}`,
        `arn:aws:logs:${region}:${account}:log-group:${logGroupName}:*`
      ]
    })

    this.lambdaRole.addToPolicy(cwPolicy);

    const ec2Policy = new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'ec2:CreateNetworkInterface',
        'ec2:DescribeNetworkInterfaces',
        'ec2:DeleteNetworkInterface',
        'ec2:AssignPrivateIpAddresses',
        'ec2:UnassignPrivateIpAddresses',
        'ec2:DescribeNetworkInterfaces'
      ],
      resources: ['*']
    })

    this.lambdaRole.addToPolicy(ec2Policy);
    this.lambdaFunction = new lambda.Function(this, `Function`, {
      handler: props.handler,
      runtime: Runtime.JAVA_21,
      functionName: props.functionName,
      code:  Code.fromAsset('../build/libs/BOPSParallelization-1.0-SNAPSHOT-all.jar'),
      role: this.lambdaRole,
      environmentEncryption: encryptionKey,
      logGroup: new LogGroup(this, `LogGroup`, {
        retention: RetentionDays.TEN_YEARS,
      }),
      memorySize: 512,
      timeout: Duration.seconds(30),
      vpc: props.vpc,
      environment: props.env
    });

    this.dbResources.executionTable.grantReadWriteData(this.lambdaFunction);

    NagSuppressions.addResourceSuppressions(this.lambdaRole, [
      {
        id: 'AwsSolutions-IAM5',
        reason: 'Lambda needs permissions to write to CloudWatch Logs, manage VPC'
      }
    ], true);

    NagSuppressions.addResourceSuppressions(this.lambdaFunction, [
      {
        id: 'AwsSolutions-L1',
        reason: 'Function uses Runtime.JAVA_21, the latest Java LTS runtime supported by AWS Lambda.'
      }
    ]);
  }

  private getDefaultEncryptionKeyAlias(functionName: string): string {
    return `alias/${functionName}-encryption-key`;
  }
}