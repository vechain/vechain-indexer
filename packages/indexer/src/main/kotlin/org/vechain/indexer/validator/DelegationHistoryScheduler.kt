package org.vechain.indexer.validator

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.history.HistoryRepository
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.IdUtils

@Profile("history-delegations", "validator")
@EnableScheduling
@Component
open class DelegationHistoryScheduler(
    private val delegationRepository: DelegationRepository,
    private val historyRepository: HistoryRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(
        initialDelayString = "\${scheduler.delegationHistory.initial-delay}",
        fixedRateString = "\${scheduler.delegationHistory.interval}",
    )
    open fun run() {
        logger.info("Checking delegations that need to be added to history")

        // Get each account that needs to be notified
        val delegations = delegationRepository.findByNotify(true)

        if (delegations.isEmpty()) return

        val historyEvents = mutableListOf<IndexedHistoryEvent>()

        val updatedDelegations =
            delegations.mapNotNull { delegation ->
                val eventName =
                    EventUtils.determineDelegationEventType(delegation.status, delegation.force)
                        ?: return@mapNotNull null

                // Build history event
                val historyEvent =
                    IndexedHistoryEvent(
                        id =
                            IdUtils.generateId(
                                delegation.id,
                                eventName.name,
                                delegation.blockNumber.toString()
                            ),
                        blockNumber = delegation.blockNumber,
                        blockTimestamp = delegation.blockTimestamp,
                        blockId = delegation.blockId,
                        txId = delegation.txId,
                        eventName = eventName,
                        tokenId = delegation.tokenId,
                        delegationId = delegation.id,
                        validator = delegation.validator,
                        owner = delegation.owner,
                        origin = delegation.owner,
                    )

                historyEvents.add(historyEvent)

                // Ensure user does not get notified for the same event twice
                delegation.copy(notify = false, version = delegation.version + 1)
            }

        logger.info("Saving ${historyEvents.size} delegations updates to history")
        this.save(historyEvents, updatedDelegations)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(historyEvents: List<IndexedHistoryEvent>, delegations: List<Delegation>) {
        // save hsitory events
        historyRepository.saveAll(historyEvents)

        // save updated delegations
        delegationRepository.saveAll(delegations)
    }
}
