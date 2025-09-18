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
## Usage
- TODO
## Integrate with your tools

- [ ] [Set up project integrations](https://gitlab.aws.dev/s3a/s3a-externalization/-/settings/integrations)

## Collaborate with your team

- [ ] [Invite team members and collaborators](https://docs.gitlab.com/ee/user/project/members/)
- [ ] [Create a new merge request](https://docs.gitlab.com/ee/user/project/merge_requests/creating_merge_requests.html)
- [ ] [Automatically close issues from merge requests](https://docs.gitlab.com/ee/user/project/issues/managing_issues.html#closing-issues-automatically)
- [ ] [Enable merge request approvals](https://docs.gitlab.com/ee/user/project/merge_requests/approvals/)
- [ ] [Set auto-merge](https://docs.gitlab.com/ee/user/project/merge_requests/merge_when_pipeline_succeeds.html)

## Test and Deploy

Use the built-in continuous integration in GitLab.

- [ ] [Get started with GitLab CI/CD](https://docs.gitlab.com/ee/ci/quick_start/index.html)
- [ ] [Analyze your code for known vulnerabilities with Static Application Security Testing (SAST)](https://docs.gitlab.com/ee/user/application_security/sast/)
- [ ] [Deploy to Kubernetes, Amazon EC2, or Amazon ECS using Auto Deploy](https://docs.gitlab.com/ee/topics/autodevops/requirements.html)
- [ ] [Use pull-based deployments for improved Kubernetes management](https://docs.gitlab.com/ee/user/clusters/agent/)
- [ ] [Set up protected environments](https://docs.gitlab.com/ee/ci/environments/protected_environments.html)

***