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
FAILURES=0

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
    FAILURES=$((FAILURES + 1))
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
    
    # Check cache status. RefreshHit first: it also matches the broader Hit pattern.
    if echo "$response" | grep -qi "x-cache.*RefreshHit"; then
        print_info "Cache REFRESH HIT - Cached content revalidated"
    elif echo "$response" | grep -qi "x-cache.*Hit"; then
        print_success "Cache HIT - Content served from cache"
    elif echo "$response" | grep -qi "x-cache.*Miss"; then
        print_info "Cache MISS - Content fetched from origin (expected on first request)"
    else
        print_info "Cache status: Unknown"
    fi
    
    # The endpoint owns the TTL, so the header it sent is what this suite is checking.
    actual_cache_control=$(echo "$response" | grep -i "^cache-control:" | head -1 |
        cut -d: -f2- | tr -d '\r' | sed 's/^ *//;s/ *$//')
    if [ "$expected_cache_control" = "graded" ]; then
        if echo "$actual_cache_control" | grep -qE "^public, max-age=[0-9]+$"; then
            print_success "Cache-Control: $actual_cache_control (graded by content age)"
        else
            print_error "Cache-Control: '$actual_cache_control' is not a graded max-age"
        fi
    elif [ "$actual_cache_control" = "$expected_cache_control" ]; then
        print_success "Cache-Control: $actual_cache_control"
    else
        print_error "Cache-Control: expected '$expected_cache_control', got '$actual_cache_control'"
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

# Expectations are copied from each endpoint's @CacheFor; "graded" means max-age from content age.
test_endpoint \
    "1-hour NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-hour" \
    "public, max-age=600"

test_endpoint \
    "1-day NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-day" \
    "public, max-age=600"

test_endpoint \
    "1-week NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-week" \
    "public, max-age=3600"

test_endpoint \
    "1-month NFT Holders" \
    "/api/v1/stargate/nft-holders/historic/1-month" \
    "public, max-age=86400"

test_endpoint \
    "B3TR Global Overview" \
    "/api/v1/b3tr/actions/global/overview" \
    "public, max-age=86400"

# Note: replace the app id with a real one for a 200.
test_endpoint \
    "B3TR Apps Overview" \
    "/api/v1/b3tr/actions/apps/example-app/overview" \
    "public, max-age=3600"

test_endpoint \
    "B3TR Galaxy Members Overview" \
    "/api/v1/b3tr/galaxy-members/level-overview" \
    "public, max-age=3600"

test_endpoint \
    "Stargate VTHO Claimed 1-day" \
    "/api/v1/stargate/total-vtho-claimed/historic/1-day" \
    "public, max-age=600"

test_endpoint \
    "Stargate VET Staked 1-day" \
    "/api/v1/stargate/total-vet-staked/historic/1-day" \
    "public, max-age=600"

# Note: replace the proposal id with a real one for a 200.
test_endpoint \
    "B3TR Proposal Results" \
    "/api/v1/b3tr/proposals/example-proposal/results" \
    "public, max-age=600"

# The head range never settles, so it falls back to the volatile tier.
test_endpoint \
    "Blocks Head Range" \
    "/api/v1/blocks?size=5" \
    "public, max-age=0, s-maxage=10"

test_endpoint \
    "Blocks Historical Range" \
    "/api/v1/blocks?from=1000&size=5" \
    "graded"

# Note: replace the id with a real transaction to see a graded header rather than a 404.
test_endpoint \
    "Transaction By Id" \
    "/api/v1/transactions/0x0000000000000000000000000000000000000000000000000000000000000000" \
    "graded"

# springdoc sends no header of its own, so the edge behaviour still picks this one.
test_endpoint \
    "API Docs" \
    "/api-docs" \
    ""

# Summary
print_header "Test Suite Complete"
echo "Total tests completed: 14"
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

if [ "$FAILURES" -gt 0 ]; then
    echo -e "${RED}✗ Test suite finished with $FAILURES failure(s)${NC}"
    exit 1
fi

print_success "Test suite execution complete!"

