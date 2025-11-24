# BOPS Parallelization Blog

## Getting started

## Prerequisites

- Java 17 or higher
- Gradle 8.x
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) 
- [AWS CDK](https://docs.aws.amazon.com/cdk/v2/guide/getting-started.html)


### Step 1. Clone the repository:

```
git clone git@ssh.code.aws.dev:proserve/s3ablog-parallel-bops/BOPSParallelization.git
```

## Step 2. Deployment to your AWS account

Make sure you are authenticated to your account with enough privileges to create IAM roles, and you are on AWS region us-west-2 then cd into the repository's <strong>lib</strong> directory and run the deploy.sh command

(Assuming you have cloned the repository into the BOPSParallelization)
```
cd ./BOPSParallelization/lib
sh ./deploy.sh

```

