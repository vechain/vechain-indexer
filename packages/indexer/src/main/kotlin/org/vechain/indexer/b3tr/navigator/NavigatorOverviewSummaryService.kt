package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails

@Service
@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-overview-summary")
open class NavigatorOverviewSummaryService(
    private val repository: NavigatorOverviewSummaryRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    open fun findById(id: String): NavigatorOverviewSummary? = repository.findByIdOrNull(id)

    open fun checkExpiredExits(
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        repository
            .findByRecordTypeAndStatusAndExitEffectiveDeadlineBlockLessThanEqual(
                NavigatorOverviewSummaryRecordType.NAVIGATOR_STATE,
                NavigatorStatus.EXITING,
                blockDetails.blockNumber,
            )
            .forEach { navigatorState ->
                updateNavigatorState(
                    navigatorState.navigator ?: return@forEach,
                    blockDetails,
                    accumulator,
                ) { current ->
                    if (current.status != NavigatorStatus.EXITING) current
                    else {
                        current.copy(
                            status = NavigatorStatus.DEACTIVATED,
                            citizenCount = 0,
                            delegatedTotal = BigDecimal.ZERO,
                        )
                    }
                }
            }
    }

    open fun processBlockEvents(
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        events.forEach { event ->
            when (event.eventType) {
                "B3TR_NavigatorRegistered" -> handleRegistered(event, blockDetails, accumulator)
                "B3TR_StakeAdded" -> handleStakeAdded(event, blockDetails, accumulator)
                "B3TR_StakeWithdrawn" -> handleStakeWithdrawn(event, blockDetails, accumulator)
                "B3TR_ExitAnnounced" -> handleExitAnnounced(event, blockDetails, accumulator)
                "B3TR_NavigatorDeactivated" -> handleDeactivated(event, blockDetails, accumulator)
                "B3TR_NavigatorSlashed",
                "B3TR_NavigatorMinorSlashed" -> handleSlashed(event, blockDetails, accumulator)
                "B3TR_DelegationCreated" ->
                    handleDelegationCreated(event, blockDetails, accumulator)
                "B3TR_DelegationIncreased" ->
                    handleDelegationIncreased(event, blockDetails, accumulator)
                "B3TR_DelegationDecreased" ->
                    handleDelegationDecreased(event, blockDetails, accumulator)
                "B3TR_DelegationRemoved" ->
                    handleDelegationRemoved(event, blockDetails, accumulator)
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(
        updated: List<NavigatorOverviewSummary>,
        existing: List<NavigatorOverviewSummary>,
    ) {
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    private fun handleRegistered(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("navigator", "stakeAmount", "metadataURI")
        val address = event.requireAddressParam("navigator")
        val stake = event.requireBigDecimalParam("stakeAmount")
        updateNavigatorState(address, block, accumulator, createIfMissing = true) {
            it.copy(
                navigator = address,
                status = NavigatorStatus.ACTIVE,
                stake = stake,
                citizenCount = 0,
                delegatedTotal = BigDecimal.ZERO,
                exitEffectiveDeadlineBlock = null,
            )
        }
    }

    private fun handleStakeAdded(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("navigator", "amount", "newTotal")
        val address = event.requireAddressParam("navigator")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(stake = event.requireBigDecimalParam("newTotal"))
        }
    }

    private fun handleStakeWithdrawn(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("navigator", "amount", "remaining")
        val address = event.requireAddressParam("navigator")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(stake = event.requireBigDecimalParam("remaining"))
        }
    }

    private fun handleExitAnnounced(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("navigator", "announcedAtRound", "effectiveDeadline")
        val address = event.requireAddressParam("navigator")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(
                status = NavigatorStatus.EXITING,
                exitEffectiveDeadlineBlock = event.requireLongParam("effectiveDeadline"),
            )
        }
    }

    private fun handleDeactivated(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("navigator", "slashPercentage")
        val address = event.requireAddressParam("navigator")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(
                status = NavigatorStatus.DEACTIVATED,
                citizenCount = 0,
                delegatedTotal = BigDecimal.ZERO,
            )
        }
    }

    private fun handleSlashed(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        if (event.eventType == "B3TR_NavigatorMinorSlashed") {
            event.validateRequiredParams(
                "navigator",
                "amount",
                "remainingStake",
                "roundId",
                "infractionFlags",
            )
        } else {
            event.validateRequiredParams("navigator", "amount", "remainingStake", "reason")
        }
        val address = event.requireAddressParam("navigator")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(stake = event.requireBigDecimalParam("remainingStake"))
        }
    }

    private fun handleDelegationCreated(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("citizen", "navigator", "amount")
        val address = event.requireAddressParam("navigator")
        val amount = event.requireBigDecimalParam("amount")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(
                citizenCount = (current.citizenCount ?: 0) + 1,
                delegatedTotal = (current.delegatedTotal ?: BigDecimal.ZERO) + amount,
            )
        }
    }

    private fun handleDelegationIncreased(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("citizen", "navigator", "addedAmount", "newTotal")
        val address = event.requireAddressParam("navigator")
        val amount = event.requireBigDecimalParam("addedAmount")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(delegatedTotal = (current.delegatedTotal ?: BigDecimal.ZERO) + amount)
        }
    }

    private fun handleDelegationDecreased(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("citizen", "navigator", "removedAmount", "newTotal")
        val address = event.requireAddressParam("navigator")
        val amount = event.requireBigDecimalParam("removedAmount")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(
                delegatedTotal =
                    maxOf(BigDecimal.ZERO, (current.delegatedTotal ?: BigDecimal.ZERO) - amount)
            )
        }
    }

    private fun handleDelegationRemoved(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
    ) {
        event.validateRequiredParams("citizen", "navigator", "amount")
        val address = event.requireAddressParam("navigator")
        val amount = event.requireBigDecimalParam("amount")
        updateNavigatorState(address, block, accumulator) { current ->
            current.copy(
                citizenCount = maxOf(0, (current.citizenCount ?: 0) - 1),
                delegatedTotal =
                    maxOf(BigDecimal.ZERO, (current.delegatedTotal ?: BigDecimal.ZERO) - amount),
            )
        }
    }

    private fun updateNavigatorState(
        navigator: String,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
        createIfMissing: Boolean = false,
        transform: (NavigatorOverviewSummary) -> NavigatorOverviewSummary,
    ) {
        val stateId = NavigatorOverviewSummary.navigatorStateId(navigator)
        val (existing, nextVersion) = accumulator.resolve(stateId)
        val current = existing
        if (current == null && !createIfMissing) return
        val base =
            current
                ?: NavigatorOverviewSummary(
                    id = stateId,
                    version = 0,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    recordType = NavigatorOverviewSummaryRecordType.NAVIGATOR_STATE,
                    navigator = navigator,
                    status = NavigatorStatus.DEACTIVATED,
                    stake = BigDecimal.ZERO,
                    citizenCount = 0,
                    delegatedTotal = BigDecimal.ZERO,
                    exitEffectiveDeadlineBlock = null,
                )
        val previousContribution = base.toContribution()
        val updated =
            transform(base)
                .copy(
                    version = nextVersion,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    recordType = NavigatorOverviewSummaryRecordType.NAVIGATOR_STATE,
                    navigator = navigator,
                )
        accumulator.put(stateId, current, updated)
        updateGlobalSummary(block, accumulator, previousContribution, updated.toContribution())
    }

    private fun updateGlobalSummary(
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorOverviewSummary>,
        previous: NavigatorContribution,
        next: NavigatorContribution,
    ) {
        val delta = next - previous
        if (delta.isZero()) return
        val (existing, nextVersion) = accumulator.resolve(NavigatorOverviewSummary.GLOBAL_ID)
        val current = existing ?: emptyGlobalSummary()
        val updated =
            current.copy(
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                recordType = NavigatorOverviewSummaryRecordType.GLOBAL_SUMMARY,
                activeNavigators = (current.activeNavigators ?: 0L) + delta.activeNavigators,
                totalStaked = (current.totalStaked ?: BigDecimal.ZERO) + delta.totalStaked,
                totalCitizens = (current.totalCitizens ?: 0L) + delta.totalCitizens,
                totalDelegated = (current.totalDelegated ?: BigDecimal.ZERO) + delta.totalDelegated,
            )
        accumulator.put(NavigatorOverviewSummary.GLOBAL_ID, existing, updated)
    }

    private fun emptyGlobalSummary(): NavigatorOverviewSummary =
        NavigatorOverviewSummary(
            id = NavigatorOverviewSummary.GLOBAL_ID,
            version = 0,
            blockId = "",
            blockNumber = 0L,
            blockTimestamp = 0L,
            recordType = NavigatorOverviewSummaryRecordType.GLOBAL_SUMMARY,
            activeNavigators = 0L,
            totalStaked = BigDecimal.ZERO,
            totalCitizens = 0L,
            totalDelegated = BigDecimal.ZERO,
        )

    private fun NavigatorOverviewSummary.toContribution(): NavigatorContribution =
        if (recordType != NavigatorOverviewSummaryRecordType.NAVIGATOR_STATE) {
            NavigatorContribution.ZERO
        } else if (status == NavigatorStatus.ACTIVE || status == NavigatorStatus.EXITING) {
            NavigatorContribution(
                activeNavigators = 1,
                totalStaked = stake ?: BigDecimal.ZERO,
                totalCitizens = (citizenCount ?: 0).toLong(),
                totalDelegated = delegatedTotal ?: BigDecimal.ZERO,
            )
        } else {
            NavigatorContribution.ZERO
        }
}

private data class NavigatorContribution(
    val activeNavigators: Long,
    val totalStaked: BigDecimal,
    val totalCitizens: Long,
    val totalDelegated: BigDecimal,
) {
    operator fun minus(other: NavigatorContribution): NavigatorContribution =
        NavigatorContribution(
            activeNavigators = activeNavigators - other.activeNavigators,
            totalStaked = totalStaked - other.totalStaked,
            totalCitizens = totalCitizens - other.totalCitizens,
            totalDelegated = totalDelegated - other.totalDelegated,
        )

    fun isZero(): Boolean =
        activeNavigators == 0L &&
            totalStaked.compareTo(BigDecimal.ZERO) == 0 &&
            totalCitizens == 0L &&
            totalDelegated.compareTo(BigDecimal.ZERO) == 0

    companion object {
        val ZERO =
            NavigatorContribution(
                activeNavigators = 0L,
                totalStaked = BigDecimal.ZERO,
                totalCitizens = 0L,
                totalDelegated = BigDecimal.ZERO,
            )
    }
}
