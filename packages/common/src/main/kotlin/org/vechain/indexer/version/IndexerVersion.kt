package org.vechain.indexer.version

import com.fasterxml.jackson.annotation.JsonView
import java.time.LocalDateTime
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
data class IndexerVersion(
    val indexerName: String,
    val tableName: String,
    @field:JsonView(Views.Internal::class) val version: Int,
    val updatedAt: LocalDateTime? = null,
    val lastProcessedBlock: BlockIdentifier? = null,
)
