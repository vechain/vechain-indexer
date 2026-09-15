package org.vechain.indexer.contracts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Contract(
    val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val createdOn: Long,
    val deploymentTxId: String,
    val deploymentClauseIndex: Long,
    val master: String,
    val isErc20: Boolean? = null,
    val isErc721: Boolean? = null,
    val isErc1155: Boolean? = null,
) : IndexedDocument
