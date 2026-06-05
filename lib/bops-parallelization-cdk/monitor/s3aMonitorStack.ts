import { Duration, Stack, StackProps } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import {
  Alarm,
  AlarmWidget,
  ComparisonOperator,
  Dashboard,
  DashboardVariable,
  DashboardVariableOptions,
  DefaultValue,
  GraphWidget,
  IWidget,
  TextWidget,
  MathExpression,
  Metric,
  PeriodOverride,
  Stats,
  TreatMissingData,
  Values,
  VariableInputType,
  VariableType,
  SingleValueWidget,
  GraphWidgetView,
} from 'aws-cdk-lib/aws-cloudwatch';

enum S3A_METRICS {
  METRICS_NAMESPACE = 'TDMS3MigrationAccelerator',
  METRICS_DIMENSION = 'bucketName',
  CRR_LATENCY_METRIC = 'CRRLatency',
  CRR_BYTES_PENDING_METRIC = 'CRRBytesPending',
  BOPS_TASKS_FAILED_METRIC = 'BOPSFailedTasks',
  BOPS_TASKS_TOTAL_METRIC = 'BOPSTotalTasks',
  BOPS_TASKS_SUCCEEDED_METRIC = 'BOPSTasksSucceeded',
  BOPS_PROGRESS_PCT_METRIC = 'BOPSProgressPct',
}

export class S3AMonitorStack extends Stack {
  private readonly stage: string;

constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);
    this.createS3aDashboard();
  }

  /**
   * Create a system-level dashboard with graphs for monitoring S3 bucket migrations.
   */
  private createS3aDashboard() {

    const s3aDashboard = new Dashboard(this, 'DataMigrationDashboard', {
      dashboardName: 'BOPSParallel-DataMigration',
      start: '-' + Duration.hours(8).toIsoString(),
      periodOverride: PeriodOverride.INHERIT,
    });

    const bucketNameVar = new DashboardVariable({
      id: 'bucketName',
      type: VariableType.PROPERTY,
      label: 'Bucket Name',
      inputType: VariableInputType.INPUT,
      value: 'bucketName',
      visible: true,
    });

    s3aDashboard.addVariable(bucketNameVar);

    s3aDashboard.addWidgets(
      // Header
      new TextWidget({
        width: 24,
        height: 1,
        markdown: '# BOPSParallel Data Migration Dashboard',
      }),
      new TextWidget({
        width: 24,
        height: 1,
        markdown: '## S3 Batch Operations Metrics',
      }),
      new SingleValueWidget({
        title: 'Batch Ops Job Progress Pct',
        metrics: [
          new Metric({
            namespace: S3A_METRICS.METRICS_NAMESPACE,
            metricName: S3A_METRICS.BOPS_PROGRESS_PCT_METRIC,
            dimensionsMap: {
              bucketName: 'bucketName',
            },
            statistic: Stats.MAXIMUM,
            period: Duration.minutes(1),
          }),
        ],
        fullPrecision: true,
        height: 4,
        width: 6,
      }),
      new SingleValueWidget({
        title: 'Batch Ops Job Total Tasks',
        metrics: [
          new Metric({
            namespace: S3A_METRICS.METRICS_NAMESPACE,
            metricName: S3A_METRICS.BOPS_TASKS_TOTAL_METRIC,
            dimensionsMap: {
              bucketName: 'bucketName',
            },
            statistic: Stats.MAXIMUM,
            period: Duration.minutes(1),
          }),
        ],
        fullPrecision: true,
        height: 4,
        width: 6,
      }),
      new SingleValueWidget({
        title: 'Batch Ops Job Tasks Succeeded',
        metrics: [
          new Metric({
            namespace: S3A_METRICS.METRICS_NAMESPACE,
            metricName: S3A_METRICS.BOPS_TASKS_SUCCEEDED_METRIC,
            dimensionsMap: {
              bucketName: 'bucketName',
            },
            statistic: Stats.MAXIMUM,
            period: Duration.minutes(1),
          }),
        ],
        fullPrecision: true,
        height: 4,
        width: 6,
      }),
      new SingleValueWidget({
        title: 'Batch Ops Job Tasks Failed',
        metrics: [
          new Metric({
            namespace: S3A_METRICS.METRICS_NAMESPACE,
            metricName: S3A_METRICS.BOPS_TASKS_FAILED_METRIC,
            dimensionsMap: {
              bucketName: 'bucketName',
            },
            statistic: Stats.MAXIMUM,
            period: Duration.minutes(1),
          }),
        ],
        fullPrecision: true,
        height: 4,
        width: 6,
      }),
      new TextWidget({
        width: 24,
        height: 1,
        markdown: '## S3 Cross Region Replication Metrics',
      }),
      new GraphWidget({
        title: 'Replication Latency',
        view: GraphWidgetView.TIME_SERIES,
        left: [
          new Metric({
            namespace: S3A_METRICS.METRICS_NAMESPACE,
            metricName: S3A_METRICS.CRR_LATENCY_METRIC,
            dimensionsMap: {
              bucketName: 'bucketName',
            },
            period: Duration.minutes(1),
          }),
        ],
        height: 7,
        width: 12,
      }),
      new GraphWidget({
        title: 'Bytes Pending Replication',
        view: GraphWidgetView.TIME_SERIES,
        left: [
          new Metric({
            namespace: S3A_METRICS.METRICS_NAMESPACE,
            metricName: S3A_METRICS.CRR_BYTES_PENDING_METRIC,
            dimensionsMap: {
              bucketName: 'bucketName',
            },
            period: Duration.minutes(1),
          }),
        ],
        height: 7,
        width: 12,
      }),
    );
  }
}