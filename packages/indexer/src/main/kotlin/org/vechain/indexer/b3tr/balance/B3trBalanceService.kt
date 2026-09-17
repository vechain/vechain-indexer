package org.vechain.indexer.b3tr.balance

import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "b3tr-balance")
@Service
open class B3trBalanceService(
    private val repository: B3trBalanceWriteRepository,
    @Value("\${business-event.substitutions.B3TR_CONTRACT}")
    private val b3trContractAddress: String,
    @Value("\${business-event.substitutions.VOT3_CONTRACT}")
    private val vot3ContractAddress: String,
) {

    /** Entry point when full block is available (IndexingResult.BlockResult). */
    open fun processBlock(block: Block, events: List<IndexedEvent>): List<B3trBalance> =
        processBlock(BlockDetails(block.id, block.number, block.timestamp), events)

    /** Entry point for fast sync (events only) or when block is available as BlockDetails. */
    open fun processBlock(
        blockDetails: BlockDetails,
        events: List<IndexedEvent>,
    ): List<B3trBalance> {
        val transfers = filterB3trAndVot3Transfers(events)
        if (transfers.isEmpty()) return emptyList()

        val addresses = mutableSetOf<String>()
        transfers.forEach { event ->
            event.params.getAsString("from")?.let { addresses.add(it) }
            event.params.getAsString("to")?.let { addresses.add(it) }
        }
        val stored =
            repository.findCurrentByAddresses(addresses, blockDetails.blockNumber).associateBy {
                it.address
            }
        val touched = linkedMapOf<String, B3trBalance>()

        transfers.forEach { event ->
            val from =
                event.params.getAsString("from")
                    ?: error("Invalid Transfer event: missing 'from' param (${event.id})")
            val to =
                event.params.getAsString("to")
                    ?: error("Invalid Transfer event: missing 'to' param (${event.id})")
            val value = (event.params.getAsBigInteger("value") ?: BigInteger.ZERO).toBigDecimal()
            if (value == BigDecimal.ZERO) return@forEach
            if (from == to) return@forEach

            val isVot3 = event.address.equals(vot3ContractAddress, ignoreCase = true)

            listOf(from to value.negate(), to to value).forEach { (address, delta) ->
                if (address.equals(Address.ZERO_ADDRESS, ignoreCase = true)) return@forEach
                if (!isVot3 && address.equals(vot3ContractAddress, ignoreCase = true))
                    return@forEach
                val updated = forMutation(address, blockDetails, stored, touched)
                if (isVot3) {
                    updated.vot3Balance += delta
                } else {
                    updated.b3trBalance += delta
                }
                updated.totalBalance = updated.vot3Balance + updated.b3trBalance
            }
        }

        return touched.values.toList()
    }

    protected fun filterB3trAndVot3Transfers(events: List<IndexedEvent>): List<IndexedEvent> =
        events.filter {
            it.eventType == "Transfer" &&
                (it.address.equals(b3trContractAddress, ignoreCase = true) ||
                    it.address.equals(vot3ContractAddress, ignoreCase = true))
        }

    private fun forMutation(
        address: String,
        block: BlockDetails,
        stored: Map<String, B3trBalance>,
        touched: MutableMap<String, B3trBalance>,
    ): B3trBalance =
        touched.getOrPut(address) {
            stored[address]?.copy(
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
            )
                ?: B3trBalance(
                    address = address,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    vot3Balance = BigDecimal.ZERO,
                    b3trBalance = BigDecimal.ZERO,
                    totalBalance = BigDecimal.ZERO,
                )
        }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(balances: List<B3trBalance>) = repository.save(balances)
}
