# BOPS Parallelization Blog

## Getting started

## Prerequisites

- Java 21 or higher
- Gradle 8.x
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) 
- [AWS CDK](https://docs.aws.amazon.com/cdk/v2/guide/getting-started.html)


### Step 1. Clone the repository:

```
git clone git@github.com:aws-samples/sample-batch-ops-parallelization.git
```

## Step 2. Deployment to your AWS account

Make sure you are authenticated to your account with enough privileges to create IAM roles, and you are on AWS region us-west-2 then cd into the repository's <strong>lib</strong> directory and run the deploy.sh command

(Assuming you have cloned the repository into the BOPSParallelization)
```
cd ./BOPSParallelization/lib
sh ./deploy.sh

```

> **Note:** The manifest-split Glue job's `ROW_LIMIT` (approximate max rows per output manifest file) is configurable per deployment via the `rowLimit` value in `cdk.json`. It defaults to `5000000000` and is clamped to the supported `1000000000`–`5000000000` range. To change it, edit `rowLimit` in `cdk.json` and re-run `deploy.sh`.

## Step 3. Run a migration

Find the lambda function called "BOPSParallelMainHandler" and modify and add the following to the test:

```
{
  "workflow": {
    "workflowName": "testWorkflowName", 
    "namespaceID": "testNameSpaceID",
    "workflowType": "S3_REPLICATION",
    "sourceBucketARN": "arn:aws:s3:::{source_bucket_name}",
    "destBucketARN": "arn:aws:s3:::{dest_bucket_name}",
    "sourceRoleARN": "arn:aws:iam::{account_id}:role/s3a-bucket-permissions",
    "destRoleARN": "arn:aws:iam::{account_id}:role/s3a-bucket-permissions",
    "sourceAccountNumber": "{account_id}",
    "destAccountNumber": "{account_id}", 
    "sourceRegion": "{source_bucket_region}",
    "destRegion": "{dest_bucket_region}"
  }
}
```

Then go to the step function called "S3AWorkflow" and click start execution and add the following:

```
{
  "workflowName": "testWorkflowName"
  "namespaceID": "testNameSpaceID",
}
```

## Step 4. Cleanup



To avoid ongoing charges, run the provided cleanup script from the repository root:

```bash
sh ./cleanup.sh
```

The script will, in order:

1. **Empty and delete runtime-created S3 buckets** — buckets matching the prefixes `src-test-bopspar-<account>`, `manifest*`, `s3a-migration-reports-bucket*`, and `server-access-logging*` are purged (all versions and delete markers are removed before deletion).
2. **Delete the DynamoDB table** — `S3A_WORKFLOWS` is created with `RemovalPolicy.RETAIN`, so it survives a `cdk destroy` and must be deleted explicitly.
3. **Destroy the CDK stacks** — `BOPSParallelizationIAMStack` then `BOPSParallelizationStack` are torn down via `cdk destroy --force`.

To preview what would be deleted without making any changes:

```bash
sh ./cleanup.sh --dry-run
```

> **Note:** The script requires `aws`, `python3` (with `boto3`), and `cdk` on your PATH, and valid AWS credentials scoped to the deployment account and region (`us-west-2` by default).

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This library is licensed under the MIT-0 License. See the LICENSE file.
