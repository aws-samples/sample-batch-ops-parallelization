#!/bin/bash

# Exit on any error
set -e

# Variables
STACK_NAME="BOPSParallelizationStack"
IAM_STACK_NAME="BOPSParallelizationIAMStack"
AWS_PROFILE="${AWS_PROFILE:-default}"  # Use specified AWS_PROFILE or default

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

echo -e "${YELLOW}Starting deployment process for ${STACK_NAME}...${NC}"

# Check if necessary tools are installed
if ! command_exists aws; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    exit 1
fi

if ! command_exists cdk; then
    echo -e "${RED}Error: AWS CDK is not installed${NC}"
    exit 1
fi

# Check if we're logged in to AWS
aws sts get-caller-identity >/dev/null 2>&1 || {
    echo -e "${RED}Error: Not logged in to AWS. Please configure your AWS credentials.${NC}"
    exit 1
}

# Install dependencies if needed
if [ -f "package.json" ]; then
    echo -e "${YELLOW}Installing dependencies...${NC}"
    npm install
fi

# Build TypeScript if needed
if [ -f "tsconfig.json" ]; then
    echo -e "${YELLOW}Building TypeScript...${NC}"
    npm run build
fi

# Deploy the stack
echo -e "${YELLOW}Deploying ${STACK_NAME}...${NC}"
cdk deploy ${STACK_NAME} --require-approval never

# Deploy the stack
echo -e "${YELLOW}Deploying ${IAM_STACK_NAME}...${NC}"
cdk deploy ${IAM_STACK_NAME} --require-approval never

# Check if deployment was successful
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Deployment completed successfully!${NC}"
else
    echo -e "${RED}Deployment failed!${NC}"
    exit 1
fi
