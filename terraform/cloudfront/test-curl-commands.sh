#!/bin/bash

# Quick Curl Commands for Testing Staging CloudFront Cache Behaviors
# Domain: https://indexer.testnet.vechain.org
# Staging Header: aws-cf-cd-staging=testnet

BASE_URL="https://indexer.testnet.vechain.org"

echo "=========================================="
echo "CloudFront Staging Cache Behavior Tests"
echo "=========================================="
echo ""

echo "1. Testing 1-hour cache behavior (hourly cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/stargate/nft-holders/historic/1-hour\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/stargate/nft-holders/historic/1-hour"
echo ""
echo "---"
echo ""

echo "2. Testing 1-day cache behavior (day cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/stargate/nft-holders/historic/1-day\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/stargate/nft-holders/historic/1-day"
echo ""
echo "---"
echo ""

echo "3. Testing 1-week cache behavior (weekly cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/stargate/nft-holders/historic/1-week\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/stargate/nft-holders/historic/1-week"
echo ""
echo "---"
echo ""

echo "4. Testing 1-month cache behavior (monthly cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/stargate/nft-holders/historic/1-month\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/stargate/nft-holders/historic/1-month"
echo ""
echo "---"
echo ""

echo "5. Testing B3TR Global Overview (day cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/b3tr/actions/global/overview\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/b3tr/actions/global/overview"
echo ""
echo "---"
echo ""

echo "6. Testing B3TR Apps Overview (hourly cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/b3tr/actions/apps/example-app/overview\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/b3tr/actions/apps/example-app/overview"
echo ""
echo "---"
echo ""

echo "7. Testing B3TR Galaxy Members Overview (hourly cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/b3tr/galaxy-members/level-overview\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/b3tr/galaxy-members/level-overview"
echo ""
echo "---"
echo ""

echo "8. Testing Stargate Total VTHO Claimed Historic (hourly cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/stargate/total-vtho-claimed/historic/1-day\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/stargate/total-vtho-claimed/historic/1-day"
echo ""
echo "---"
echo ""

echo "9. Testing Stargate Total VET Staked Historic (hourly cache policy):"
echo "curl -I -H \"aws-cf-cd-staging: testnet\" \"${BASE_URL}/api/v1/stargate/total-vet-staked/historic/1-day\""
curl -I -H "aws-cf-cd-staging: testnet" "${BASE_URL}/api/v1/stargate/total-vet-staked/historic/1-day"
echo ""
echo "---"
echo ""

echo "=========================================="
echo "All tests completed!"
echo "=========================================="
echo ""
echo "Key headers to check:"
echo "  - x-cache: Should show 'Hit from cloudfront' on subsequent requests"
echo "  - age: Shows how long the response has been cached (in seconds)"
echo "  - cache-control: Shows the cache directives"
echo "  - x-amz-cf-id: Confirms request went through CloudFront"
echo ""

