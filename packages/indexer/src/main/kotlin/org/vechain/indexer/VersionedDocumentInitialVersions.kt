package org.vechain.indexer

object VersionedDocumentInitialVersions {
    private const val DEFAULT_INITIAL_VERSION = 1

    fun forCollection(collectionName: String): Int = DEFAULT_INITIAL_VERSION
}
