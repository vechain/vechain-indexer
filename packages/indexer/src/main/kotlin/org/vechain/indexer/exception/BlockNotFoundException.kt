package org.vechain.indexer.exception

class BlockNotFoundException(message: String? = null, val blockNumber: Long) : Exception(message)