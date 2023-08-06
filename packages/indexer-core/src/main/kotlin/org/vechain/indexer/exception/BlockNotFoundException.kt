package org.vechain.indexer.exception

/**
 * Thrown in case a block number does not exist or hasn't been mined yet. Likely signifies a fully
 * synced indexer.
 */
class BlockNotFoundException(message: String) : Exception(message)
