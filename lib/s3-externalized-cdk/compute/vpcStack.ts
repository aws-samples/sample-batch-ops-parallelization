import { App, Stack, StackProps } from 'aws-cdk-lib';
import { aws_ec2 as ec2 } from 'aws-cdk-lib';
import { Construct } from 'constructs';

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

    this.securityGroup = new ec2.SecurityGroup(this, 'S3ALambdaVPCSecurityGroup', {
      vpc: this.vpc,
      securityGroupName: 'S3ALambdaVPCSecurityGroup',
    });
  }
}