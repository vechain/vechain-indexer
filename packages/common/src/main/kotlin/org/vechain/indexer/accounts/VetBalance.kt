package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.model.Views
import org.vechain.indexer.utils.IdUtils.generateId

@Document(collection = IndexerNames.VET_BALANCE.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VetBalance
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore val address: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val balance: BigInteger,
) : IndexedDocument {
    constructor(
        address: String,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        balance: BigInteger,
    ) : this(
        id = generateId(address, blockNumber.toString()),
        address = address,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        balance = balance,
    )
}
