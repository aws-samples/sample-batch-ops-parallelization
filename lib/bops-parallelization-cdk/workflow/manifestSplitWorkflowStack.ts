import {Construct} from 'constructs';

import {Duration, Stack, StackProps} from 'aws-cdk-lib';
import {
    Chain, Choice, Condition,
    DefinitionBody,
    Fail,
    IntegrationPattern,
    Pass,
    StateMachine,
    StateMachineType,
    TaskInput,
    Wait,
    WaitTime
} from "aws-cdk-lib/aws-stepfunctions";
import {LambdaInvoke, StepFunctionsStartExecution} from "aws-cdk-lib/aws-stepfunctions-tasks";
import { LambdaManifestSplitMap } from '../compute/inventoryReportConfigStack';
import { LambdaMap } from '../compute/computeStack';

export interface ManifestSplitWorkflowStackProps extends StackProps {
    lambdas: LambdaManifestSplitMap & {
        createS3BucketLambda: LambdaMap['createS3BucketLambda'];
        configureS3BucketLambda: LambdaMap['configureS3BucketLambda'];
    };
    workflowStateMachine: StateMachine;
}

export class ManifestSplitWorkflowStack extends Stack {
    readonly stateMachine: StateMachine;
    constructor(scope: Construct, id: string, props: ManifestSplitWorkflowStackProps) {
        super(scope, id, props);

        const {lambdas} = props;

        // Define Lambda tasks
        const s3CreateBucketTask = new LambdaInvoke(this, 'S3CreateBucketTask', {
            stateName: 'S3CreateBucket',
            lambdaFunction: lambdas.createS3BucketLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3ConfigureBucketTask = new LambdaInvoke(this, 'S3ConfigureBucketTask', {
            stateName: 'S3ConfigureBucket',
            lambdaFunction: lambdas.configureS3BucketLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3InventoryConfigSetupTask = new LambdaInvoke(this, 'S3InventoryConfigSetupTask', {
            stateName: 'S3InventoryConfigSetup',
            lambdaFunction: lambdas.s3InventoryConfigSetupLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3PollManifestTask = new LambdaInvoke(this, 'S3PollManifestTask', {
            stateName: 'S3PollManifest',
            lambdaFunction: lambdas.s3PollForInventoryReportManifestLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3ManifestSplitTask = new LambdaInvoke(this, 'S3ManifestSplitTask', {
            stateName: 'S3ManifestSplit',
            lambdaFunction: lambdas.manifestSplitLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const s3PollGlueJobTask = new LambdaInvoke(this, 'S3PollGlueJobTask', {
            stateName: 'S3PollGlueJob',
            lambdaFunction: lambdas.s3PollForGlueJobLambda.lambdaFunction,
            outputPath: '$.Payload'
        });

        const workflowInvokeTask = new StepFunctionsStartExecution(this, "WorkflowInvokeTask", {
            stateMachine: props.workflowStateMachine,
            associateWithParent: true,
            integrationPattern: IntegrationPattern.REQUEST_RESPONSE,
            input: TaskInput.fromObject({
                startAt: "SetupReplication",
                "workflowName.$": "$.workflowName",
                "namespaceID.$": "$.namespaceID"
            })
        });

        // Define wait states
        const waitForS3PollManifest = new Wait(this, 'WaitForS3PollManifest', {
            stateName: 'WaitForS3PollManifest',
            time: WaitTime.duration(Duration.minutes(1)) // 10
        });

        const waitForS3PollGlueJob = new Wait(this, 'WaitForS3PollGlueJob', {
            stateName: 'WaitForS3PollGlueJob',
            time: WaitTime.duration(Duration.minutes(1)) // 5
        });

        // Define choice states
        const inventoryConfigSetupChoice = new Choice(this, 'InventoryConfigSetupChoice', {
            stateName: 'InventoryConfigSetupChoice'
        });

        const pollManifestChoice = new Choice(this, 'PollManifestChoice', {
            stateName: 'PollManifestChoice'
        });

        const manifestSplitChoice = new Choice(this, 'ManifestSplitChoice', {
            stateName: 'ManifestSplitChoice'
        });

        const pollGlueJobChoice = new Choice(this, 'PollGlueJobChoice', {
            stateName: 'PollGlueJobChoice'
        });

        const createBucketChoice = new Choice(this, 'Check S3CreateBucket response');
        const configureLambdaChoice = new Choice(this, 'Check ConfigureLambda response');

        const failWorkflow = new Fail(this, "FailWorkflow", {
            stateName: "FailWorkflow"
        })

        // Define close workflow state
        const closeWorkflow = new Pass(this, 'CloseWorkflow', {
            stateName: 'CloseWorkflow'
        });

        // Build the workflow chain
        const workflowChain = Chain
            .start(s3CreateBucketTask)
            .next(createBucketChoice);

        createBucketChoice
            .when(Condition.stringEquals('$.status', 'CREATED'), s3ConfigureBucketTask)
            .otherwise(closeWorkflow);

        s3ConfigureBucketTask
            .next(configureLambdaChoice);

        configureLambdaChoice
            .when(Condition.stringEquals('$.status', 'FINISHED'), s3InventoryConfigSetupTask)
            .otherwise(closeWorkflow);

        s3InventoryConfigSetupTask
            .next(inventoryConfigSetupChoice);

        // Define the choice paths
        inventoryConfigSetupChoice
            .when(Condition.stringEquals('$.status', 'RUNNING'), s3PollManifestTask)
            .when(Condition.stringEquals('$.status', 'FINISHED'), workflowInvokeTask)
            .when(Condition.stringEquals('$.status', 'STOPPED'), closeWorkflow)
            .when(Condition.stringEquals('$.status', 'FAILED'), failWorkflow)
            .otherwise(closeWorkflow);

        s3PollManifestTask
            .next(pollManifestChoice);

        pollManifestChoice
            .when(Condition.stringEquals('$.status', 'RUNNING'), waitForS3PollManifest)
            .when(Condition.stringEquals('$.status', 'FINISHED'), s3ManifestSplitTask)
            .when(Condition.stringEquals('$.status', 'STOPPED'), closeWorkflow)
            .when(Condition.stringEquals('$.status', 'FAILED'), failWorkflow)
            .otherwise(closeWorkflow);

        waitForS3PollManifest
            .next(s3PollManifestTask);

        s3ManifestSplitTask
            .next(manifestSplitChoice);

        manifestSplitChoice
            .when(Condition.stringEquals('$.status', 'RUNNING'), s3PollGlueJobTask)
            .when(Condition.stringEquals('$.status', 'STOPPED'), closeWorkflow)
            .when(Condition.stringEquals('$.status', 'FAILED'), failWorkflow)
            .otherwise(closeWorkflow);

        s3PollGlueJobTask
            .next(pollGlueJobChoice);

        pollGlueJobChoice
            .when(Condition.stringEquals('$.status', 'RUNNING'), waitForS3PollGlueJob)
            .when(Condition.stringEquals('$.status', 'FINISHED'), workflowInvokeTask)
            .when(Condition.stringEquals('$.status', 'STOPPED'), closeWorkflow)
            .when(Condition.stringEquals('$.status', 'FAILED'), failWorkflow)
            .otherwise(closeWorkflow);

        waitForS3PollGlueJob
            .next(s3PollGlueJobTask);

        // Create the state machine
        this.stateMachine = new StateMachine(this, `ManifestSplitWorkflow`, {
            stateMachineName: "ManifestSplitWorkflow",
            definitionBody: DefinitionBody.fromChainable(workflowChain),
            stateMachineType: StateMachineType.STANDARD,
            tracingEnabled: true,
        });
    }
}
