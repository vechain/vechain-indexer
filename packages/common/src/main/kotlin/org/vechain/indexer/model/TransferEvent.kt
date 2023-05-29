package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigInteger

enum class TransferEventType {
    VET, FUNGIBLE_TOKEN, NFT, SEMI_FUNGIBLE_TOKEN
}

@Document(collection = "transfer_events")
@CompoundIndex(name = "transfer_block_number_idx", def = "{'blockNumber': -1}")
@CompoundIndex(
    name = "transfers_to_1_blockNumber_txId_id_-1_idx",
    def = "{'to': 1, 'blockNumber': -1, 'txId': -1, '_id': -1}"
)
@CompoundIndex(
    name = "transfers_from_1_blockNumber_txId_id_-1_idx",
    def = "{'from': 1, 'blockNumber': -1, 'txId': -1, '_id': -1}"
)
@CompoundIndex(
    name = "transfers_tokenAddress_1_blockNumber_txId_id_-1_idx",
    def = "{'tokenAddress': 1, 'blockNumber': -1, 'txId': -1, '_id': -1}"
)
data class TransferEvent @ConstructorBinding constructor(
    @Id
    val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val from: String,
    val to: String,
    val value: BigInteger,
    val tokenAddress: String?,
    val topics: List<String>,
    val eventType: TransferEventType
) : IndexedDocument