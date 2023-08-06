package org.vechain.indexer.exception

/** Thrown in case a chain reorg is detected. */
class ReorgException(message: String) : Exception(message)
