package org.vechain.indexer.stargate

import java.math.BigInteger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.maxByOrNull
import kotlin.collections.plus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("stargate", "vtho-claimed-by-account")
@Service
open class VthoClaimedByAccountService(
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository,
    private val vthoClaimedByAccountArchiveService:
        ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
    private val vthoClaimByAccountPruner: Pruner,
) {
    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<VthoClaimedByAccount>, existing: List<VthoClaimedByAccount>) {
        if (updated.isNotEmpty()) {
            vthoClaimedByAccountRepository.saveAll(updated)
        }

        if (existing.isNotEmpty()) {
            vthoClaimedByAccountArchiveService.saveAll(existing)

            // Prune old archives
            val idsToPrune = existing.filter { it.version > 1 }.map { it.account }
            if (idsToPrune.isNotEmpty()) {
                val latestBlock =
                    (updated + existing).maxByOrNull { it.blockNumber }?.blockNumber ?: 0L

                vthoClaimByAccountPruner.run(latestBlock, idsToPrune)
            }
        }
    }

    open fun parseRecords(
        events: List<IndexedEvent>,
        existing: List<VthoClaimedByAccount>,
    ): List<VthoClaimedByAccount> {
        if (events.isEmpty()) return emptyList()

        val existingByAccount = existing.associateBy { it.account }
        val groupedEvents =
            events.groupBy {
                it.params.getAsString("owner")
                    ?: throw IllegalArgumentException("Missing 'owner' parameter in event")
            }
        val latestEvent = events.maxByOrNull { it.blockNumber } ?: return emptyList()

        return groupedEvents.map { (account, accountEvents) ->
            val prev = existingByAccount[account]
            val version = (prev?.version ?: 0) + 1
            val totalVthoClaimed =
                accountEvents.sumOf { it.params.getAsBigInteger("value") ?: BigInteger.ZERO }
            val value = prev?.total?.add(totalVthoClaimed) ?: totalVthoClaimed
            VthoClaimedByAccount(
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                total = value,
                account = account,
                version = version,
            )
        }
    }

    open fun getExisting(events: List<IndexedEvent>): List<VthoClaimedByAccount> {
        if (events.isEmpty()) {
            return emptyList()
        }

        // Extract account addresses from events
        val accounts =
            events
                .map {
                    it.params.getAsString("owner")
                        ?: throw IllegalArgumentException("Missing 'owner' parameter in event")
                }
                .distinct()

        // Fetch existing records from the repository
        return vthoClaimedByAccountRepository.findAllById(accounts).toList()
    }
}
