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
import org.vechain.indexer.utils.IdUtils
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

    private data class Rewards(val legacy: BigInteger, val delegation: BigInteger) {
        val total = legacy + delegation
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<VthoClaimedByAccount>, existing: List<VthoClaimedByAccount>) {
        saveVersionedDocuments(
            updated,
            existing,
            vthoClaimedByAccountArchiveService,
            vthoClaimByAccountPruner,
        )
    }

    /** Accumulate rewards */
    private fun accumulateRewards(
        events: List<IndexedEvent>,
        prev: VthoClaimedByAccount?,
    ): Rewards {
        // Sum reward values per category
        var legacySum = BigInteger.ZERO
        var delegationSum = BigInteger.ZERO

        for (e in events) {
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

        return Rewards(newLegacyTotal, newDelegationTotal)
    }

    open fun parseAccountRecords(
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

            val rewards = accumulateRewards(accountEvents, prev)

            VthoClaimedByAccount(
                version = version,
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                total = rewards.total,
                legacyRewards = rewards.legacy,
                delegationRewards = rewards.delegation,
                account = account,
                tokenId = null,
            )
        }
    }

    open fun parseAccountTokenIdRecords(
        events: List<IndexedEvent>,
        existing: List<VthoClaimedByAccount>,
    ): List<VthoClaimedByAccount> {
        if (events.isEmpty()) return emptyList()

        val existingByAccountTokenId = existing.associateBy { "${it.account}_${it.tokenId}" }
        val groupedEvents =
            events.groupBy {
                val owner =
                    it.params.getAsString("owner")
                        ?: throw IllegalArgumentException("Missing 'owner' in event")
                val tokenId =
                    it.params.getAsString("tokenId")
                        ?: throw IllegalArgumentException("Missing 'tokenId' parameter in event")
                "${owner}_${tokenId}"
            }

        val latestEvent = events.maxByOrNull { it.blockNumber } ?: return emptyList()

        return groupedEvents.map { (accountTokenId, accountEvents) ->
            val prev = existingByAccountTokenId[accountTokenId]
            val version = (prev?.version ?: 0) + 1

            val rewards = accumulateRewards(accountEvents, prev)

            val splitText = accountTokenId.split('_')
            val account = splitText[0]
            val tokenId = splitText[1]

            VthoClaimedByAccount(
                version = version,
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                total = rewards.total,
                legacyRewards = rewards.legacy,
                delegationRewards = rewards.delegation,
                account = account,
                tokenId = tokenId,
            )
        }
    }

    open fun getExistingByAccount(events: List<IndexedEvent>): List<VthoClaimedByAccount> {
        if (events.isEmpty()) {
            return emptyList()
        }

        // Extract account addresses from events
        val accounts =
            events
                .map {
                    it.params.getAsString("owner")?.let { account -> IdUtils.generateId(account) }
                        ?: throw IllegalArgumentException("Missing 'owner' parameter in event")
                }
                .distinct()

        // Fetch existing records from the repository
        return vthoClaimedByAccountRepository.findAllById(accounts).toList()
    }

    open fun getExistingByAccountTokenId(events: List<IndexedEvent>): List<VthoClaimedByAccount> {
        if (events.isEmpty()) {
            return emptyList()
        }

        // Extract account addresses from events
        val accountTokenIds =
            events
                .map {
                    val owner =
                        it.params.getAsString("owner")
                            ?: throw IllegalArgumentException("Missing 'owner' parameter in event")
                    val tokenId =
                        it.params.getAsString("tokenId")
                            ?: throw IllegalArgumentException(
                                "Missing 'tokenId' parameter in event"
                            )
                    IdUtils.generateId(owner, tokenId)
                }
                .distinct()

        // Fetch existing records from the repository
        return vthoClaimedByAccountRepository.findAllById(accountTokenIds).toList()
    }
}
