#!/bin/bash

# CloudFront Staging Cache Behavior Test Suite
# This script tests all cache behaviors configured for the staging CloudFront distribution
# Domain: https://indexer.testnet.vechain.org
# Staging Header: aws-cf-cd-staging=testnet

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="https://indexer.testnet.vechain.org"
STAGING_HEADER="aws-cf-cd-staging: testnet"
OUTPUT_DIR="./test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Log file
LOG_FILE="$OUTPUT_DIR/test-results-${TIMESTAMP}.log"

# Function to print colored output
print_header() {
    echo -e "${BLUE}================================================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================================================================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Function to test an endpoint
test_endpoint() {
    local name="$1"
    local path="$2"
    local cache_policy="$3"
    
    print_header "Testing: $name"
    echo "Path: $path"
    echo "Expected Cache Policy: $cache_policy"
    echo ""
    
    local full_url="${BASE_URL}${path}"
    local output_file="$OUTPUT_DIR/${name// /_}-${TIMESTAMP}.txt"
    
    echo "Request: curl -I -H \"$STAGING_HEADER\" \"$full_url\"" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
    
    # Make the request and capture headers
    response=$(curl -s -i -H "$STAGING_HEADER" "$full_url" 2>&1)
    
    # Save full response to file
    echo "$response" > "$output_file"
    
    # Extract key headers
    echo "Response Headers:" | tee -a "$LOG_FILE"
    echo "$response" | grep -i "HTTP\|cache-control\|x-cache\|age\|via\|x-amz-cf" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
    
    # Check if CloudFront is serving the request
    if echo "$response" | grep -q "x-amz-cf-id\|via.*cloudfront"; then
        print_success "Request served by CloudFront"
    else
        print_error "Request NOT served by CloudFront"
    fi
    
    # Check cache status
    if echo "$response" | grep -qi "x-cache.*Hit"; then
        print_success "Cache HIT - Content served from cache"
    elif echo "$response" | grep -qi "x-cache.*Miss"; then
        print_info "Cache MISS - Content fetched from origin (expected on first request)"
    elif echo "$response" | grep -qi "x-cache.*RefreshHit"; then
        print_info "Cache REFRESH HIT - Cached content revalidated"
    else
        print_info "Cache status: Unknown"
    fi
    
    # Check HTTP status
    status_code=$(echo "$response" | grep "HTTP" | head -1 | awk '{print $2}')
    if [ "$status_code" = "200" ]; then
        print_success "HTTP Status: $status_code OK"
    elif [ "$status_code" = "404" ]; then
        print_error "HTTP Status: $status_code - Endpoint not found (may need actual data)"
    else
        print_info "HTTP Status: $status_code"
    fi
    
    echo "" | tee -a "$LOG_FILE"
    echo "Full response saved to: $output_file" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
    
    # Wait a bit between requests
    sleep 2
}

# Main test execution
print_header "CloudFront Staging Cache Behavior Test Suite"
echo "Base URL: $BASE_URL"
echo "Staging Header: $STAGING_HEADER"
echo "Log File: $LOG_FILE"
echo ""
echo "Starting tests at: $(date)" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# Test 1: 1-hour cache behavior
test_endpoint \
    "1-hour NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-hour" \
    "hourly (5 min TTL)"

# Test 2: 1-day cache behavior
test_endpoint \
    "1-day NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-day" \
    "day (5 min TTL)"

# Test 3: 1-week cache behavior
test_endpoint \
    "1-week NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-week" \
    "weekly (5 min TTL)"

# Test 4: 1-month cache behavior
test_endpoint \
    "1-month NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-month" \
    "monthly (5 min TTL)"

# Test 5: B3TR Global Overview
test_endpoint \
    "B3TR Global Overview" \
    "/api/v1/b3tr/actions/global/overview" \
    "day (5 min TTL)"

# Test 6: B3TR Apps Overview (with wildcard)
# Note: Replace * with an actual app ID for testing
test_endpoint \
    "B3TR Apps Overview" \
    "/api/v1/b3tr/actions/apps/example-app/overview" \
    "hourly (5 min TTL)"

# Test 7: B3TR Galaxy Members Overview
test_endpoint \
    "B3TR Galaxy Members Overview" \
    "/api/v1/b3tr/galaxy-members/level-overview" \
    "hourly (5 min TTL)"

# Test 8: Stargate Total VTHO Claimed Historic (with wildcard)
test_endpoint \
    "Stargate VTHO Claimed 1-day" \
    "/api/v1/stargate/total-vtho-claimed/historic/1-day" \
    "hourly (5 min TTL)"

# Test 9: Stargate Total VET Staked Historic (with wildcard)
test_endpoint \
    "Stargate VET Staked 1-day" \
    "/api/v1/stargate/total-vet-staked/historic/1-day" \
    "hourly (5 min TTL)"

# Test 10: B3TR Proposal Results (with wildcard)
# Note: Replace * with an actual proposal ID for testing
test_endpoint \
    "B3TR Proposal Results" \
    "/api/v1/b3tr/proposals/example-proposal/results" \
    "hourly (5 min TTL)"

# Additional test: Default behavior (uncached endpoint)
print_header "Testing: Default Behavior (Uncached)"
echo "This tests an endpoint that doesn't match any specific cache behavior"
echo ""
test_endpoint \
    "Default Behavior Test" \
    "/api/v1/health" \
    "default (no cache)"

# Summary
print_header "Test Suite Complete"
echo "Total tests completed: 11"
echo "Results saved to: $OUTPUT_DIR"
echo "Log file: $LOG_FILE"
echo ""
echo "Completed at: $(date)" | tee -a "$LOG_FILE"
echo ""

print_info "To verify caching is working:"
echo "  1. Run this script twice - second run should show more cache HITs"
echo "  2. Check the 'Age' header to see how long content has been cached"
echo "  3. Look for 'x-cache: Hit from cloudfront' in responses"
echo ""

print_info "To test with real data:"
echo "  1. Replace wildcard paths (*/example-app, */example-proposal) with actual IDs"
echo "  2. Ensure your API endpoints are returning valid data"
echo "  3. Monitor CloudFront metrics in AWS Console"
echo ""

print_success "Test suite execution complete!"

