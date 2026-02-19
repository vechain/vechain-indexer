package org.vechain.indexer.b3tr.treasury

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.TREASURY_TRANSFER.COLLECTION)
data class TreasuryTransfer
@ConstructorBinding
constructor(
    @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val from: String,
    val to: String,
    val value: String,
    val category: TreasuryTransferCategory,
    val label: String,
    val counterpartyName: String?,
) : IndexedDocument
