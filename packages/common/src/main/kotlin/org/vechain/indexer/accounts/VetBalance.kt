package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Views

/** An address's VET balance after a block that moved it. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VetBalance(
    @JsonIgnore val address: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val balance: BigInteger,
) : IndexedDocument
