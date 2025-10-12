import {Construct} from 'constructs';

import {Duration, Stack, StackProps} from 'aws-cdk-lib';
import {LambdaMap} from "../compute/computeStack";
import {
    Chain, Choice, Condition,
    DefinitionBody, IntegrationPattern, JsonPath,
    Pass,
    StateMachine,
    StateMachineType, TaskInput,
    Wait,
    WaitTime
} from "aws-cdk-lib/aws-stepfunctions";
import {LambdaInvoke} from "aws-cdk-lib/aws-stepfunctions-tasks";

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

        const waitForStopSourceTrafficAckTask = new LambdaInvoke(this, 'WaitForStopSourceTrafficAckTask', {
            stateName: 'WaitForStopSourceTrafficAck',
            lambdaFunction: lambdas.waitForCustomerAckLambda.lambdaFunction,
            integrationPattern: IntegrationPattern.WAIT_FOR_TASK_TOKEN,
            payload: TaskInput.fromObject({
                workflowName: JsonPath.stringAt('$.workflowName'),
                namespaceID: JsonPath.stringAt('$.namespaceID'),
                taskToken: JsonPath.taskToken
            }),
            outputPath: '$'
        });

        const s3MonitorLiveReplicationTask = new LambdaInvoke(this, 'S3MonitorLiveReplicationTask', {
            stateName: 'MonitorLiveReplication',
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

        const waitBeforeCrrMonitoring = new Wait(this, 'WaitBeforeCrrLiveMonitoring', {
            time: WaitTime.duration(Duration.seconds(900))
        });

        const waitForCrrComplete = new Wait(this, 'WaitForCrrCompleteSignal', {
            time: WaitTime.duration(Duration.seconds(60))
        });

        const closeWorkflow = new Pass(this, 'CloseWorkflow');
        const createBucketChoice = new Choice(this, 'Check S3CreateBucket response');
        const configureLambdaChoice = new Choice(this, 'Check ConfigureLambda response');
        const replicationSetupChoice = new Choice(this, 'Check S3ReplicationSetupLambda response');
        const monitorChoice = new Choice(this, 'Check S3Monitor response');
        const monitorLiveReplicationChoice = new Choice(this, 'Check MonitorLiveReplication response');
        const startAtChoice = new Choice(this, 'Check startAt state');

        const mainPath = s3CreateBucketTask
            .next(createBucketChoice)

        const replicationPath = s3ReplicationSetupTask;

        const workflowChain = Chain
            .start(startAtChoice
                .when(Condition.stringEquals("$.startAt", "SetupReplication"), replicationPath)
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
            .when(Condition.stringEquals('$.status', 'WAITING'), waitForStopSourceTrafficAckTask)
            .otherwise(closeWorkflow);

        waitForBackfillComplete
            .next(s3MonitorBackfillTask);

        waitForStopSourceTrafficAckTask
            .next(waitBeforeCrrMonitoring)
            .next(s3MonitorLiveReplicationTask)
            .next(monitorLiveReplicationChoice);

        monitorLiveReplicationChoice
            .when(Condition.or(
                Condition.stringEquals('$.status', 'FINISHED'),
                Condition.stringEquals('$.status', 'STOPPED')
            ), s3PostReplicationTask)
            .when(Condition.or(
                Condition.stringEquals('$.status', 'RUNNING'),
                Condition.stringEquals('$.status', 'STOPPING')
            ), waitForCrrComplete)
            .otherwise(closeWorkflow);

        waitForCrrComplete
            .next(s3MonitorLiveReplicationTask);

        s3PostReplicationTask
            .next(closeWorkflow);

        this.stateMachine = new StateMachine(this, `S3AWorkflow`, {
            stateMachineName: "S3AWorkflow",
            definitionBody: DefinitionBody.fromChainable(workflowChain),
            stateMachineType: StateMachineType.STANDARD,
            tracingEnabled: true,
        });

    }
}