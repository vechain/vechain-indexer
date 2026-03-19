package org.vechain.indexer.b3tr.balance

import java.math.BigDecimal
import java.math.BigInteger
import org.bson.types.Decimal128
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "b3tr-balance")
@Service
open class B3trBalanceService(
    private val repository: B3trBalanceRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    @Value("\${business-event.substitutions.B3TR_CONTRACT}")
    private val b3trContractAddress: String,
    @Value("\${business-event.substitutions.VOT3_CONTRACT}") private val vot3ContractAddress: String,
) {

    /** Entry point when full block is available (IndexingResult.BlockResult). */
    open fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<List<B3trBalance>, List<B3trBalance>> =
        processBlock(BlockDetails(block.id, block.number, block.timestamp), events)

    /** Entry point for fast sync (events only) or when block is available as BlockDetails. */
    open fun processBlock(
        blockDetails: BlockDetails,
        events: List<IndexedEvent>,
    ): Pair<List<B3trBalance>, List<B3trBalance>> {
        val transfers = filterB3trAndVot3Transfers(events)
        if (transfers.isEmpty()) return emptyList<B3trBalance>() to emptyList()

        // Pre-collect all addresses and batch-load from DB
        val allAddresses = mutableSetOf<String>()
        transfers.forEach { event ->
            event.params.getAsString("from")?.let { allAddresses.add(it) }
            event.params.getAsString("to")?.let { allAddresses.add(it) }
        }
        val preloaded =
            if (allAddresses.isNotEmpty()) {
                repository.findAllById(allAddresses).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<B3trBalance>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) },
                initialVersion = 1,
            )
        accumulator.startBlock()
        val resolved = mutableMapOf<String, B3trBalance>()

        transfers.forEach { event ->
            val from =
                event.params.getAsString("from")
                    ?: error("Invalid Transfer event: missing 'from' param (${event.id})")
            val to =
                event.params.getAsString("to")
                    ?: error("Invalid Transfer event: missing 'to' param (${event.id})")
            val value = event.params.getAsBigInteger("value") ?: BigInteger.ZERO
            if (value == BigInteger.ZERO) return@forEach
            if (from == to) return@forEach

            val isVot3 = event.address.equals(vot3ContractAddress, ignoreCase = true)

            val deltaDec = BigDecimal(value)
            listOf(from to deltaDec.negate(), to to deltaDec).forEach { (address, delta) ->
                if (address.equals(Address.ZERO_ADDRESS, ignoreCase = true)) return@forEach
                if (!isVot3 && address.equals(vot3ContractAddress, ignoreCase = true))
                    return@forEach
                val updated = resolveForMutation(address, blockDetails, accumulator, resolved)
                if (isVot3) {
                    updated.vot3Balance =
                        Decimal128(updated.vot3Balance.bigDecimalValue().add(delta))
                } else {
                    updated.b3trBalance =
                        Decimal128(updated.b3trBalance.bigDecimalValue().add(delta))
                }
                updated.totalBalance =
                    Decimal128(
                        updated.vot3Balance
                            .bigDecimalValue()
                            .add(updated.b3trBalance.bigDecimalValue())
                    )
            }
        }

        return accumulator.results()
    }

    protected fun filterB3trAndVot3Transfers(events: List<IndexedEvent>): List<IndexedEvent> =
        events.filter {
            it.eventType == "Transfer" &&
                (it.address.equals(b3trContractAddress, ignoreCase = true) ||
                    it.address.equals(vot3ContractAddress, ignoreCase = true))
        }

    protected fun resolveForMutation(
        recordId: String,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<B3trBalance>,
        resolved: MutableMap<String, B3trBalance>,
    ): B3trBalance {
        resolved[recordId]?.let {
            return it
        }

        val (existing, nextVersion) = accumulator.resolve(recordId)
        val result =
            if (existing != null) {
                val copy =
                    existing.copy(
                        version = nextVersion,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        vot3Balance = existing.vot3Balance,
                        b3trBalance = existing.b3trBalance,
                        totalBalance = existing.totalBalance,
                    )
                accumulator.put(recordId, existing, copy)
                copy
            } else {
                val newRecord =
                    B3trBalance(
                        address = recordId,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        version = nextVersion,
                        vot3Balance = B3trBalance.ZERO,
                        b3trBalance = B3trBalance.ZERO,
                        totalBalance = B3trBalance.ZERO,
                    )
                accumulator.put(recordId, null, newRecord)
                newRecord
            }

        resolved[recordId] = result
        return result
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<B3trBalance>, existing: List<B3trBalance>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }
}
