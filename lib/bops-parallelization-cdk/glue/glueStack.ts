import { Role } from 'aws-cdk-lib/aws-iam';
import { CfnJob } from 'aws-cdk-lib/aws-glue';
import { aws_s3_assets, Stack, StackProps } from 'aws-cdk-lib';
import { Construct } from 'constructs';

export interface GlueStackProps extends StackProps {
  glueJobRole: Role;
  /**
   * Approximate max number of rows per output CSV manifest file, passed to the
   * Glue script as --row_limit. Supported range is 1,000,000,000 - 5,000,000,000.
   * Defaults to 5,000,000,000 when omitted.
   */
  rowLimit?: number;
}

// ROW_LIMIT bounds, kept in sync with manifest-split-job.py.
const ROW_LIMIT_DEFAULT = 5_000_000_000;
const ROW_LIMIT_MIN = 1_000_000_000;
const ROW_LIMIT_MAX = 5_000_000_000;

export class GlueStack extends Stack {
  readonly stage: string;
  readonly glueJobRole: Role;

  constructor(scope: Construct, id: string, props: GlueStackProps) {
    super(scope, id, props);

    this.glueJobRole = props.glueJobRole;

    const rowLimit = props.rowLimit ?? ROW_LIMIT_DEFAULT;
    if (rowLimit < ROW_LIMIT_MIN || rowLimit > ROW_LIMIT_MAX) {
      throw new Error(
        `rowLimit ${rowLimit} is outside the supported range [${ROW_LIMIT_MIN}, ${ROW_LIMIT_MAX}]`
      );
    }

    const etlScript = new aws_s3_assets.Asset(this, 'ETLScript', {
      path: './bops-parallelization-cdk/glue/assets/manifest-split-job.py',
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
        '--row_limit': rowLimit.toString(),
      },
    });

    etlScript.grantRead(this.glueJobRole);
  }
}
