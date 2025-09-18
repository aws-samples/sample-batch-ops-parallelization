import {Duration, RemovalPolicy, Stack, StackProps} from 'aws-cdk-lib';
import { Construct } from 'constructs';
import {BlockPublicAccess, Bucket} from "aws-cdk-lib/aws-s3";
import {AllowedMethods, Distribution, SecurityPolicyProtocol, ViewerProtocolPolicy} from "aws-cdk-lib/aws-cloudfront";
import {S3BucketOrigin} from "aws-cdk-lib/aws-cloudfront-origins";
import {BucketDeployment, Source} from "aws-cdk-lib/aws-s3-deployment";

export class FrontendStack extends Stack {
    constructor(scope: Construct, id: string, props: StackProps) {
        super(scope, id, props);

        const s3Bucket = new Bucket(this, "S3Bucket", {
            bucketName: `s3a-externalization-${props.env?.account}`,
            blockPublicAccess: BlockPublicAccess.BLOCK_ALL,
            publicReadAccess: false,
            removalPolicy: RemovalPolicy.DESTROY,
            autoDeleteObjects: true
        })

        const distribution = new Distribution(this, "CloudfrontDistribution", {
            defaultBehavior: {
                allowedMethods: AllowedMethods.ALLOW_GET_HEAD_OPTIONS,
                compress: true,
                origin: S3BucketOrigin.withOriginAccessControl(s3Bucket),
                viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS
            },
            defaultRootObject: "index.html",
            errorResponses: [
                {
                    httpStatus: 403,
                    responseHttpStatus: 200,
                    responsePagePath: "/index.html",
                    ttl: Duration.minutes(30)
                }
            ],
            minimumProtocolVersion: SecurityPolicyProtocol.TLS_V1_2_2021
        });

        // Deploy frontend files from /frontend/dist/ to the S3 bucket
        new BucketDeployment(this, "DeployFrontend", {
            sources: [Source.asset("../frontend/dist")],
            destinationBucket: s3Bucket,
            distribution,
            distributionPaths: ["/*"],
        });
    }
}