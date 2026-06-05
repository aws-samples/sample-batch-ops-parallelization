import {Construct} from 'constructs';

import {Duration, RemovalPolicy, Stack, StackProps} from 'aws-cdk-lib';
import {LambdaMap} from "../compute/computeStack";
import {
    Chain,
    Choice,
    Condition,
    DefinitionBody,
    LogLevel,
    Pass,
    StateMachine,
    StateMachineType,
    Wait,
    WaitTime,
    CfnStateMachine
} from "aws-cdk-lib/aws-stepfunctions";
import {LambdaInvoke} from "aws-cdk-lib/aws-stepfunctions-tasks";
import {LogGroup, RetentionDays} from "aws-cdk-lib/aws-logs";
import {NagSuppressions} from "cdk-nag";

export interface WorkflowStackProps extends StackProps {
    lambdas: LambdaMap
}

export class WorkflowStack extends Stack {
    readonly stateMachine: StateMachine;

    constructor(scope: Construct, id: string, props: WorkflowStackProps) {
        super(scope, id, props);

        const {lambdas} = props;

        const s3CreateBucketTask = new LambdaInvoke(this, 'S3CreateBucketTask', {
            stateName: 'CreateBucket',
            lambdaFunction: lambdas.createS3BucketLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3ConfigureBucketTask = new LambdaInvoke(this, 'S3ConfigureBucketTask', {
            stateName: 'ConfigureBucket',
            lambdaFunction: lambdas.configureS3BucketLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3ReplicationSetupTask = new LambdaInvoke(this, 'S3ReplicationSetupTask', {
            stateName: 'ReplicationSetup',
            lambdaFunction: lambdas.s3ReplicationSetupLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3MonitorBackfillTask = new LambdaInvoke(this, 'S3MonitorBackfillTask', {
            stateName: 'MonitorBackfill',
            lambdaFunction: lambdas.s3MonitorLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3PostReplicationTask = new LambdaInvoke(this, 'S3PostReplicationTask', {
            stateName: 'PostReplication',
            lambdaFunction: lambdas.s3PostReplicationLambda.lambdaFunction,
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

        const mainPath = s3CreateBucketTask
            .next(createBucketChoice)

        const replicationPath = s3ReplicationSetupTask;

        const workflowChain = Chain
            .start(startAtChoice
                .when(Condition.and(
                    Condition.isPresent('$.startAt'),
                    Condition.stringEquals('$.startAt', "SetupReplication")
                ), replicationPath)
                .otherwise(mainPath)
            );

        createBucketChoice
            .when(Condition.stringEquals('$.status', 'CREATED'), s3ConfigureBucketTask)
            .otherwise(closeWorkflow);

        s3ConfigureBucketTask
            .next(configureLambdaChoice);

        configureLambdaChoice
            .when(Condition.stringEquals('$.status', 'FINISHED'), s3ReplicationSetupTask)
            .otherwise(closeWorkflow);

        s3ReplicationSetupTask
            .next(replicationSetupChoice);

        replicationSetupChoice
            .when(Condition.stringEquals('$.status', 'FINISHED'), s3MonitorBackfillTask)
            .otherwise(closeWorkflow);

        s3MonitorBackfillTask
            .next(monitorChoice);

        monitorChoice
            .when(Condition.or(
                Condition.stringEquals('$.status', 'RUNNING'),
                Condition.stringEquals('$.status', 'STOPPING')
            ), waitForBackfillComplete)
            .when(Condition.stringEquals('$.status', 'STOPPED'), s3PostReplicationTask)
            .when(Condition.stringEquals('$.status', 'WAITING'), s3PostReplicationTask)
            .otherwise(closeWorkflow);

        waitForBackfillComplete
            .next(s3MonitorBackfillTask);

        s3PostReplicationTask
            .next(closeWorkflow);

        const logGroup = new LogGroup(this, 'S3AWorkflowLogGroup', {
            retention: RetentionDays.ONE_WEEK,
            removalPolicy: RemovalPolicy.DESTROY
        });

        this.stateMachine = new StateMachine(this, `S3AWorkflow`, {
            stateMachineName: "S3AWorkflow",
            definitionBody: DefinitionBody.fromChainable(workflowChain),
            stateMachineType: StateMachineType.STANDARD,
            tracingEnabled: true,
            logs: {
                destination: logGroup,
                level: LogLevel.ALL,
                includeExecutionData: true
            }
        });

        NagSuppressions.addResourceSuppressions(this.stateMachine.role, [
            {
                id: 'AwsSolutions-IAM5',
                reason: 'Step function policy needs * for X-ray tracing and function alias/version'
            }
        ], true)

    }
}