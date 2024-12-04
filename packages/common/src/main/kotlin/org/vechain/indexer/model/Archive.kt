package org.vechain.indexer.model

interface Archive<T : VersionedDocument> {
    val id: String
    val data: T
}
