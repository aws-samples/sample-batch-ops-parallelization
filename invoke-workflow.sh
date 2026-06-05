#!/bin/bash

# Script to invoke BOPSParallelMainHandler Lambda function
# Uses invoke-workflow-payload.json as the payload

set -e  # Exit on any error

FUNCTION_NAME="BOPSParallelMainHandler"
PAYLOAD_FILE="invoke-workflow-payload.json"
OUTPUT_FILE="lambda-response.json"

echo "Invoking Lambda function: $FUNCTION_NAME"
echo "Using payload from: $PAYLOAD_FILE"

# Check if payload file exists
if [ ! -f "$PAYLOAD_FILE" ]; then
    echo "Error: Payload file '$PAYLOAD_FILE' not found!"
    exit 1
fi

# Invoke the Lambda function
echo "Executing Lambda invocation..."
aws lambda invoke \
    --function-name "$FUNCTION_NAME" \
    --payload "file://$PAYLOAD_FILE" \
    --cli-binary-format raw-in-base64-out \
    "$OUTPUT_FILE"

# Check if invocation was successful
if [ $? -eq 0 ]; then
    echo "Lambda invocation completed successfully!"
    echo "Response saved to: $OUTPUT_FILE"
    echo ""
    echo "Response content:"
    cat "$OUTPUT_FILE"
    echo ""
else
    echo "Error: Lambda invocation failed!"
    exit 1
fi
