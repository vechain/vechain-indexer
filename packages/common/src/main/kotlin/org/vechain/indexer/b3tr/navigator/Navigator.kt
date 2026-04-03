package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.NAVIGATOR.COLLECTION)
data class Navigator
@ConstructorBinding
constructor(
    @Id val address: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val status: NavigatorStatus,
    val stake: String,
    val citizenCount: Int,
    val totalDelegated: String,
    val metadataURI: String?,
    val registeredAt: Long,
    val exitAnnouncedRound: String?,
    val exitEffectiveRound: String?,
    val lastReportRound: String?,
    val lastReportURI: String?,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}

enum class NavigatorStatus {
    ACTIVE,
    EXITING,
    DEACTIVATED,
}
