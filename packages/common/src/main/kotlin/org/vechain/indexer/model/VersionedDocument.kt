package org.vechain.indexer.model

interface VersionedDocument : IndexedDocument {
    // The version of this document. Starts from 1 and increments on every update.
    val version: Int

    // The ID of the document in the database
    fun getDocumentId(): String
}
