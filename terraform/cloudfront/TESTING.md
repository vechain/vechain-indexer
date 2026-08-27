# CloudFront Staging Cache Behavior Testing Guide

This guide provides instructions and commands for testing the CloudFront staging distribution cache behaviors.

## Domain and Header Configuration

- **Domain:** `https://indexer.testnet.vechain.org`
- **Staging Header:** `aws-cf-cd-staging: testnet`
- **Purpose:** Routes traffic to staging CloudFront distribution for testing

## Quick Start

### Option 1: Run the Full Test Suite
```bash
./test-staging-cache-behaviors.sh
```
This will test all cache behaviors and save detailed results to the `test-results/` directory.

### Option 2: Run Quick Curl Tests
```bash
./test-curl-commands.sh
```
This runs all curl commands in sequence and shows the response headers.

### Option 3: Manual Testing (Copy-Paste Commands)

Use the individual curl commands below for manual testing:

## Individual Test Commands

### 1. 1-Hour Cache Behavior (Hourly Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/nft-holders/historic/1-hour"
```

### 2. 1-Day Cache Behavior (Day Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/nft-holders/historic/1-day"
```

### 3. 1-Week Cache Behavior (Weekly Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/nft-holders/historic/1-week"
```

### 4. 1-Month Cache Behavior (Monthly Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/nft-holders/historic/1-month"
```

### 5. B3TR Global Overview (Day Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/b3tr/actions/global/overview"
```

### 6. B3TR Apps Overview (Hourly Cache Policy - 5 min TTL)
```bash
# Replace 'example-app' with actual app ID
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/b3tr/actions/apps/example-app/overview"
```

### 7. B3TR Galaxy Members Overview (Hourly Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/b3tr/galaxy-members/level-overview"
```

### 8. Stargate Total VTHO Claimed Historic (Hourly Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/total-vtho-claimed/historic/1-day"
```

### 9. Stargate Total VET Staked Historic (Hourly Cache Policy - 5 min TTL)
```bash
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/total-vet-staked/historic/1-day"
```

### 10. B3TR Proposal Results (Hourly Cache Policy - 5 min TTL)
```bash
# Replace 'example-proposal' with actual proposal ID
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/b3tr/proposals/example-proposal/results"
```

## Understanding the Response Headers

### Key Headers to Check:

1. **x-cache**
   - `Hit from cloudfront` - Content served from CloudFront cache (GOOD!)
   - `Miss from cloudfront` - Content fetched from origin (expected on first request)
   - `RefreshHit from cloudfront` - Cached content was revalidated

2. **age**
   - Shows how long (in seconds) the response has been cached
   - Example: `age: 120` means cached for 2 minutes

3. **cache-control**
   - Shows cache directives from origin
   - Example: `cache-control: max-age=300` means 5 minutes cache

4. **x-amz-cf-id**
   - CloudFront request ID
   - Confirms the request went through CloudFront

5. **via**
   - Should contain "cloudfront"
   - Example: `via: 1.1 abc123.cloudfront.net (CloudFront)`

## Testing Strategy

### First Run (Cache MISS Expected)
```bash
# Run any endpoint - should see "Miss from cloudfront"
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/nft-holders/historic/1-hour"
```

### Second Run (Cache HIT Expected)
```bash
# Run the same endpoint within 5 minutes - should see "Hit from cloudfront"
curl -I -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/stargate/nft-holders/historic/1-hour"
```

### Check Cache Age
```bash
# Look for the 'age' header in the response
# age: 60 means the content has been cached for 60 seconds
```

## Testing with Full Response Body

To see the actual response data (not just headers), remove the `-I` flag:

```bash
curl -H "aws-cf-cd-staging: testnet" \
  "https://indexer.testnet.vechain.org/api/v1/b3tr/actions/global/overview" | jq
```

(Add `| jq` to pretty-print JSON responses if you have jq installed)

## Troubleshooting

### Issue: Not seeing CloudFront headers
**Solution:** Verify that:
1. The staging header is correct: `aws-cf-cd-staging: testnet`
2. The continuous deployment policy is configured in production
3. You're testing against the correct domain

### Issue: Always getting Cache MISS
**Solution:**
1. Wait a few seconds between requests
2. Use the exact same URL (query parameters matter!)
3. Check if the cache policy TTL has expired (5 minutes for most staging endpoints)

### Issue: 404 Not Found
**Solution:**
1. Verify the API endpoint exists and has data
2. Replace wildcard paths (*/example) with actual IDs
3. Check that the backend service is running

## Cache Policy Mapping

| Cache Behavior | Path Pattern | Cache Policy | TTL (Staging) |
|----------------|-------------|--------------|---------------|
| 1-hour | `/api/v1/stargate/nft-holders/historic/1-hour` | hourly | 5 minutes |
| 1-day | `/api/v1/stargate/nft-holders/historic/1-day` | day | 5 minutes |
| 1-week | `/api/v1/stargate/nft-holders/historic/1-week` | weekly | 5 minutes |
| 1-month | `/api/v1/stargate/nft-holders/historic/1-month` | monthly | 5 minutes |
| B3TR Global | `/api/v1/b3tr/actions/global/overview` | day | 5 minutes |
| B3TR Apps | `/api/v1/b3tr/actions/apps/*/overview` | hourly | 5 minutes |
| B3TR Galaxy | `/api/v1/b3tr/galaxy-members/level-overview` | hourly | 5 minutes |
| VTHO Claimed | `/api/v1/stargate/total-vtho-claimed/historic/*` | hourly | 5 minutes |
| VET Staked | `/api/v1/stargate/total-vet-staked/historic/*` | hourly | 5 minutes |
| Proposals | `/api/v1/b3tr/proposals/*/results` | hourly | 5 minutes |

## Monitoring in AWS Console

1. Go to CloudFront → Distributions
2. Find your staging distribution
3. Check the "Monitoring" tab for:
   - Cache hit rate
   - Request count
   - Error rate
4. Check "Behaviors" tab to verify cache policy associations

## Next Steps After Testing

1. **Verify Cache Hit Rates** - Should increase after initial requests
2. **Test with Production Header** - Once confident, remove the staging header
3. **Monitor Performance** - Check response times and cache hit rates in CloudFront metrics
4. **Update TTLs** - Adjust cache policy TTLs in shared.yml if needed

---

For questions or issues, check the CloudFront logs or contact the DevOps team.

