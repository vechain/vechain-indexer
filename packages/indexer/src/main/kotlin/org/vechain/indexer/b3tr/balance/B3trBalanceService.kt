package org.vechain.indexer.b3tr.balance

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "b3tr-balance")
@Service
open class B3trBalanceService(
    private val repository: B3trBalanceRepository,
    private val archiveService: ArchiveService<B3trBalance, B3trBalanceArchive>,
    private val pruner: TargetedPruner<B3trBalance, B3trBalanceArchive>,
    @Value("\${business-event.substitutions.B3TR_CONTRACT}")
    private val b3trContractAddress: String,
    @Value("\${business-event.substitutions.VOT3_CONTRACT}") private val vot3ContractAddress: String,
) {

    open fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<List<B3trBalance>, List<B3trBalance>> {
        val transfers = filterB3trAndVot3Transfers(events)
        if (transfers.isEmpty()) return emptyList<B3trBalance>() to emptyList()

        val accumulator =
            VersionedDocumentAccumulator<B3trBalance>(
                repository::findByIdOrNull,
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

            listOf(from to value.negate(), to to value).forEach { (address, delta) ->
                if (address.equals(Address.ZERO_ADDRESS, ignoreCase = true)) return@forEach
                if (!isVot3 && address.equals(vot3ContractAddress, ignoreCase = true))
                    return@forEach
                val updated = resolveForMutation(address, block, accumulator, resolved)
                if (isVot3) {
                    updated.vot3Balance += delta
                } else {
                    updated.b3trBalance += delta
                }
                updated.totalBalance = updated.vot3Balance + updated.b3trBalance
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
        block: Block,
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
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
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
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = nextVersion,
                        vot3Balance = BigInteger.ZERO,
                        b3trBalance = BigInteger.ZERO,
                        totalBalance = BigInteger.ZERO,
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
            archiveService = archiveService,
            pruner = pruner,
        )
    }
}
