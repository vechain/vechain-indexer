package org.vechain.indexer.exception

class BlockNotFoundException(message: String? = null, val blockNumber: Long? = null, val blockId: String? = null) :
    Exception(message)