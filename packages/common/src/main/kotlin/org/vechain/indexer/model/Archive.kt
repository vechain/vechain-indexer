package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.model.rest.Views

@Document(collection = "archives")
@JsonView(Views.Internal::class)
data class Archive<T : VersionedDocument>(val id: String, val data: T)
