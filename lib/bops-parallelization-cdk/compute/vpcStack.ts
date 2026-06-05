import {aws_ec2 as ec2, RemovalPolicy, Stack, StackProps} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import {LogGroup, RetentionDays} from "aws-cdk-lib/aws-logs";

export class VpcStack extends Stack {
  readonly vpc: ec2.Vpc;
  readonly securityGroup: ec2.SecurityGroup;
  readonly securityGroupForConduit: ec2.SecurityGroup;
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'S3ALambdaVPC', {
      cidr: '10.0.0.0/16',
      availabilityZones: ['us-west-2a', 'us-west-2b', 'us-west-2c']
    });

    const flowLogGroup = new LogGroup(this, 'VpcFlowLogGroup', {
      retention: RetentionDays.ONE_WEEK,
      removalPolicy: RemovalPolicy.DESTROY
    });

    const vpcFlowLog = new ec2.FlowLog(this, 'VpcFlowLog', {
      resourceType: ec2.FlowLogResourceType.fromVpc(this.vpc),
      destination: ec2.FlowLogDestination.toCloudWatchLogs(flowLogGroup),
      trafficType: ec2.FlowLogTrafficType.ALL
    });

    this.securityGroup = new ec2.SecurityGroup(this, 'S3ALambdaVPCSecurityGroup', {
      vpc: this.vpc,
      securityGroupName: 'S3ALambdaVPCSecurityGroup',
    });
  }
}