package org.vechain.indexer.accounts

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("accounts", "vet-balance")
@Service
open class VetBalanceService(private val repository: VetBalanceRepository) {
    open fun processEvents(events: List<IndexedEvent>): List<VetBalance> {
        val transfers = events.filter { it.eventType == "VET_TRANSFER" }
        if (transfers.isEmpty()) return emptyList()

        val results = mutableListOf<VetBalance>()

        groupByBlock(transfers).forEach { (blockDetails, blockEvents) ->
            val deltasByAddress = mutableMapOf<String, BigInteger>()

            fun addDelta(address: String, delta: BigInteger) {
                if (delta == BigInteger.ZERO) return
                deltasByAddress[address] = (deltasByAddress[address] ?: BigInteger.ZERO) + delta
            }

            blockEvents.forEach { event ->
                val from =
                    event.params.getAsString("from")
                        ?: error("Invalid VET_TRANSFER event: missing 'from' param (${event.id})")
                val to =
                    event.params.getAsString("to")
                        ?: error("Invalid VET_TRANSFER event: missing 'to' param (${event.id})")
                val value = event.params.getAsBigInteger("amount") ?: BigInteger.ZERO

                if (from != to) {
                    addDelta(from, value.negate())
                    addDelta(to, value)
                }
            }

            deltasByAddress
                .filterValues { it != BigInteger.ZERO }
                .forEach { (address, delta) ->
                    val previous = repository.findFirstByAddressOrderByBlockTimestampDesc(address)
                    val previousBalance = previous?.balance ?: BigInteger.ZERO
                    val newBalance = previousBalance + delta

                    results.add(
                        VetBalance(
                            address = address,
                            blockId = blockDetails.blockId,
                            blockNumber = blockDetails.blockNumber,
                            blockTimestamp = blockDetails.blockTimestamp,
                            balance = newBalance,
                        )
                    )
                }
        }

        return results
    }

    @Transactional
    open fun save(records: List<VetBalance>) {
        if (records.isEmpty()) return
        repository.saveAll(records)
    }
}
