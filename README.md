# S3 Accelerator(S3A) - BOPS Parallelization Blog

## Getting started```

1. Clone the repository:

```
git clone git@ssh.code.aws.dev:proserve/s3ablog-parallel-bops/BOPSParallelization.git
```


## Prerequisites

- Java 17 or higher
- Gradle 8.x

## Building the Project

```bash
./gradlew shadowJar
```

## Running tests

```bash
./gradlew test
```

## Deployment
```
ada credentials update --account=<AWS_ACCOUNT> --provider=Isengard --role=Admin --once

cd lib

# You should see the stacks with your AWS Account ID in the list
cdk list 

# Deploying the Service Stack deploys all other stacks (except the code pipeline)
npx cdk deploy PipelineStack/S3AExternalization-dev/ServiceStack-<AWS_ACCOUNT>-us-west-2-dev
```
