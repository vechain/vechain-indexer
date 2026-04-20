package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.ResolvedRecord
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails

@Service
@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-main")
open class NavigatorService(
    private val repository: NavigatorRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    open fun findByAddress(address: String): Navigator? = repository.findByIdOrNull(address)

    /**
     * Checks EXITING navigators whose exitEffectiveDeadline has passed and transitions them to
     * DEACTIVATED. Called on every processed block so exits are resolved without an on-chain event.
     */
    open fun checkExpiredExits(
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        val exitingNavigators =
            repository.findByStatusAndExitEffectiveDeadlineBlockLessThanEqual(
                NavigatorStatus.EXITING,
                blockDetails.blockNumber,
            )
        for (nav in exitingNavigators) {
            val (existing, nextVersion) = accumulator.resolve(nav.address)
            val current = existing ?: nav
            if (current.status != NavigatorStatus.EXITING) continue
            val updated =
                current.copy(
                    version = nextVersion,
                    blockId = blockDetails.blockId,
                    blockNumber = blockDetails.blockNumber,
                    blockTimestamp = blockDetails.blockTimestamp,
                    status = NavigatorStatus.DEACTIVATED,
                    citizenCount = 0,
                    totalDelegated = BigDecimal.ZERO,
                )
            accumulator.put(nav.address, current, updated)
        }
    }

    open fun processBlockEvents(
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        for (ev in events) {
            when (ev.eventType) {
                "B3TR_NavigatorRegistered" -> handleRegistered(ev, blockDetails, accumulator)
                "B3TR_StakeAdded" -> handleStakeAdded(ev, blockDetails, accumulator)
                "B3TR_StakeWithdrawn" -> handleStakeWithdrawn(ev, blockDetails, accumulator)
                "B3TR_ExitAnnounced" -> handleExitAnnounced(ev, blockDetails, accumulator)
                "B3TR_NavigatorDeactivated" -> handleDeactivated(ev, blockDetails, accumulator)
                "B3TR_NavigatorSlashed",
                "B3TR_NavigatorMinorSlashed" -> handleSlashed(ev, blockDetails, accumulator)
                "B3TR_MetadataURIUpdated" -> handleMetadataUpdated(ev, blockDetails, accumulator)
                "B3TR_ReportSubmitted" -> handleReportSubmitted(ev, blockDetails, accumulator)
                "B3TR_DelegationCreated" -> handleDelegationCreated(ev, blockDetails, accumulator)
                "B3TR_DelegationIncreased" ->
                    handleDelegationIncreased(ev, blockDetails, accumulator)
                "B3TR_DelegationDecreased" ->
                    handleDelegationDecreased(ev, blockDetails, accumulator)
                "B3TR_DelegationRemoved" -> handleDelegationRemoved(ev, blockDetails, accumulator)
                // Events that don't mutate navigator state
                "B3TR_NavigatorVoteCast",
                "B3TR_FeeDeposited",
                "B3TR_FeeClaimed",
                "B3TR_NavigatorFeeTaken" -> {}
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<Navigator>, existing: List<Navigator>) {
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    private fun handleRegistered(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("navigator", "stakeAmount", "metadataURI")
        val address = ev.requireAddressParam("navigator")
        val (existing, nextVersion) = accumulator.resolve(address)
        val created =
            Navigator(
                address = address,
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                status = NavigatorStatus.ACTIVE,
                stake = ev.requireBigDecimalParam("stakeAmount"),
                citizenCount = 0,
                totalDelegated = BigDecimal.ZERO,
                metadataURI = ev.requireParam("metadataURI"),
                registeredAt = block.blockTimestamp,
                exitAnnouncedRound = null,
                exitEffectiveDeadline = null,
                exitEffectiveDeadlineBlock = null,
                lastReportRound = null,
                lastReportURI = null,
            )
        accumulator.put(address, existing, created)
    }

    private fun handleStakeAdded(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("navigator", "amount", "newTotal")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val newStake = ev.requireBigDecimalParam("newTotal")
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                stake = newStake,
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleStakeWithdrawn(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("navigator", "amount", "remaining")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                stake = ev.requireBigDecimalParam("remaining"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleExitAnnounced(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("navigator", "announcedAtRound", "effectiveDeadline")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val effectiveDeadline = ev.requireParam("effectiveDeadline")
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                status = NavigatorStatus.EXITING,
                exitAnnouncedRound = ev.requireParam("announcedAtRound"),
                exitEffectiveDeadline = effectiveDeadline,
                exitEffectiveDeadlineBlock = ev.requireLongParam("effectiveDeadline"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleDeactivated(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("navigator", "slashPercentage")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                status = NavigatorStatus.DEACTIVATED,
                citizenCount = 0,
                totalDelegated = BigDecimal.ZERO,
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleSlashed(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        if (ev.eventType == "B3TR_NavigatorMinorSlashed") {
            ev.validateRequiredParams(
                "navigator",
                "amount",
                "remainingStake",
                "roundId",
                "infractionFlags",
            )
        } else {
            ev.validateRequiredParams("navigator", "amount", "remainingStake", "reason")
        }
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                stake = ev.requireBigDecimalParam("remainingStake"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleMetadataUpdated(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("navigator", "newURI")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                metadataURI = ev.requireParam("newURI"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleReportSubmitted(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("navigator", "roundId", "reportURI")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                lastReportRound = ev.requireParam("roundId"),
                lastReportURI = ev.requireParam("reportURI"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleDelegationCreated(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("citizen", "navigator", "amount")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val amount = ev.requireBigDecimalParam("amount")
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                citizenCount = nav.citizenCount + 1,
                totalDelegated = nav.totalDelegated + amount,
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleDelegationIncreased(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("citizen", "navigator", "addedAmount", "newTotal")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val addedAmount = ev.requireBigDecimalParam("addedAmount")
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                totalDelegated = nav.totalDelegated + addedAmount,
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleDelegationDecreased(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("citizen", "navigator", "removedAmount", "newTotal")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val removedAmount = ev.requireBigDecimalParam("removedAmount")
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                totalDelegated = maxOf(BigDecimal.ZERO, nav.totalDelegated - removedAmount),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleDelegationRemoved(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        ev.validateRequiredParams("citizen", "navigator", "amount")
        val address = ev.requireAddressParam("navigator")
        val resolved = resolveRecord(address, accumulator)
        val nav = resolved.existing ?: return
        val amount = ev.requireBigDecimalParam("amount")
        val updated =
            nav.copy(
                version = resolved.nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                citizenCount = maxOf(0, nav.citizenCount - 1),
                totalDelegated = maxOf(BigDecimal.ZERO, nav.totalDelegated - amount),
            )
        accumulator.put(address, nav, updated)
    }

    private fun resolveRecord(
        address: String,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ): ResolvedRecord<Navigator> = accumulator.resolve(address)
}
