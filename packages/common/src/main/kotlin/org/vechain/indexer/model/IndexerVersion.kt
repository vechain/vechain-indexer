package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonView
import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.Views

@Document("indexer-versions")
@JsonView(Views.Public::class)
data class IndexerVersion(
    @Id val id: String,
    @JsonView(Views.Internal::class) val version: Int,
    @LastModifiedDate val updatedAt: LocalDateTime? = null,
)
