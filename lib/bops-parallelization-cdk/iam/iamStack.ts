import { Stack, StackProps } from 'aws-cdk-lib';
import { AccountPrincipal, CompositePrincipal, Effect, Policy, PolicyStatement, Role, ServicePrincipal }
    from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { NagSuppressions } from "cdk-nag";

export class S3AResourcesIAMStack extends Stack {

    constructor(scope: Construct, id: string, props?: StackProps) {
        super(scope, id, props);

        const account = this.account;

        // Creates Replication Role and attach necessary permissions
        const replicationRole = new Role(this, 'S3AReplicationRole', {
            roleName: 's3a-bops-permissions',
            assumedBy: new CompositePrincipal(
                new ServicePrincipal('batchoperations.s3.amazonaws.com'),
                new ServicePrincipal('s3.amazonaws.com')
            )
        });

        const replicationKMSStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: ['kms:Encrypt', 'kms:Decrypt'],
            resources: ['*'],
        });

        const replicationKmsPolicy = new Policy(this, 'ReplicationKmsPolicy', {
            policyName: 'ReplicationKmsPolicy',
            statements: [replicationKMSStatement],
        });

        const putManifestStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: ["s3:PutObject", "s3:AbortMultipartUpload"],
            resources: [
                "arn:aws:s3:::manifest*",
                "arn:aws:s3:::s3a-migration-reports-bucket*"
            ]
        });

        const s3BopsPolicyStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
                "s3:AbortMultipartUpload",
                "s3:BypassGovernanceRetention",
                "s3:GetJobTagging",
                "s3:GetLifecycleConfiguration",
                "s3:GetObject",
                "s3:GetObjectAcl",
                "s3:GetObjectLegalHold",
                "s3:GetObjectRetention",
                "s3:GetObjectTagging",
                "s3:GetObjectTorrent",
                "s3:GetObjectVersion*",
                "s3:InitiateReplication",
                "s3:PauseReplication",
                "s3:PutAccessPointPublicAccessBlock",
                "s3:PutJobTagging",
                "s3:PutObject*",
                "s3:Replicate*",
                "s3:RestoreObject",
                "s3:TagResource",
                "s3:UntagResource",
                "s3:PutInventoryConfiguration",
                "s3:GetReplicationConfiguration",
                "s3:ListBucket"
            ],
            resources: ["*"],
        });

        const s3BopsPolicy = new Policy(this, 'S3BopsPolicy', {
            policyName: 'S3BopsPolicy',
            statements: [putManifestStatement, s3BopsPolicyStatement],
        });

        replicationRole.attachInlinePolicy(s3BopsPolicy);
        replicationRole.attachInlinePolicy(replicationKmsPolicy);

        // Creates CloudWatch Role and attach necessary permissions
        const cloudwatchRole = new Role(this, 'S3ACloudwatchRole', {
            roleName: 's3a-cloudwatch-permissions',
            assumedBy: new CompositePrincipal(
                new AccountPrincipal(account),
                new ServicePrincipal('cloudwatch.amazonaws.com'),
            )
        });

        const cloudwatchPolicyStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
                'cloudwatch:GetMetricData',
                'cloudwatch:ListMetrics',
                'cloudwatch:GetMetricStatistics'
            ],
            resources: ['*'],
        });

        const cloudwatchPolicy = new Policy(this, 'CloudWatchPolicies', {
            policyName: 'CloudWatchPolicies',
            statements: [cloudwatchPolicyStatement],
        });

        cloudwatchRole.attachInlinePolicy(cloudwatchPolicy);

        // Creates Bucket Permissions Role and attach necessary permissions
        const bucketRole = new Role(this, 'S3ABucketRole', {
            roleName: 's3a-bucket-permissions',
            assumedBy: new AccountPrincipal(account)
        });

        const passRolePolicyStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: ['iam:PassRole'],
            resources: [replicationRole.roleArn]
        });

        const passRolePolicy = new Policy(this, 'PassRolePolicy', {
            policyName: 'PassRolePolicy',
            statements: [passRolePolicyStatement],
        });

        const bucketKMSStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: ['kms:GetKeyPolicy', 'kms:PutKeyPolicy'],
            resources: [`arn:aws:kms:*:*:key/*`],
        });

        const bucketKmsPolicy = new Policy(this, 'BucketKmsPolicy', {
            policyName: 'BucketKmsPolicy',
            statements: [bucketKMSStatement],
        });

        const lambdaAccessPointStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: ['s3:ListAccessPoints'],
            resources: ['*'],
        });

        const manifestLoggingStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: ["s3:CreateBucket", "s3:ListBucket"],
            resources: [
                "arn:aws:s3:::manifest*",
                "arn:aws:s3:::server-access-logging*",
                "arn:aws:s3:::s3a-migration-reports-bucket*"
            ]
        });

        const s3LambdaPolicyStatement = new PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
                "s3:CreateJob",
                "s3:CreateStorageLensGroup",
                "s3:DeleteBucketMetadataTableConfiguration",
                "s3:DeleteJobTagging",
                "s3:DeleteBucketPolicy",
                "s3:DeleteStorageLensConfigurationTagging",
                "s3:DescribeJob",
                "s3:GetAccelerateConfiguration",
                "s3:GetBucketAcl",
                "s3:GetBucketCORS",
                "s3:GetBucketLogging",
                "s3:GetBucketMetadataTableConfiguration",
                "s3:GetBucketNotification",
                "s3:GetBucketObjectLockConfiguration",
                "s3:GetBucketOwnershipControls",
                "s3:GetBucketPolicy",
                "s3:GetBucketPublicAccessBlock",
                "s3:GetBucketRequestPayment",
                "s3:GetBucketTagging",
                "s3:GetBucketVersioning",
                "s3:GetBucketWebsite",
                "s3:GetEncryptionConfiguration",
                "s3:GetJobTagging",
                "s3:GetLifecycleConfiguration",
                "s3:GetReplicationConfiguration",
                "s3:GetStorageLensConfigurationTagging",
                "s3:GetStorageLensGroup",
                "s3:InitiateReplication",
                "s3:ListBucket",
                "s3:ListBucketVersions",
                "s3:ListCallerAccessGrants",
                "s3:ListMultipartUploadParts",
                "s3:ListStorageLensGroups",
                "s3:ListTagsForResource",
                "s3:ObjectOwnerOverrideToBucketOwner",
                "s3:PauseReplication",
                "s3:PutAccessPointPublicAccessBlock",
                "s3:PutBucketAcl",
                "s3:PutBucketCORS",
                "s3:PutBucketLogging",
                "s3:PutBucketObjectLockConfiguration",
                "s3:PutBucketOwnershipControls",
                "s3:PutBucketPolicy",
                "s3:PutBucketRequestPayment",
                "s3:PutBucketTagging",
                "s3:PutBucketVersioning",
                "s3:PutBucketWebsite",
                "s3:PutEncryptionConfiguration",
                "s3:PutInventoryConfiguration",
                "s3:PutJobTagging",
                "s3:PutLifecycleConfiguration",
                "s3:PutReplicationConfiguration",
                "s3:PutStorageLensConfigurationTagging",
                "s3:TagResource",
                "s3:UntagResource",
                "s3:UpdateStorageLensGroup",
                "s3:UpdateJobStatus"
            ],
            resources: ["*"],
        });

        const s3LambdaPolicy = new Policy(this, 'S3BucketPolicy', {
            policyName: 'S3BucketPolicy',
            statements: [manifestLoggingStatement, s3LambdaPolicyStatement, lambdaAccessPointStatement],
        });

        bucketRole.attachInlinePolicy(s3LambdaPolicy);
        bucketRole.attachInlinePolicy(bucketKmsPolicy);
        bucketRole.attachInlinePolicy(passRolePolicy);

        // NAG Suppressions for IAM Stack
        NagSuppressions.addResourceSuppressions(replicationKmsPolicy, [
            {
                id: 'AwsSolutions-IAM5',
                reason: 'KMS permissions require wildcard access for cross-account replication with different encryption keys',
                appliesTo: ['Resource::*']
            }
        ]);

        NagSuppressions.addResourceSuppressions(s3BopsPolicy, [
            {
                id: 'AwsSolutions-IAM5',
                reason: 'S3 replication requires wildcard permissions for manifest buckets, migration reports, and object operations across different buckets',
                appliesTo: [
                    'Resource::arn:aws:s3:::manifest*',
                    'Resource::arn:aws:s3:::s3a-migration-reports-bucket*',
                    'Action::s3:GetObjectVersion*',
                    'Action::s3:PutObject*',
                    'Action::s3:Replicate*',
                    'Resource::*'
                ]
            }
        ]);

        NagSuppressions.addResourceSuppressions(cloudwatchPolicy, [
            {
                id: 'AwsSolutions-IAM5',
                reason: 'CloudWatch monitoring requires wildcard permissions to access metrics across different resources',
                appliesTo: ['Resource::*']
            }
        ]);

        NagSuppressions.addResourceSuppressions(bucketKmsPolicy, [
            {
                id: 'AwsSolutions-IAM5',
                reason: 'KMS key policy management requires wildcard access to keys across regions and accounts',
                appliesTo: ['Resource::arn:aws:kms:*:*:key/*']
            }
        ]);

        NagSuppressions.addResourceSuppressions(s3LambdaPolicy, [
            {
                id: 'AwsSolutions-IAM5',
                reason: 'S3 bucket operations require wildcard permissions for manifest, logging, and migration report buckets',
                appliesTo: [
                    'Resource::arn:aws:s3:::manifest*',
                    'Resource::arn:aws:s3:::s3a-migration-reports-bucket*',
                    'Resource::arn:aws:s3:::server-access-logging*',
                    'Resource::arn:aws:s3:::*',
                    'Resource::*'
                ]
            }
        ]);
    }
}
