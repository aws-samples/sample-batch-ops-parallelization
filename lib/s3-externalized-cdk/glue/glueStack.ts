import { Role } from 'aws-cdk-lib/aws-iam';
import { CfnJob } from 'aws-cdk-lib/aws-glue';
import { aws_s3_assets, Stack, StackProps } from 'aws-cdk-lib';
import { Construct } from 'constructs';

export interface GlueStackProps extends StackProps {
  glueJobRole: Role;
}

export class GlueStack extends Stack {
  readonly stage: string;
  readonly glueJobRole: Role;

  constructor(scope: Construct, id: string, props: GlueStackProps) {
    super(scope, id, props);

    this.glueJobRole = props.glueJobRole;

    const etlScript = new aws_s3_assets.Asset(this, 'ETLScript', {
      path: './s3-externalized-cdk/glue/assets/manifest-split-job.py',
    });

    // Create the Glue job using CfnJob
    const glueJob = new CfnJob(this, 'ManifestSplitJob', {
      name: 'manifest-split-glue-job',
      role: this.glueJobRole.roleArn,
      command: {
        name: 'glueetl',
        pythonVersion: '3',
        scriptLocation: etlScript.s3ObjectUrl,
      },
      glueVersion: '5.0',
      timeout: 1440,
      workerType: 'G.8X',
      numberOfWorkers: 30,
      executionProperty: {
        maxConcurrentRuns: 2000,
      },
      defaultArguments: {
        '--enable-metrics': 'true',
        '--enable-continuous-cloudwatch-log': 'true',
        '--job-language': 'python',
      },
    });

    etlScript.grantRead(this.glueJobRole);
  }
}
