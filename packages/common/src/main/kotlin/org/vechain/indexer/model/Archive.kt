package org.vechain.indexer.model

import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "archives")
data class Archive<T : VersionedDocument>(
    val id: String,
    val data: T
)