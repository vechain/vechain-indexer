package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding

data class Action
@ConstructorBinding
constructor(
    val appId: String,
    val distributor: String,
    val amount: BigDecimal,
    val receiver: String,
    val proof: SustainabilityProofV2?,
)
