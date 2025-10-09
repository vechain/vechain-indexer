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
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.rest.ExecuteCodeResponse
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.ValidatorUtils.hasAbiData
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
@Profile("validator", "delegation")
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
        val validatorSnapshots = decodeValidatorSnapshots(callResponses)
        val disappeared = checkMissingValidators(validatorSnapshots)

        // 1. Collect delegations from different sources
        val (unknownStart, due) = findDueDelegations(block)
        val eventDelegations = findDelegationsFromEvents(events)
        val validatorExitDelegations = findDelegationsFromExits(events, disappeared)

        // 2. Build working set (deduped by delegation ID)
        val delegations =
            (unknownStart + due + eventDelegations + validatorExitDelegations)
                .associateBy { it.id }
                .toMutableMap()
        val delegationsToArchive = mutableListOf<Delegation>()

        // 3. Check status on delegations with unknown start blocks
        checkForUpdatesOnUnknown(
            unknownStart,
            block,
            delegations,
            delegationsToArchive,
            validatorSnapshots,
        )

        // 4. Apply lifecycle transitions + event mutations
        applyScheduledTransitions(block, delegations, delegationsToArchive)
        applyEventMutations(events, delegations, delegationsToArchive, block, validatorSnapshots)

        // 5. Handle validators that disappeared entirely
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

    private fun findDueDelegations(block: Block): Pair<List<Delegation>, List<Delegation>> {
        val delegations =
            repository.findByValidatorNextCycleInAndStatusIn(
                listOf(block.number, 0L),
                listOf(Status.QUEUED, Status.EXITING),
            )

        val zeroCycle = delegations.filter { it.validatorNextCycle == 0L }
        val nonZeroCycle = delegations - zeroCycle // everything else

        return zeroCycle to nonZeroCycle
    }

    private fun findDelegationsFromEvents(events: List<IndexedEvent>): List<Delegation> {
        val ids = events.mapNotNull { event -> event.params.getAsString("tokenId") }.distinct()
        return if (ids.isNotEmpty()) repository.findByTokenIdIn(ids).toList() else emptyList()
    }

    private fun findDelegationsFromExits(
        events: List<IndexedEvent>,
        disappeared: Set<String>,
    ): List<Delegation> {
        val exitingValidators =
            events
                .filter { it.eventType == "ValidatorExitRequested" }
                .mapNotNull { it.params.getAsString("validator") }

        return if (exitingValidators.isNotEmpty() || disappeared.isNotEmpty()) {
            repository.findByValidatorIn(exitingValidators + disappeared)
        } else {
            emptyList()
        }
    }

    // ------------------------------
    // Transitions
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

    /**
     * Resolves delegations with unknown start blocks.
     *
     * Uses validator snapshots if available, otherwise queries the chain
     * (`getValidationPeriodDetails`). When a non-zero start block is found, the delegation is
     * archived and updated with the new cycle info.
     */
    private fun checkForUpdatesOnUnknown(
        unknown: List<Delegation>,
        block: Block,
        delegations: MutableMap<String, Delegation>,
        archive: MutableList<Delegation>,
        validatorsSnapshots: Map<String, ValidatorSnapshot>,
    ) {
        if (unknown.isEmpty()) return

        val validators = unknown.groupBy { it.validator }

        val snapshotEmpty = validatorsSnapshots.isEmpty()
        val responses: List<ExecuteCodeResponse> =
            (if (snapshotEmpty) {
                val clauses =
                    validators.keys.map { validatorId ->
                        ContractUtils.createClause(
                            stakerSC,
                            getDelegationsAbiFunctions("getValidationPeriodDetails"),
                            AddressUtils.toBigInt(validatorId),
                        )
                    }
                thorService.executeReadOnlyCode(clauses)
            } else {
                emptyList()
            })

        validators.keys.forEachIndexed { index, validatorId ->
            val startBlock =
                if (snapshotEmpty) {
                    determineStartBlock(responses[index])
                } else {
                    validatorsSnapshots[validatorId]!!.startBlock
                }

            if (startBlock != 0L) {
                validators[validatorId]?.forEach { existing ->
                    archive.add(existing)
                    delegations[existing.id] =
                        existing.copy(
                            validatorNextCycle = startBlock,
                            blockId = block.id,
                            blockNumber = block.number,
                            blockTimestamp = block.timestamp,
                            version = existing.version + 1,
                        )
                }
            }
        }
    }

    // ------------------------------
    // Validator state helpers
    // ------------------------------

    /** Detect validators that disappeared compared to previous state. */
    fun checkMissingValidators(validatorsSnapshots: Map<String, ValidatorSnapshot>): Set<String> {
        val currentValidators = validatorsSnapshots.keys

        if (cachedValidators.isEmpty()) {
            cachedValidators = repository.findValidatorIdsByStatusNot(Status.EXITED).toSet()
        }

        val removed = cachedValidators.minus(currentValidators)
        cachedValidators = currentValidators
        return removed
    }

    fun getExitBlock(validatorId: String): Long {
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
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ) {
        events
            .filter { ValidatorUtils.shouldProcessEvent(it, stakerSC) }
            .forEach { ev ->
                when (ev.eventType) {
                    "DelegationInitiated" ->
                        handleDelegationInitiated(ev, delegations, block, validatorSnapshots)
                    "DelegationExitRequested" ->
                        handleDelegationExitRequested(ev, delegations, delegationsToArchive, block)
                    "DelegationWithdrawn" ->
                        handleDelegationWithdrawn(ev, delegations, delegationsToArchive, block)
                    "DelegationRewardsClaimed" ->
                        handleDelegationRewardsClaimed(ev, delegations, delegationsToArchive, block)
                    "Transfer" -> handleTransfer(ev, delegations, delegationsToArchive, block)
                    "ValidatorExitRequested" ->
                        handleValidatorExitRequested(
                            ev,
                            delegations,
                            delegationsToArchive,
                            block,
                            validatorSnapshots,
                        )
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
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val validator = ev.params.getAsString("validator")!!
        val (cycleLength, nextCycle) = resolveCycleInfo(validator, block.number, validatorSnapshots)

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
        val delegationId = ev.params.getAsString("delegationId")!! // note: uppercase ID here
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

    /** Handles a token transfer */
    private fun handleTransfer(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        val tokenId = ev.params.getAsString("tokenId") ?: return
        val to = ev.params.getAsString("to") ?: return

        // Find the active delegation for this token
        val existing =
            delegations.values.find { it.tokenId == tokenId && it.status != Status.EXITED }
                ?: return

        // Archive the old state
        delegationsToArchive.add(existing)

        // Create updated copy with new owner
        delegations[existing.id] =
            existing.copy(
                owner = to,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                version = existing.version + 1,
            )
    }

    /** Handles a validator requesting exit → updates all of its delegations. */
    private fun handleValidatorExitRequested(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ) {
        val validatorId = ev.params.getAsString("validator")!!
        val exitAt = getValidatorExitBlock(validatorId, validatorSnapshots)

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
                        force = true,
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

    private fun nextStatus(status: Status): Status =
        if (status == Status.EXITING) Status.EXITED else Status.ACTIVE

    private fun resolveNextCycleBlock(
        lastCycleEnd: Long?,
        cycleLength: Long,
        currentBlock: Long,
    ): Long {
        val base = lastCycleEnd ?: currentBlock
        return if (base > currentBlock) {
            base
        } else {
            base + ((currentBlock - base) / cycleLength + 1) * cycleLength
        }
    }

    /** Decodes `getValidationPeriodDetails` response. */
    private fun decodeValidationPeriodDetails(data: String): Pair<Long, Long> {
        val decoded =
            FunctionReturnDecoder.decode(
                data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )
        val startBlock = (decoded["startBlock"] as BigInteger).toLong()
        val periodLength = (decoded["period"] as BigInteger).toLong()
        return startBlock to periodLength
    }

    /**
     * Fetches validator period info directly from chain.
     *
     * @return Pair(periodLength, nextCycleStart) or (periodLength, 0) if not started
     */
    fun getValidatorPeriodInfo(validatorId: String, currentBlock: Long): Pair<Long, Long> {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))
        val (startBlock, periodLength) = decodeValidationPeriodDetails(response[0].data)

        if (startBlock == 0L) return periodLength to 0L

        val offset = currentBlock - startBlock
        val positionInCycle = offset % periodLength
        val currentCycleStart = currentBlock - positionInCycle
        val nextCycleStart = currentCycleStart + periodLength

        return periodLength to nextCycleStart
    }

    /** Convenience helper if you only care about startBlock from an already-fetched response. */
    fun determineStartBlock(response: ExecuteCodeResponse): Long =
        decodeValidationPeriodDetails(response.data).first

    /**
     * Resolve a validator's exit block. Uses cached snapshot if available, otherwise falls back to
     * chain call.
     */
    fun getValidatorExitBlock(
        validatorId: String,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): Long =
        validatorSnapshots[validatorId]?.exitBlock
            ?: run {
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
                (decoded["exitBlock"] as BigInteger).toLong()
            }

    /** Decode validator state from chain call responses. */
    private fun decodeValidatorSnapshots(
        callResponses: List<InspectionResult>
    ): Map<String, ValidatorSnapshot> {
        val usable = callResponses.filter { it.hasAbiData() }
        if (usable.isEmpty()) {
            // nothing returned from the chain for this block; treat as no validators
            return emptyMap()
        }

        val decoded =
            ValidatorUtils.decodeValidators(
                callResponses,
                getDelegationsAbiFunctions("getValidators"),
            )
        val masters = decoded.listOf<String>("masters")
        val periods = decoded.listOf<BigInteger>("stakingPeriodLengths")
        val starts = decoded.listOf<BigInteger>("startBlocks")
        val exits = decoded.listOf<BigInteger>("exitBlocks")

        return masters
            .mapIndexed { i, id ->
                id to
                    ValidatorSnapshot(
                        validatorId = id,
                        stakingPeriodLength = periods[i].toLong(),
                        startBlock = starts[i].toLong(),
                        exitBlock = exits[i].toLong(),
                    )
            }
            .toMap()
    }

    private fun resolveCycleInfo(
        validatorId: String,
        blockNumber: Long,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): Pair<Long, Long> {
        val snapshot = validatorSnapshots[validatorId]
        return if (snapshot != null) {
            snapshot.stakingPeriodLength to
                ValidatorUtils.computeNextCycleStart(snapshot, blockNumber)
        } else {
            getValidatorPeriodInfo(validatorId, blockNumber)
        }
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
