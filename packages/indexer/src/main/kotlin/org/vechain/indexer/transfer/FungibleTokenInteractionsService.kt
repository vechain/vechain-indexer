package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS

@Service
@Profile("transfers")
open class FungibleTokenInteractionsService(
    private val repository: FungibleTokenInteractionsRepository
) {
    open fun processEvents(events: List<IndexedEvent>): List<FungibleTokenInteraction> {
        return events
            .filter { event ->
                require(event.eventType == "Transfer") {
                    "Event type must be 'Transfer', found '${event.eventType}'"
                }
                require(event.params.params.containsKey("value")) {
                    "Transfer event must have a 'value' parameter"
                }
                event.address != null &&
                    event.address != VTHO_CONTRACT_ADDRESS &&
                    event.params.params["from"] is String &&
                    event.params.params["to"] is String
            }
            .flatMap { event ->
                val from = event.params.params["from"] as String
                val to = event.params.params["to"] as String
                listOfNotNull(
                    if (from != Address.ZERO_ADDRESS) {
                        FungibleTokenInteraction(
                            contractAddress = event.address!!,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                            walletAddress = from,
                        )
                    } else null,
                    if (to != Address.ZERO_ADDRESS) {
                        FungibleTokenInteraction(
                            contractAddress = event.address!!,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                            walletAddress = to,
                        )
                    } else null,
                )
            }
            .distinctBy { it.contractAddress to it.walletAddress }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<FungibleTokenInteraction>) {
        repository.saveAll(records)
    }
}
