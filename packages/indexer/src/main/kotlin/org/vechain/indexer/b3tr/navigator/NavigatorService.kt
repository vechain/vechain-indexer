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
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorService(
    private val repository: NavigatorRepository,
    private val citizenRepository: NavigatorCitizenRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    open fun findByAddress(address: String): Navigator? = repository.findByIdOrNull(address)

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
                "B3TR_NavigatorSlashed" -> handleSlashed(ev, blockDetails, accumulator)
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
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val (existing, nextVersion) = accumulator.resolve(address)
        val created =
            Navigator(
                address = address,
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                status = NavigatorStatus.ACTIVE,
                stake = ev.params.getAsString("stakeAmount").toBigDecimalOrZero(),
                citizenCount = 0,
                totalDelegated = BigDecimal.ZERO,
                metadataURI = ev.params.getAsString("metadataURI"),
                registeredAt = block.blockTimestamp,
                exitAnnouncedRound = null,
                exitEffectiveDeadline = null,
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
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val newStake =
            ev.params.getAsString("newTotal")?.toBigDecimalOrNull()
                ?: (nav.stake + ev.params.getAsString("amount").toBigDecimalOrZero())
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
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
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                stake = ev.params.getAsString("remaining")?.toBigDecimalOrNull() ?: nav.stake,
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleExitAnnounced(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                status = NavigatorStatus.EXITING,
                exitAnnouncedRound = ev.params.getAsString("announcedAtRound"),
                exitEffectiveDeadline = ev.params.getAsString("effectiveDeadline"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleDeactivated(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                status = NavigatorStatus.DEACTIVATED,
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleSlashed(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                stake = ev.params.getAsString("remainingStake")?.toBigDecimalOrNull() ?: nav.stake,
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleMetadataUpdated(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                metadataURI = ev.params.getAsString("newURI"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleReportSubmitted(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                lastReportRound = ev.params.getAsString("roundId"),
                lastReportURI = ev.params.getAsString("reportURI"),
            )
        accumulator.put(address, nav, updated)
    }

    private fun handleDelegationCreated(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ) {
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val amount = ev.params.getAsString("amount").toBigDecimalOrZero()
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
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
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val addedAmount = ev.params.getAsString("addedAmount").toBigDecimalOrZero()
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
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
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val removedAmount = ev.params.getAsString("removedAmount").toBigDecimalOrZero()
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
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
        val address = ev.params.getAsString("navigator")?.lowercase() ?: return
        val citizen = ev.params.getAsString("citizen")?.lowercase() ?: return
        val nav = resolveExisting(address, accumulator) ?: return
        val citizenAmount = getCitizenAmount(citizen)
        val updated =
            nav.copy(
                version = accumulator.resolve(address).nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                citizenCount = maxOf(0, nav.citizenCount - 1),
                totalDelegated = maxOf(BigDecimal.ZERO, nav.totalDelegated - citizenAmount),
            )
        accumulator.put(address, nav, updated)
    }

    private fun resolveExisting(
        address: String,
        accumulator: VersionedDocumentAccumulator<Navigator>,
    ): Navigator? {
        val (existing, _) = accumulator.resolve(address)
        return existing
    }

    private fun getCitizenAmount(citizenAddress: String): BigDecimal =
        citizenRepository.findById(citizenAddress).orElse(null)?.amount ?: BigDecimal.ZERO

    private fun String?.toBigDecimalOrZero(): BigDecimal =
        this?.toBigDecimalOrNull() ?: BigDecimal.ZERO
}
