package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "contracts")
data class Contract(
    @Id
    val address: String? = null,
    val blockId: String? = null,
    @Indexed
    val blockNumber: Long? = null,
    val txId: String? = null,
    @Indexed
    val creator: String? = null,
    @Indexed
    var master: String? = null,
    @Indexed(sparse = true)
    val factoryContract: String? = null,
    val rawData: String? = null,
    val isVip180: Boolean? = false,
    val isVip181: Boolean? = false,
    val isErc20: Boolean? = false,
    val isErc721: Boolean? = false,
)
