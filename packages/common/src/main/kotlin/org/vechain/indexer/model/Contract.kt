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
    val clauseIndex: Int? = null,
    @Indexed
    val creator: String? = null,
    @Indexed(sparse = true)
    val factoryContract: String? = null,
    val rawData: String? = null,
    val isErc20: Boolean? = null,
    val isVip180: Boolean? = null,
)
