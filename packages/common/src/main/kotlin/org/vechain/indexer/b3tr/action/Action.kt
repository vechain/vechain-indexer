package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.utils.scaleDown

data class Action
@ConstructorBinding
constructor(
    val appId: String,
    val distributor: String,
    val amount: BigDecimal,
    val receiver: String,
    val proof: SustainabilityProofV2?,
) {
    companion object {
        fun from(history: IndexedHistoryEvent): Action {

            return Action(
                appId = history.appId!!,
                distributor = history.from!!,
                amount = scaleDown(BigInteger(history.value ?: "0"), 18),
                receiver = history.to!!,
                proof = history.proof,
            )
        }
    }
}
