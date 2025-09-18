import {Environment} from "aws-cdk-lib";

export enum Stage {
    DEV = "dev"
}

export interface DeploymentEnvironment extends Environment {
    stage: Stage
}

export const DEPLOYMENT_ENVIRONMENTS : DeploymentEnvironment[] = [
    {
        account: '222233334444',
        region: 'us-west-2',
        stage: Stage.DEV
    }
]