package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.maxByOrNull
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("stargate", "vtho-claimed-by-account")
@Service
open class VthoClaimedByAccountService(
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository,
    private val vthoClaimedByAccountArchiveService:
        ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
    private val vthoClaimByAccountPruner:
        TargetedPruner<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
) {
    private val legacyEvents =
        setOf("STARGATE_CLAIM_REWARDS_BASE_LEGACY", "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY")

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<VthoClaimedByAccount>, existing: List<VthoClaimedByAccount>) {
        saveVersionedDocuments(
            updated,
            existing,
            vthoClaimedByAccountRepository,
            vthoClaimedByAccountArchiveService,
            vthoClaimByAccountPruner,
        )
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
                    ?: throw IllegalArgumentException("Missing 'owner' in event")
            }

        val latestEvent = events.maxByOrNull { it.blockNumber } ?: return emptyList()

        return groupedEvents.map { (account, accountEvents) ->
            val prev = existingByAccount[account]
            val version = (prev?.version ?: 0) + 1

            // Sum reward values per category
            var legacySum = BigInteger.ZERO
            var delegationSum = BigInteger.ZERO

            for (e in accountEvents) {
                val value = e.params.getAsBigInteger("value") ?: BigInteger.ZERO
                if (e.eventType in legacyEvents) {
                    legacySum += value
                } else {
                    delegationSum += value
                }
            }

            // Add to previous totals
            val newLegacyTotal = prev?.legacyRewards?.add(legacySum) ?: legacySum
            val newDelegationTotal = prev?.delegationRewards?.add(delegationSum) ?: delegationSum
            val newTotal = newLegacyTotal + newDelegationTotal

            VthoClaimedByAccount(
                version = version,
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                total = newTotal,
                legacyRewards = newLegacyTotal,
                delegationRewards = newDelegationTotal,
                account = account,
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
