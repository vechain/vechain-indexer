package org.vechain.indexer.model

import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "archives")
data class Archive(
    val id: String,
    val data: Any,
)
