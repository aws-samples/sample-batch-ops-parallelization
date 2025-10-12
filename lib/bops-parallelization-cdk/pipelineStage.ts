import { Stage, StageProps } from "aws-cdk-lib";
import { Construct } from "constructs";
import { PersistenceStack } from "./persistence/persistence_stack";
import { VpcStack } from "./compute/vpcStack";
import { ComputeStack } from "./compute/computeStack";
import { WorkflowStack } from "./workflow/workflowStack";
import { S3AResourcesIAMStack } from "./iam/iamStack";
import { S3AMonitorStack } from "./monitor/s3aMonitorStack";
import { ServiceStack } from "./serviceStack";
import { Stage as DeploymentStage } from "./config/deploymentEnvironment";
import {FrontendStack} from "./frontend/frontendStack";
import { GlueStack } from "./glue/glueStack";
import { InventoryReportConfigStack } from "./compute/inventoryReportConfigStack";
import { ManifestSplitWorkflowStack } from "./workflow/manifestSplitWorkflowStack";

interface PipelineStageProps extends StageProps {
    stage: DeploymentStage
}
export class PipelineStage extends Stage{
    constructor(scope: Construct, id: string, props: PipelineStageProps) {
        super(scope, id, props);

            const persistenceStack = new PersistenceStack(this, `PersistenceStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env
            });
        
            const vpcStack = new VpcStack(this, `VpcStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env
            });
        
            const computeStack = new ComputeStack(this, `ComputeStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env,
                vpc: vpcStack.vpc,
                dbResources: persistenceStack.dynamoDBResources
            });
        
            computeStack.addDependency(vpcStack);
            computeStack.addDependency(persistenceStack);
        
            const workflowStack = new WorkflowStack(this, `WorkflowStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env,
                lambdas: computeStack.lambdas
            })
        
            workflowStack.addDependency(computeStack);

            const inventoryReportConfigStack = new InventoryReportConfigStack(this, `InventoryReportConfigStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env,
                vpc: vpcStack.vpc,
                dbResources: persistenceStack.dynamoDBResources
            });
        
            const iamStack = new S3AResourcesIAMStack(this, `IAMStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env,
                manifestlambdaRoleArn: inventoryReportConfigStack.manifestlambdaRoleArn,
                pollForGlueJobLambdaRoleArn: inventoryReportConfigStack.pollForGlueJobRoleArn,
                pollForInventoryReportManifestLambdaRoleArn: inventoryReportConfigStack.pollForInventoryReportManifestRoleArn,
                inventoryConfigLambdaArn: inventoryReportConfigStack.inventoryConfigLambdaRoleArn
            });

            persistenceStack.dynamoDBResources.executionTable.grantReadWriteData(iamStack.glueJobRole);
        
            const monitorStack = new S3AMonitorStack(this, `MonitorStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env
            });

            const frontendStack = new FrontendStack(this, `FrontendStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env
            });

            const glueStack = new GlueStack(this, `GlueStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env,
                glueJobRole: iamStack.glueJobRole
            });

            const manifestSplitWorkflowStack = new ManifestSplitWorkflowStack(this, `ManifestSplitWorkflowStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env,
                lambdas: {
                    ...inventoryReportConfigStack.manifestSplitLambdas,
                    createS3BucketLambda: computeStack.lambdas.createS3BucketLambda,
                    configureS3BucketLambda: computeStack.lambdas.configureS3BucketLambda
                },
                workflowStateMachine: workflowStack.stateMachine
            })

            const serviceStack = new ServiceStack(this, `ServiceStack-${props.env?.account}-${props.env?.region}-${props.stage}`, {
                env: props.env,
                vpc: vpcStack.vpc,
                workflowStateMachine: workflowStack.stateMachine,
                manifestSplitStateMachine: manifestSplitWorkflowStack.stateMachine
            });
                
            serviceStack.addDependency(vpcStack);
            serviceStack.addDependency(workflowStack);
            serviceStack.addDependency(manifestSplitWorkflowStack);

            manifestSplitWorkflowStack.addDependency(inventoryReportConfigStack);
            manifestSplitWorkflowStack.addDependency(workflowStack);

            inventoryReportConfigStack.addDependency(vpcStack);
            inventoryReportConfigStack.addDependency(persistenceStack);
            serviceStack.addDependency(inventoryReportConfigStack);
            glueStack.addDependency(iamStack);
    }
}
