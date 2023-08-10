package org.vechain.indexer.exception

/** Thrown in case a chain re-organization is detected. */
class ReorgException(message: String) : Exception(message)
