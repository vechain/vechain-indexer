package org.vechain.indexer.exception

/**
 * Thrown by the price service when an oracle read cannot be completed: the upstream RPC is
 * unreachable, the contract returned no data, the response was malformed, or the contract address
 * is not configured. The API layer maps this to HTTP 503.
 */
class PriceFeedUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
