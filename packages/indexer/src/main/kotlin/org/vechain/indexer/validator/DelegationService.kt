package org.vechain.indexer.validator

import java.math.BigInteger
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.ValidatorUtils.listOf

/**
 * DelegationService is responsible for managing the lifecycle of delegations.
 *
 * Each block, it:
 * 1. Finds delegations that are due for a status transition.
 * 2. Finds delegations referenced in chain events.
 * 3. Finds delegations belonging to validators that exited or disappeared.
 * 4. Applies lifecycle transitions (QUEUED → ACTIVE → EXITING → EXITED).
 * 5. Applies event mutations (initiation, exit requests, withdrawals, rewards).
 * 6. Forces exit of delegations if their validator disappeared.
 *
 * Returns updated delegations and those that must be archived.
 */
@Profile("delegation")
@Service
open class DelegationService(
    private val repository: DelegationRepository,
    private val archiveService: ArchiveService<Delegation, DelegationArchive>,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}") private val stakerSC: String,
) {
    private val cachedGetDelegationAbi: MutableMap<String, AbiElement> = mutableMapOf()
    private var cachedValidators: Set<String> = emptySet()

    open fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Delegation>, List<Delegation>> {
        println("Processing delegations for block ${block.number} with ${events.size} events")

        // 1. Collect delegations from different sources
        val due = findDueDelegations(block)
        val eventDelegations = findDelegationsFromEvents(events)
        val validatorExitDelegations = findDelegationsFromExits(events, callResponses)

        // 2. Build working set (deduped by delegation ID)
        val delegations =
            (due + eventDelegations + validatorExitDelegations).associateBy { it.id }.toMutableMap()
        val delegationsToArchive = mutableListOf<Delegation>()

        // 3. Apply lifecycle transitions + event mutations
        applyScheduledTransitions(block, delegations, delegationsToArchive)
        applyEventMutations(events, delegations, delegationsToArchive, block)

        // 4. Handle validators that disappeared entirely
        val disappeared = checkMissingValidators(callResponses)
        disappeared.forEach {
            handleValidatorDisappeared(it, delegations, delegationsToArchive, block)
        }

        return delegations.values.toList() to delegationsToArchive
    }

    @Transactional
    open fun save(updates: List<Delegation>, archive: List<Delegation>) {
        if (updates.isNotEmpty()) repository.saveAll(updates)
        if (archive.isNotEmpty()) archiveService.saveAll(archive)
    }

    // ------------------------------
    // Step 1 helpers
    // ------------------------------

    private fun findDueDelegations(block: Block): List<Delegation> =
        repository.findByValidatorNextCycleAndStatusIn(
            block.number,
            listOf(Status.QUEUED, Status.EXITING),
        )

    private fun findDelegationsFromEvents(events: List<IndexedEvent>): List<Delegation> {
        val ids = events.mapNotNull { getDelegationIdFromParams(it.params) }.toSet()
        return if (ids.isNotEmpty()) repository.findAllById(ids).toList() else emptyList()
    }

    private fun findDelegationsFromExits(
        events: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): List<Delegation> {
        val exitingValidators =
            events
                .filter { it.eventType == "ValidatorExitRequested" }
                .mapNotNull { it.params.getAsString("validator") }

        val disappeared = checkMissingValidators(callResponses)

        return if (exitingValidators.isNotEmpty() || disappeared.isNotEmpty()) {
            repository.findByValidatorIn(exitingValidators + disappeared)
        } else {
            emptyList()
        }
    }

    // ------------------------------
    // Step 2 transitions
    // ------------------------------

    private fun applyScheduledTransitions(
        block: Block,
        delegations: MutableMap<String, Delegation>,
        archive: MutableList<Delegation>,
    ) {
        delegations.values
            .filter {
                (it.status == Status.QUEUED || it.status == Status.EXITING) &&
                    it.validatorNextCycle == block.number
            }
            .forEach { d ->
                archive.add(d)
                delegations[d.id] =
                    d.copy(
                        status = nextStatus(d.status),
                        notify = true,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = d.version + 1,
                    )
            }
    }

    // ------------------------------
    // Validator state helpers
    // ------------------------------

    /**
     * Compare current validator set with cached set. Returns any validators that have disappeared
     * since the last block.
     * - First call: loads cache from DB (all non-EXITED validators).
     * - Subsequent calls: compares current vs cache.
     */
    fun checkMissingValidators(callResponses: List<InspectionResult>): Set<String> {
        val decodedInfo =
            ValidatorUtils.decodeValidators(
                callResponses,
                getDelegationsAbiFunctions("getValidators"),
            )
        val currentValidators = decodedInfo.listOf<String>("masters").toSet()

        if (cachedValidators.isEmpty()) {
            cachedValidators = repository.findValidatorIdsByStatusNot(Status.EXITED).toSet()
        }

        val removed = cachedValidators.minus(currentValidators)
        cachedValidators = currentValidators
        return removed
    }

    private fun getExitBlock(validatorId: String): Long {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))
        val decoded =
            FunctionReturnDecoder.decode(
                response[0].data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )
        return (decoded["exitBlock"] as BigInteger).toLong()
    }

    // ------------------------------
    // Event mutations
    // ------------------------------

    private fun applyEventMutations(
        events: List<IndexedEvent>,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        events.forEach { ev ->
            println(
                " - Applying event ${ev.eventType} for delegation ${getDelegationIdFromParams(ev.params)}"
            )
            when (ev.eventType) {
                "DelegationInitiated" -> handleDelegationInitiated(ev, delegations, block)
                "DelegationExitRequested" ->
                    handleDelegationExitRequested(ev, delegations, delegationsToArchive, block)
                "DelegationWithdrawn" ->
                    handleDelegationWithdrawn(ev, delegations, delegationsToArchive, block)
                "DelegationRewardsClaimed" ->
                    handleDelegationRewardsClaimed(ev, delegations, delegationsToArchive, block)
                "ValidatorExitRequested" ->
                    handleValidatorExitRequested(ev, delegations, delegationsToArchive, block)
            }
        }
    }

    // ------------------------------
    // Individual event handlers
    // ------------------------------

    /** Handles a new delegation being initiated. */
    private fun handleDelegationInitiated(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val validator = ev.params.getAsString("validator")!!
        val (cycleLength, nextCycle) = getValidatorPeriodInfo(validator, block.number)

        val newDelegation =
            Delegation(
                id = delegationId,
                validator = validator,
                tokenId = ev.params.getAsString("tokenId")!!,
                tokenLevel = TokenLevel.fromOrdinal(ev.params.getAsString("levelId")!!.toInt())!!,
                status = Status.QUEUED,
                stakedAmount = ev.params.getAsString("amount")!!,
                totalRewardsClaimed = BigInteger.ZERO,
                owner = ev.origin!!,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                version = 0,
                validatorNextCycle = nextCycle,
                validatorCycleLength = cycleLength,
                txId = ev.txId,
            )

        delegations[delegationId] = newDelegation
    }

    /** Handles a delegation requesting exit. */
    private fun handleDelegationExitRequested(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        delegations[delegationId]?.let { existing ->
            if (existing.status == Status.EXITED) return@let

            delegationsToArchive.add(existing)
            delegations[delegationId] =
                existing.copy(
                    status = Status.EXITING,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    validatorNextCycle =
                        resolveNextCycleBlock(
                            existing.validatorNextCycle,
                            existing.validatorCycleLength,
                            block.number,
                        ),
                    version = existing.version + 1,
                )
        }
    }

    /** Handles a delegation being withdrawn (final exit). */
    private fun handleDelegationWithdrawn(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationID")!! // note: uppercase ID here
        delegations[delegationId]?.let { existing ->
            delegationsToArchive.add(existing)
            delegations[delegationId] =
                existing.copy(
                    status = Status.EXITED,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = existing.version + 1,
                )
        }
    }

    /** Handles a rewards claim on a delegation. */
    private fun handleDelegationRewardsClaimed(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val amount = ev.params.getAsBigInteger("amount")!!
        delegations[delegationId]?.let { existing ->
            delegationsToArchive.add(existing)
            delegations[delegationId] =
                existing.copy(
                    totalRewardsClaimed = existing.totalRewardsClaimed + amount,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    blockId = block.id,
                    version = existing.version + 1,
                )
        }
    }

    /** Handles a validator requesting exit → updates all of its delegations. */
    private fun handleValidatorExitRequested(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        val validatorId = ev.params.getAsString("validator")!!
        val exitAt = getExitBlock(validatorId)

        delegations.values
            .filter { it.validator == validatorId && it.status != Status.EXITED }
            .forEach { existing ->
                if (existing.status == Status.EXITING) return@forEach

                delegationsToArchive.add(existing)
                delegations[existing.id] =
                    existing.copy(
                        status = Status.EXITING,
                        validatorNextCycle = exitAt,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = existing.version + 1,
                    )
            }
    }

    /** Handles a validator disappearing completely from chain state. */
    private fun handleValidatorDisappeared(
        validatorId: String,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        delegations.values
            .filter { it.validator == validatorId && it.status != Status.EXITED }
            .forEach { existing ->
                delegationsToArchive.add(existing)
                delegations[existing.id] =
                    existing.copy(
                        status = Status.EXITED,
                        validatorNextCycle = block.number, // immediate exit
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = existing.version + 1,
                        notify = true,
                        force = true,
                    )
            }
    }

    // ------------------------------
    // Utility methods
    // ------------------------------

    private fun getDelegationIdFromParams(params: AbiEventParameters): String? =
        params.getAsString("delegationId") ?: params.getAsString("delegationID")

    private fun nextStatus(status: Status): Status =
        if (status == Status.EXITING) Status.EXITED else Status.ACTIVE

    private fun resolveNextCycleBlock(
        lastCycleEnd: Long?,
        cycleLength: Long,
        currentBlock: Long,
    ): Long {
        val base = lastCycleEnd ?: currentBlock
        return if (base > currentBlock) base
        else base + ((currentBlock - base) / cycleLength + 1) * cycleLength
    }

    private fun getValidatorPeriodInfo(validatorId: String, currentBlock: Long): Pair<Long, Long> {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))
        val decoded =
            FunctionReturnDecoder.decode(
                response[0].data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )
        val startBlock = (decoded["startBlock"] as BigInteger).toLong()
        val periodLength = (decoded["period"] as BigInteger).toLong()
        val offset = currentBlock - startBlock
        val positionInCycle = offset % periodLength
        val currentCycleStart = currentBlock - positionInCycle
        val nextCycleStart = currentCycleStart + periodLength
        return periodLength to nextCycleStart
    }

    private fun getDelegationsAbiFunctions(name: String): AbiElement =
        cachedGetDelegationAbi[name]
            ?: run {
                val abis = AbiLoader.loadFunctions("abis/stargate", listOf(name))
                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException("Function '$name' not found in ABI")
                cachedGetDelegationAbi[name] = abi
                abi
            }
}
