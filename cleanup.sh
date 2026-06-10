#!/bin/bash

# Cleanup script for BOPS Parallelization sample
#
# Deletes all runtime-created and CDK-managed resources in the correct order:
#   1. Empty and delete runtime-created S3 buckets (including versioned objects)
#   2. Delete DynamoDB table items
#   3. Destroy CDK stacks
#
# The CloudFormation stack alone is insufficient for full cleanup because:
#   - The DynamoDB table has RemovalPolicy.RETAIN (intentional, for data safety)
#   - S3 buckets created at runtime by the workflow Lambdas are never tracked by CDK
#
# Usage: sh cleanup.sh [--dry-run]

set -e

STACK_NAME="BOPSParallelizationStack"
IAM_STACK_NAME="BOPSParallelizationIAMStack"
DYNAMODB_TABLE="S3A_WORKFLOWS"
AWS_PROFILE="${AWS_PROFILE:-default}"
REGION="${AWS_DEFAULT_REGION:-us-west-2}"
DRY_RUN=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Parse args
for arg in "$@"; do
  case $arg in
    --dry-run) DRY_RUN=true ;;
  esac
done

if $DRY_RUN; then
  echo -e "${YELLOW}[DRY RUN] No changes will be made.${NC}"
fi

log()     { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ─── helpers ────────────────────────────────────────────────────────────────

command_exists() { command -v "$1" >/dev/null 2>&1; }

require_tool() {
  if ! command_exists "$1"; then
    error "$1 is required but not installed."
    exit 1
  fi
}

# Empty an S3 bucket including all versioned objects and delete markers, then
# delete the bucket itself.
purge_bucket() {
  local bucket="$1"

  # Confirm the bucket actually exists
  if ! aws s3api head-bucket --bucket "$bucket" --region "$REGION" 2>/dev/null; then
    warn "Bucket s3://${bucket} not found – skipping."
    return
  fi

  log "Purging all objects from s3://${bucket} ..."

  if $DRY_RUN; then
    warn "[DRY RUN] Would delete all objects and versions from s3://${bucket}"
    return
  fi

  # Delete all object versions and delete markers in batches
  aws s3api list-object-versions --bucket "$bucket" --region "$REGION" \
    --output json --query '{Objects: Versions[].{Key:Key,VersionId:VersionId}}' \
    2>/dev/null | \
    python3 -c "
import sys, json, subprocess, boto3
data = json.load(sys.stdin)
objects = (data or {}).get('Objects') or []
if not objects:
    print('  No versioned objects found.')
    sys.exit(0)
s3 = boto3.client('s3', region_name='$REGION')
for i in range(0, len(objects), 1000):
    batch = objects[i:i+1000]
    resp = s3.delete_objects(Bucket='$bucket', Delete={'Objects': batch, 'Quiet': True})
    errs = resp.get('Errors', [])
    if errs:
        print(f'  Errors: {errs}', file=sys.stderr)
print(f'  Deleted {len(objects)} version(s).')
" || true

  # Delete all delete markers
  aws s3api list-object-versions --bucket "$bucket" --region "$REGION" \
    --output json --query '{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}' \
    2>/dev/null | \
    python3 -c "
import sys, json, boto3
data = json.load(sys.stdin)
objects = (data or {}).get('Objects') or []
if not objects:
    sys.exit(0)
s3 = boto3.client('s3', region_name='$REGION')
for i in range(0, len(objects), 1000):
    batch = objects[i:i+1000]
    s3.delete_objects(Bucket='$bucket', Delete={'Objects': batch, 'Quiet': True})
print(f'  Removed {len(objects)} delete marker(s).')
" || true

  # Remove any remaining non-versioned objects
  aws s3 rm "s3://${bucket}" --recursive --region "$REGION" 2>/dev/null || true

  # Now delete the bucket
  aws s3api delete-bucket --bucket "$bucket" --region "$REGION" 2>/dev/null && \
    success "Deleted bucket s3://${bucket}" || \
    warn "Could not delete s3://${bucket} – it may already be gone."
}

# Find and purge all S3 buckets whose names match a given prefix
purge_buckets_by_prefix() {
  local prefix="$1"
  log "Looking for buckets matching prefix: ${prefix}*"
  local buckets
  buckets=$(aws s3api list-buckets --region "$REGION" \
    --query "Buckets[?starts_with(Name, '${prefix}')].Name" \
    --output text 2>/dev/null) || true

  if [ -z "$buckets" ]; then
    warn "No buckets found with prefix '${prefix}'."
    return
  fi

  for bucket in $buckets; do
    purge_bucket "$bucket"
  done
}

# ─── pre-flight ─────────────────────────────────────────────────────────────

require_tool aws
require_tool python3
require_tool cdk

log "Verifying AWS credentials..."
aws sts get-caller-identity >/dev/null 2>&1 || {
  error "Not authenticated to AWS. Configure credentials and retry."
  exit 1
}

ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
log "Account: ${ACCOUNT}  |  Region: ${REGION}"

echo ""
echo -e "${RED}┌─────────────────────────────────────────────────────────────────────┐${NC}"
echo -e "${RED}│  WARNING: This will PERMANENTLY delete resources in account          │${NC}"
echo -e "${RED}│  ${ACCOUNT}. This action is IRREVERSIBLE.              │${NC}"
echo -e "${RED}└─────────────────────────────────────────────────────────────────────┘${NC}"
echo ""

if ! $DRY_RUN; then
  read -r -p "Type 'yes' to proceed: " confirm
  if [ "$confirm" != "yes" ]; then
    echo "Aborted."
    exit 0
  fi
fi

# ─── Step 1: Purge runtime-created S3 buckets ───────────────────────────────

echo ""
log "=== Step 1: Purging runtime-created S3 buckets ==="

# Test source/destination buckets
purge_buckets_by_prefix "bops-test-source-${ACCOUNT}"
purge_buckets_by_prefix "bops-test-dest-${ACCOUNT}"

# Source buckets created by the workflow (pattern from invoke-workflow-payload.json)
purge_buckets_by_prefix "src-test-bopspar-${ACCOUNT}"

# Manifest buckets written to by Lambdas and S3 Batch Ops
purge_buckets_by_prefix "manifest"

# Inventory/migration report buckets
purge_buckets_by_prefix "s3a-migration-reports-bucket"

# Server-access-logging buckets
purge_buckets_by_prefix "server-access-logging"

# ─── Step 2: Delete DynamoDB table (RemovalPolicy.RETAIN bypasses CDK) ──────

echo ""
log "=== Step 2: Deleting DynamoDB table '${DYNAMODB_TABLE}' ==="

TABLE_EXISTS=$(aws dynamodb describe-table --table-name "$DYNAMODB_TABLE" \
  --region "$REGION" --query 'Table.TableName' --output text 2>/dev/null) || TABLE_EXISTS=""

if [ -n "$TABLE_EXISTS" ]; then
  if $DRY_RUN; then
    warn "[DRY RUN] Would delete DynamoDB table '${DYNAMODB_TABLE}'."
  else
    aws dynamodb delete-table --table-name "$DYNAMODB_TABLE" --region "$REGION" >/dev/null
    log "Waiting for table deletion to complete..."
    aws dynamodb wait table-not-exists --table-name "$DYNAMODB_TABLE" --region "$REGION"
    success "DynamoDB table '${DYNAMODB_TABLE}' deleted."
  fi
else
  warn "DynamoDB table '${DYNAMODB_TABLE}' not found – skipping."
fi

# ─── Step 3: Destroy CDK stacks ─────────────────────────────────────────────

echo ""
log "=== Step 3: Destroying CDK stacks ==="

cd "$(dirname "$0")/lib"

if $DRY_RUN; then
  warn "[DRY RUN] Would run: cdk destroy ${IAM_STACK_NAME} --force && cdk destroy ${STACK_NAME} --force"
  cd - >/dev/null
else
  log "Destroying ${IAM_STACK_NAME}..."
  cdk destroy "$IAM_STACK_NAME" --force && success "${IAM_STACK_NAME} destroyed."

  log "Destroying ${STACK_NAME}..."
  cdk destroy "$STACK_NAME" --force && success "${STACK_NAME} destroyed."

  cd - >/dev/null
fi

# ─── Done ────────────────────────────────────────────────────────────────────

echo ""
success "Cleanup complete. All BOPS Parallelization resources have been removed."
