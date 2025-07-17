#!/bin/bash

set -euo pipefail

# ----------- Configuration ----------
AWS_REGION="eu-west-1"
FORWARDER_FUNCTION_NAME="DatadogIntegration-ForwarderStack-1X0QSW-Forwarder-XCjAm0MJ9pej"
FILTER_NAME="DatadogForwarder"

# ----------- Add Log Group Prefixes -------------
LOG_GROUP_PREFIXES=("prod-green-" "prod-blue-" "veworld-")

echo "Getting account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "Constructing forwarder ARN..."
FORWARDER_ARN="arn:aws:lambda:${AWS_REGION}:${ACCOUNT_ID}:function:${FORWARDER_FUNCTION_NAME}"
echo "Forwarder ARN: $FORWARDER_ARN"


for PREFIX in "${LOG_GROUP_PREFIXES[@]}"; do
  echo "Searching for log groups with prefix: $PREFIX"

  LOG_GROUPS=$(aws logs describe-log-groups \
    --log-group-name-prefix "$PREFIX" \
    --region "$AWS_REGION" \
    --query 'logGroups[*].logGroupName' \
    --output text)

  if [[ -z "$LOG_GROUPS" ]]; then
    echo "No log groups found for prefix: $PREFIX"
    continue
  fi

  for LOG_GROUP in $LOG_GROUPS; do
    echo "Checking subscription for: $LOG_GROUP"

    FILTER_EXISTS=$(aws logs describe-subscription-filters \
      --log-group-name "$LOG_GROUP" \
      --region "$AWS_REGION" \
      --query 'subscriptionFilters[?filterName==`'"$FILTER_NAME"'`]' \
      --output text)

    if [[ -n "$FILTER_EXISTS" ]]; then
      echo "Already subscribed, skipping: $LOG_GROUP"
      continue
    fi

    echo "Subscribing: $LOG_GROUP"
    aws logs put-subscription-filter \
      --log-group-name "$LOG_GROUP" \
      --filter-name "$FILTER_NAME" \
      --filter-pattern "" \
      --destination-arn "$FORWARDER_ARN" \
      --region "$AWS_REGION"

    echo "Subscribed: $LOG_GROUP"
  done
done
