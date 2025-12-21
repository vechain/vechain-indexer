package org.vechain.indexer.accounts

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("accounts", "vet-balance")
@Service
open class VetBalanceService(private val repository: VetBalanceRepository) {
    open fun processEvents(events: List<IndexedEvent>): List<VetBalance> {
        val transfers = filterVetTransfers(events)
        if (transfers.isEmpty()) return emptyList()

        val results = mutableListOf<VetBalance>()
        val latestBalanceByAddress = mutableMapOf<String, BigInteger>()

        groupByBlock(transfers).forEach { (blockDetails, blockEvents) ->
            val deltasByAddress = computeDeltasByAddress(blockEvents)
            applyDeltas(blockDetails, deltasByAddress, latestBalanceByAddress, results)
        }

        return results
    }

    protected fun filterVetTransfers(events: List<IndexedEvent>): List<IndexedEvent> =
        events.filter { it.eventType == "VET_TRANSFER" }

    protected fun computeDeltasByAddress(blockEvents: List<IndexedEvent>): Map<String, BigInteger> {
        val deltasByAddress = mutableMapOf<String, BigInteger>()

        fun addDelta(address: String, delta: BigInteger) {
            if (delta == BigInteger.ZERO) return
            deltasByAddress[address] = (deltasByAddress[address] ?: BigInteger.ZERO) + delta
        }

        blockEvents.forEach { event ->
            val (from, to, amount) = parseVetTransfer(event)
            if (from == to) return@forEach

            addDelta(from, amount.negate())
            addDelta(to, amount)
        }

        return deltasByAddress.filterValues { it != BigInteger.ZERO }
    }

    protected fun parseVetTransfer(event: IndexedEvent): Triple<String, String, BigInteger> {
        val from =
            event.params.getAsString("from")
                ?: error("Invalid VET_TRANSFER event: missing 'from' param (${event.id})")
        val to =
            event.params.getAsString("to")
                ?: error("Invalid VET_TRANSFER event: missing 'to' param (${event.id})")
        val amount = event.params.getAsBigInteger("amount") ?: BigInteger.ZERO
        return Triple(from, to, amount)
    }

    protected fun applyDeltas(
        blockDetails: BlockDetails,
        deltasByAddress: Map<String, BigInteger>,
        latestBalanceByAddress: MutableMap<String, BigInteger>,
        results: MutableList<VetBalance>,
    ) {
        deltasByAddress.forEach { (address, delta) ->
            val previousBalance = getLatestBalance(address, latestBalanceByAddress)
            val newBalance = previousBalance + delta
            latestBalanceByAddress[address] = newBalance
            checkNonNegative(address, blockDetails.blockNumber, newBalance)

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

    protected fun getLatestBalance(
        address: String,
        latestBalanceByAddress: MutableMap<String, BigInteger>,
    ): BigInteger =
        latestBalanceByAddress.getOrPut(address) {
            repository.findFirstByAddressOrderByBlockTimestampDesc(address)?.balance
                ?: BigInteger.ZERO
        }

    protected fun checkNonNegative(address: String, blockNumber: Long, balance: BigInteger) {
        if (balance < BigInteger.ZERO) {
            throw IllegalStateException(
                "Negative balance detected for address $address at block $blockNumber"
            )
        }
    }

    @Transactional
    open fun save(records: List<VetBalance>) {
        if (records.isEmpty()) return
        repository.saveAll(records)
    }
}
