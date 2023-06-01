package org.vechain.indexer.exception

class BlockNotFoundException(message: String, val blockNumber: Long) :
    Exception(message)