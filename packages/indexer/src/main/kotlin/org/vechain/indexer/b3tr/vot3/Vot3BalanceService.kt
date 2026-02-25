package org.vechain.indexer.b3tr.vot3

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.vot3.repository.Vot3BalanceRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "vot3-balance")
@Service
open class Vot3BalanceService(
    private val repository: Vot3BalanceRepository,
    private val archiveService: ArchiveService<Vot3Balance, Vot3BalanceArchive>,
    private val pruner: TargetedPruner<Vot3Balance, Vot3BalanceArchive>,
    @Value("\${business-event.substitutions.VOT3_CONTRACT}") private val vot3ContractAddress: String,
) {

    open fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<List<Vot3Balance>, List<Vot3Balance>> {
        val vot3Transfers = filterVot3Transfers(events)
        if (vot3Transfers.isEmpty()) return emptyList<Vot3Balance>() to emptyList()

        val accumulator =
            VersionedDocumentAccumulator<Vot3Balance>(
                repository::findByIdOrNull,
                initialVersion = 1,
            )
        accumulator.startBlock()
        val resolved = mutableMapOf<String, Vot3Balance>()

        vot3Transfers.forEach { event ->
            val from =
                event.params.getAsString("from")
                    ?: error("Invalid VOT3 Transfer event: missing 'from' param (${event.id})")
            val to =
                event.params.getAsString("to")
                    ?: error("Invalid VOT3 Transfer event: missing 'to' param (${event.id})")
            val value = event.params.getAsBigInteger("value") ?: BigInteger.ZERO
            if (value == BigInteger.ZERO) return@forEach
            if (from == to) return@forEach

            listOf(from to value.negate(), to to value).forEach { (address, delta) ->
                val updated = resolveForMutation(address, block, accumulator, resolved)
                updated.balance += delta
            }
        }

        return accumulator.results()
    }

    protected fun filterVot3Transfers(events: List<IndexedEvent>): List<IndexedEvent> =
        events.filter {
            it.eventType == "Transfer" && it.address.equals(vot3ContractAddress, ignoreCase = true)
        }

    protected fun resolveForMutation(
        recordId: String,
        block: Block,
        accumulator: VersionedDocumentAccumulator<Vot3Balance>,
        resolved: MutableMap<String, Vot3Balance>,
    ): Vot3Balance {
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
                        balance = existing.balance,
                    )
                accumulator.put(recordId, existing, copy)
                copy
            } else {
                val newRecord =
                    Vot3Balance(
                        address = recordId,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = nextVersion,
                        balance = BigInteger.ZERO,
                    )
                accumulator.put(recordId, null, newRecord)
                newRecord
            }

        resolved[recordId] = result
        return result
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<Vot3Balance>, existing: List<Vot3Balance>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            archiveService = archiveService,
            pruner = pruner,
        )
    }
}
