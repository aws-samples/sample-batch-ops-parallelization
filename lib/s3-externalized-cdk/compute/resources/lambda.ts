import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as kms from 'aws-cdk-lib/aws-kms';
import { Code, Runtime } from 'aws-cdk-lib/aws-lambda';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import { Duration } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { ConstructBaseProps } from '../../persistence/constants';
import { Vpc } from 'aws-cdk-lib/aws-ec2';
import { DynamoDBResources } from '../../persistence/resources/dynamodb';

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

    const encryptionKey = new kms.Key(this, `EncryptionKey`, {
      enableKeyRotation: true,
      alias: this.getDefaultEncryptionKeyAlias(props.functionName),
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.lambdaRole = new iam.Role(this, `Role`, {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      roleName: props.roleName,
    });
    this.lambdaRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaBasicExecutionRole'),
    );
    this.lambdaRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSLambdaVPCAccessExecutionRole'),
    );

    this.lambdaFunction = new lambda.Function(this, `Function`, {
      handler: props.handler,
      runtime: Runtime.JAVA_17,
      functionName: props.functionName,
      code:  Code.fromAsset('../build/libs/S3AExternalization-1.0-SNAPSHOT-all.jar'),
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
  }

  private getDefaultEncryptionKeyAlias(functionName: string): string {
    return `alias/${functionName}-encryption-key`;
  }
}