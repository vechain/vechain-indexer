package org.vechain.indexer.version

import com.fasterxml.jackson.annotation.JsonView
import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.thor.model.Views

@Document("indexer_versions")
@JsonView(Views.Public::class)
data class IndexerVersion(
    @Id val indexerName: String,
    val collectionName: String,
    @field:JsonView(Views.Internal::class) val version: Int,
    @LastModifiedDate val updatedAt: LocalDateTime? = null,
    val lastProcessedBlock: BlockIdentifier? = null,
)
