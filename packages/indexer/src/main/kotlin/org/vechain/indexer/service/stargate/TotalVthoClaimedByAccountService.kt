package org.vechain.indexer.service.stargate

import java.math.BigInteger
import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.stargate.TotalVthoClaimedByAccount
import org.vechain.indexer.model.stargate.TotalVthoClaimedByAccountArchive
import org.vechain.indexer.repository.stargate.TotalVthoClaimedByAccountRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("stargate")
@Service
open class TotalVthoClaimedByAccountService(
    private val totalVthoClaimedByAccountRepository: TotalVthoClaimedByAccountRepository,
    private val totalVthoClaimedByAccountArchiveService:
        ArchiveService<TotalVthoClaimedByAccount, TotalVthoClaimedByAccountArchive>,
) {
    @Transactional(rollbackFor = [Exception::class])
    open fun update(
        update: List<TotalVthoClaimedByAccount>,
        existing: List<TotalVthoClaimedByAccount>,
    ) {
        if (update.isNotEmpty()) {
            totalVthoClaimedByAccountRepository.saveAll(update)
        }

        if (existing.isNotEmpty()) {
            totalVthoClaimedByAccountArchiveService.saveAll(existing)
        }
    }

    open fun parseRecords(
        events: List<IndexedEvent>,
        existing: List<TotalVthoClaimedByAccount>,
    ): List<TotalVthoClaimedByAccount> {

        if (events.isEmpty()) {
            return emptyList()
        }

        // Pre-index existing records for faster lookup
        val existingByAccount = existing.associateBy { it.account }

        // Group events by account address
        val groupedEvents =
            events.groupBy {
                it.params.getAsString("owner")
                    ?: throw IllegalArgumentException("Missing 'owner' parameter in event")
            }

        // Get the event with the largest block number
        val latestEvent = events.maxBy { it.blockNumber }

        // Create a new record for each account
        return groupedEvents.map { (account, events) ->
            val existing = existingByAccount[account]
            val version = existing?.version?.plus(1) ?: 1

            val totalVthoClaimed =
                events.sumOf { it.params.getAsBigInteger("amount") ?: BigInteger.ZERO }
            val value = existing?.value?.add(totalVthoClaimed) ?: totalVthoClaimed
            TotalVthoClaimedByAccount(
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                value = value,
                account = account,
                version = version,
            )
        }
    }

    open fun getExisting(events: List<IndexedEvent>): List<TotalVthoClaimedByAccount> {
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
        return totalVthoClaimedByAccountRepository.findAllById(accounts).toList()
    }
}
