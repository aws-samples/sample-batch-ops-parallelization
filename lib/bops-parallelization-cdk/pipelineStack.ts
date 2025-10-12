import { Duration, RemovalPolicy, SecretValue, Stack, StackProps, Stage } from "aws-cdk-lib";
import { AllowedMethods, Distribution, SecurityPolicyProtocol, ViewerProtocolPolicy } from "aws-cdk-lib/aws-cloudfront";
import { S3BucketOrigin } from "aws-cdk-lib/aws-cloudfront-origins";
import { AccountPrincipal, ArnPrincipal, CfnRole, Effect, ManagedPolicy, Policy, PolicyDocument, PolicyStatement, Role } from "aws-cdk-lib/aws-iam";
import { BlockPublicAccess, Bucket } from "aws-cdk-lib/aws-s3";
import { CodePipeline, CodePipelineSource, PipelineBase, ShellStep } from "aws-cdk-lib/pipelines";
import { Construct } from "constructs";
import { PipelineStage } from "./pipelineStage";
import { DEPLOYMENT_ENVIRONMENTS, Stage as EnvStage } from "./config/deploymentEnvironment";


export class PipelineStack extends Stack {
    constructor(scope: Construct, id: string, props?: StackProps) {
      super(scope, id, props);

        const s3Bucket = new Bucket(this, "S3BucketCDK", {
            bucketName: `s3a-extbops-${this.account}-${this.region}`,
            blockPublicAccess: BlockPublicAccess.BLOCK_ALL,
            publicReadAccess: false,
            removalPolicy: RemovalPolicy.DESTROY,
            autoDeleteObjects: true,
            versioned: true
        })

        const gitLabRole = new CfnRole(this, "GitLabRoleCDK", {
            roleName: "GitLabCDK",
            description: "GitLab role for uploading to S3 CDK bucket",
            assumeRolePolicyDocument: new PolicyDocument({
              statements: [
                new PolicyStatement({
                    effect: Effect.ALLOW,
                    actions: [
                        "sts:AssumeRole",
                        "sts:TagSession"
                    ],
                    principals: [
                        new ArnPrincipal("arn:aws:iam::777788889999:role/gitlab-runners-prod")
                    ],
                    conditions: {
                        StringEquals: {
                            'aws:PrincipalTag/GitLab:Group': "s3a",
                            'aws:PrincipalTag/GitLab:Project': "bops-parallelization"
                        }
                    }
                }),
              ]
            }),
            policies: [
                {
                    policyName: "S3AccessPolicy",
                    policyDocument: new PolicyDocument({
                        statements: [
                            new PolicyStatement({
                                effect: Effect.ALLOW,
                                actions: [
                                    "s3:GetObject",
                                    "s3:PutObject",
                                    "s3:ListBucket"
                                ],
                                resources: [
                                    `${s3Bucket.bucketArn}/*`
                                ]
                            })
                        ]
                    })
                }
            ]
        })

        const pipeline = new CodePipeline(this, "S3AExternlization", {
            pipelineName: "S3AExternlizationPipeline",
            synth: new ShellStep("Synth", {
                input: CodePipelineSource.s3(s3Bucket, "cdk/bopsparallelization.zip"),
                commands: [
                    "cd lib",
                    "npm ci",
                    "npx cdk synth"
                ],
                primaryOutputDirectory: "lib/cdk.out",
            }),
        })

        const pipelineStages = DEPLOYMENT_ENVIRONMENTS.forEach((environment) => {
            pipeline.addStage(new PipelineStage(this, `BOPSParallelization-${environment.stage}`, {
                env: environment,
                stage: environment.stage
            }))
        })


    }
}
