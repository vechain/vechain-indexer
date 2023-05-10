package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "contracts")
@CompoundIndex(name = "contract_block_number_idx", def = "{'blockNumber': -1}")
data class Contract @ConstructorBinding constructor(
    @Id
    val address: String,
    override val blockId: String,
    override val blockNumber: Long,
    val txId: String,
    @Indexed
    val creator: String,
    @Indexed
    var master: String,
    val rawData: String,
    val isVip180: Boolean,
    val isVip181: Boolean,
    val isErc20: Boolean,
    val isErc721: Boolean,
    val previousMasters: MutableSet<String>
) : IndexedDocument