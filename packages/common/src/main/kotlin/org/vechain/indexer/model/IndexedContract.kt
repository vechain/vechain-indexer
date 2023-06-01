package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "contracts")
data class IndexedContract @ConstructorBinding constructor(
    @Id
    val address: String,
    override var blockId: String,
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