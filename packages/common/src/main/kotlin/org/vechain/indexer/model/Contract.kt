package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "contracts")
@CompoundIndex(name = "contract_block_number_idx", def = "{'blockNumber': -1}")
@CompoundIndex(
    name = "contract_creator_1_blockNumber_-1_txId_-1__id_-1",
    def = "{'creator': 1, 'blockNumber': -1, 'txId': -1, '_id': -1}"
)
data class Contract @ConstructorBinding constructor(
    @Id
    val address: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val creator: String,
    var master: String,
    val rawData: String,
    val isVip180: Boolean,
    val isVip181: Boolean,
    val isVip210: Boolean,
    val isErc20: Boolean,
    val isErc721: Boolean,
    val isErc1155: Boolean,
    val previousMasters: MutableSet<String>
) : IndexedDocument