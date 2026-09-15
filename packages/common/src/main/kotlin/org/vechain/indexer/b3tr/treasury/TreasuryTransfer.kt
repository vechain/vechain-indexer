package org.vechain.indexer.b3tr.treasury

import com.fasterxml.jackson.annotation.JsonIgnore
import org.vechain.indexer.IndexedDocument

data class TreasuryTransfer(
    @JsonIgnore val id: String,
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
