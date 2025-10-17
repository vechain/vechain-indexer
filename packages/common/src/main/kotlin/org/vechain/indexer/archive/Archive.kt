package org.vechain.indexer.archive

import org.vechain.indexer.VersionedDocument

interface Archive<T : VersionedDocument> {
    val id: String
    val data: T
}
