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
# Authenticate to your account
ada credentials update --account=<AWS_ACCOUNT> --provider=Isengard --role=Admin --once

cd lib

# You should see the list of stacks
cdk list 

# Deploying the Service Stack deploys all other stacks (except the code pipeline)
cdk deploy --all
```

## Update frontend to use deployed API Gateway URL
```
Update frontend to use deployed API Gateway URL in file: frontend/src/config/config.ts

cd lib

npm run build

cdk deploy FrontendStack
```

### Issues
- If you run into node libaries not found errors, remove your ```node_modules``` directory and try ```npm install```