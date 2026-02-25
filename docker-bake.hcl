variable "CACHE_SCOPE_PREFIX" {
  default = ""
}

target "vechain-indexer" {
  cache-from = CACHE_SCOPE_PREFIX != "" ? ["type=gha,scope=${CACHE_SCOPE_PREFIX}indexer"] : []
  cache-to   = CACHE_SCOPE_PREFIX != "" ? ["type=gha,mode=max,scope=${CACHE_SCOPE_PREFIX}indexer"] : []
}

target "vechain-indexer-api" {
  cache-from = CACHE_SCOPE_PREFIX != "" ? ["type=gha,scope=${CACHE_SCOPE_PREFIX}api"] : []
  cache-to   = CACHE_SCOPE_PREFIX != "" ? ["type=gha,mode=max,scope=${CACHE_SCOPE_PREFIX}api"] : []
}
