package org.vechain.indexer.b3tr.balance

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigDecimal
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class B3trBalance(
    val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    var vot3Balance: BigDecimal,
    var b3trBalance: BigDecimal,
    var totalBalance: BigDecimal,
) : IndexedDocument
