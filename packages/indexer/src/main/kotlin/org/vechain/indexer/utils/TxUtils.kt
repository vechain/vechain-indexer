package org.vechain.indexer.utils

import org.vechain.indexer.model.ActivityType
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.thor.model.Transaction
import org.web3j.utils.Numeric

object TxUtils {

    fun getTransferEvents(tx: Transaction): List<Pair<TransferParameters, ActivityType>> {
        val transfers = mutableListOf<Pair<TransferParameters, ActivityType>>()

        for (outputs in tx.outputs) {
            for (transfer in outputs.transfers) {
                val amount = Numeric.decodeQuantity(transfer.amount)

                val params =
                    TransferParameters(
                        from = transfer.sender,
                        to = transfer.recipient,
                        tokenId = null,
                        amount = amount,
                        eventType = TransferEventType.VET
                    )
                transfers.add(Pair(params, ActivityType.VET_TRANSFER))
            }

            for (event in outputs.events) {
                if (
                    EventUtils.isNftTransferEvent(event) ||
                        EventUtils.isFungibleTransferEvent(event)
                ) {
                    val transferEvents = EventUtils.getEventParams(event)
                    for (transfer in transferEvents) {
                        val eventType =
                            if (EventUtils.isNftTransferEvent(event)) {
                                ActivityType.NON_FUNGIBLE_TRANSFER
                            } else {
                                ActivityType.FUNGIBLE_TRANSFER
                            }
                        transfers.add(Pair(transfer, eventType))
                    }
                }
            }
        }

        return transfers
    }
}
