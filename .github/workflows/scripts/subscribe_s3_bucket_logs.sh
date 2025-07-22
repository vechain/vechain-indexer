#!/bin/bash

set -euo pipefail

# ----------- Configuration ----------
AWS_REGION="eu-west-1"
FORWARDER_FUNCTION_NAME="DatadogIntegration-ForwarderStack-1X0QSW-Forwarder-XCjAm0MJ9pej"


# List of S3 bucket names to configure
S3_BUCKETS=("prod-blue-veworld-main-api-ecs-lb-bucket" "prod-blue-veworld-test-api-ecs-lb-bucket" "prod-green-veworld-main-api-ecs-lb-bucket" "prod-green-veworld-test-api-ecs-lb-bucket")

# ----------- Get AWS Account ID ----------
echo "Getting AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

FORWARDER_ARN="arn:aws:lambda:${AWS_REGION}:${ACCOUNT_ID}:function:${FORWARDER_FUNCTION_NAME}"
echo "Datadog Forwarder ARN: $FORWARDER_ARN"

# ----------- Loop over each S3 bucket -----------
for BUCKET in "${S3_BUCKETS[@]}"; do
  echo "Configuring bucket: $BUCKET"

  # Generate a unique statement ID
  STATEMENT_ID="s3invoke-${BUCKET//[^a-zA-Z0-9]/}-$(date +%s)"

  echo "Adding Lambda invoke permission for $BUCKET"
  aws lambda add-permission \
    --function-name "$FORWARDER_FUNCTION_NAME" \
    --principal s3.amazonaws.com \
    --statement-id "$STATEMENT_ID" \
    --action "lambda:InvokeFunction" \
    --source-arn "arn:aws:s3:::${BUCKET}" \
    --region "$AWS_REGION" || echo "Permission may already exist. Continuing..."

  echo "Adding notification configuration for $BUCKET"
  aws s3api put-bucket-notification-configuration \
    --bucket "$BUCKET" \
    --notification-configuration "{
      \"LambdaFunctionConfigurations\": [
        {
          \"LambdaFunctionArn\": \"${FORWARDER_ARN}\",
          \"Events\": [\"s3:ObjectCreated:*\"]        
        }
      ]
    }"

  echo "S3 bucket configured: $BUCKET"
done
