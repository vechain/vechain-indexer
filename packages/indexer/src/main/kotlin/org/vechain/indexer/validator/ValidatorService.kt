package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.domain.ValidatorDecoder
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData
import org.vechain.indexer.validator.logic.ValidatorAssembler
import org.vechain.indexer.validator.logic.ValidatorCalculator

@Profile("validator", "validator-stats")
@Service
open class ValidatorService(
    private val repository: ValidatorRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val thorClient: ThorClient,
    @Value("\${indexer.validator-stats-threshold-blocks}") private val statsStartThreshold: Long,
    @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}") private val stakerSC: String,
) {
    private val cachedGetValidatorsAbi: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    /** Flag to track if queue positions have been initialized */
    @Volatile private var queueInitialized: Boolean = false

    /**
     * Processes a block to update validator state.
     * - Loads existing validator documents (from DB or cache).
     * - Applies changes detected from blockchain events.
     * - Optionally decodes full validator state from chain calls (if not an old block).
     *
     * @param block Current blockchain block being processed.
     * @param matchedEvents Events from this block related to validators.
     * @param callResponses Read-only call responses (e.g. contract state).
     * @return Pair of:
     *     - Updated validators (to be persisted).
     *     - Validators that should be archived.
     */
    open fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
        isFullySynced: Boolean,
    ): Pair<List<Validator>, List<Validator>> {

        // Load docs once
        val existingDocs = loadExistingDocs(matchedEvents, isFullySynced)
        val working = existingDocs.toMutableMap()

        // Load ABIs if not cached
        loadAllValidatorAbiFunctions(
            listOf(
                "getValidators",
                "totalStake",
                "vthoTotalSupply",
                "getVetPriceUsd",
                "getVthoPriceUsd",
                "totalBurned",
            )
        )

        // Apply event changes from blockchain logs
        applyEventChanges(matchedEvents, working, callResponses, !isFullySynced)

        // For old blocks → only beneficiary changes matter or if responses have no ABI data
        if (!isFullySynced || callResponses.none { it.hasAbiData() }) {
            return working.values.toList() to emptyList()
        }

        // Decode and calculate full validator updates
        val chainUpdates =
            ValidatorAssembler.getLatestValidatorInfo(
                responses = callResponses,
                validatorsAbi = cachedGetValidatorsAbi,
                existingDocs = existingDocs,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )

        // Merge into working set
        applyChainUpdates(chainUpdates, working)

        return working.values.toList() to existingDocs.values.toList()
    }

    /**
     * Persists updated validator records and archives old ones.
     *
     * @param updates List of validators with new state.
     * @param archive List of validators to archive.
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(updates: List<Validator>, archive: List<Validator>) {
        saveVersionedDocuments(
            updates,
            archive,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    /**
     * Loads existing validator documents depending on block number.
     * - For older blocks: only fetch docs for validators present in events.
     * - For newer blocks: fetch all non-exited validators.
     *
     * @param matchedEvents Events in this block.
     * @param isFullySynced Whether the block is recent enough to consider full sync.
     * @return Map of validatorId → Validator document.
     */
    private fun loadExistingDocs(
        matchedEvents: List<IndexedEvent>,
        isFullySynced: Boolean,
    ): Map<String, Validator> =
        if (!isFullySynced) {
            // For old blocks → only fetch docs for validators in events
            val ids = matchedEvents.mapNotNull { it.params.getAsString("validator") }.distinct()

            if (ids.isEmpty()) {
                emptyMap()
            } else {
                repository.findAllById(ids).associateBy { it.id }
            }
        } else {
            // For recent blocks → load all validators once
            repository.findByStatusNot(Status.EXITED).associateBy { it.id }
        }

    /**
     * Applies chain updates (decoded from contract calls) into the working validator set.
     * - Preserves certain fields like `beneficiary` and `exitingValidatorVetStaked` if the
     *   validator already exists in the working map.
     *
     * @param chainUpdates List of validators decoded from chain.
     * @param working Mutable map of validators being updated.
     */
    private fun applyChainUpdates(
        chainUpdates: List<Validator>,
        working: MutableMap<String, Validator>,
    ) {
        chainUpdates.forEach { v ->
            val existing = working[v.id]
            working[v.id] =
                if (existing != null) {
                    v.copy(
                        beneficiary = existing.beneficiary,
                        exitingValidatorVetStaked = existing.exitingValidatorVetStaked,
                    )
                } else {
                    v
                }
        }
    }

    /**
     * Applies validator updates based on blockchain events.
     * - Sets/updates `beneficiary` and `exitingValidatorVetStaked` fields.
     *
     * @param events Blockchain events from this block.
     * @param working Mutable map of validators being updated.
     * @param responses Optional call responses (used for old blocks).
     * @param isOldBlock Whether this block is considered old (pre-threshold).
     */
    private fun applyEventChanges(
        events: List<IndexedEvent>,
        working: MutableMap<String, Validator>,
        responses: List<InspectionResult> = emptyList(),
        isOldBlock: Boolean = false,
    ) {
        val periodInfo =
            if (isOldBlock) {
                val validatorIdsToFetch =
                    working
                        .filter { (_, doc) -> doc.startBlock == null || doc.startBlock == 0L }
                        .map { it.key }

                ValidatorDecoder.getValidatorPeriodDetails(
                    validatorIds = validatorIdsToFetch,
                    responses = responses,
                    validatorsAbi = cachedGetValidatorsAbi,
                )
            } else {
                emptyMap()
            }

        events.forEach { ev ->
            val validatorId = ev.params.getAsString("validator")!!
            val base =
                if (working[validatorId] != null) {
                    working[validatorId]!!
                } else {
                    Validator(
                        id = validatorId,
                        blockId = ev.blockId,
                        blockNumber = ev.blockNumber,
                        blockTimestamp = ev.blockTimestamp,
                        beneficiary = ev.params.getAsString("beneficiary"),
                        exitingValidatorVetStaked =
                            NumberUtils.toVET(
                                ev.params.getAsBigInteger("removed") ?: BigInteger.ZERO
                            ),
                        version = 0,
                        startBlock = periodInfo?.get(validatorId)?.first,
                        cyclePeriodLength = periodInfo?.get(validatorId)?.second,
                    )
                }

            val updatedExitingStake =
                when (ev.eventType) {
                    "ValidationWithdrawn" -> {
                        val withdrawn =
                            NumberUtils.toVET(ev.params.getAsBigInteger("stake") ?: BigInteger.ZERO)
                        (base.exitingValidatorVetStaked - withdrawn).max(BigDecimal.ZERO)
                    }
                    else ->
                        ValidatorCalculator.updatePendingValidatorVET(
                            ev.params.getAsBigInteger("removed"),
                            base.exitingValidatorVetStaked,
                            base.blockNumber,
                            ev.blockNumber,
                            base.startBlock,
                            base.cyclePeriodLength,
                        )
                }

            working[validatorId] =
                base.copy(
                    blockId = ev.blockId,
                    blockNumber = ev.blockNumber,
                    blockTimestamp = ev.blockTimestamp,
                    beneficiary = ev.params.getAsString("beneficiary") ?: base.beneficiary,
                    exitingValidatorVetStaked = updatedExitingStake,
                    startBlock = base.startBlock ?: periodInfo?.get(validatorId)?.first,
                    cyclePeriodLength =
                        base.cyclePeriodLength ?: periodInfo?.get(validatorId)?.second,
                )
        }
    }

    /**
     * Initialize queue positions for validators if not already done. This should be called when the
     * indexer becomes fully synced. Always fetches queue order from contract on first call after
     * restart.
     *
     * @param blockId The current block ID to use for contract calls.
     */
    suspend fun getTotalVETStaked(blockId: String): BigInteger {
        val res = thorClient.getAccountState(address = stakerSC, BlockRevision.Id(blockId))
        return res.balance.removePrefix("0x").ifEmpty { "0" }.toBigInteger(16)
    }

    open suspend fun initializeQueuePositionsIfNeeded(blockId: String) {
        if (queueInitialized) return

        // Always fetch queue order from contract on restart
        val queueOrder = fetchQueueOrderFromContract(blockId)
        if (queueOrder.isNotEmpty()) {
            println("Initializing queue positions for ${queueOrder.size} validators.")
            updateQueuePositions(queueOrder)
        }

        queueInitialized = true
    }

    /**
     * Fetch the queue order from the staker contract. Calls firstQueued, then iterates with next
     * until zero address.
     *
     * @param blockId Block ID for the contract calls.
     * @return Ordered list of validator addresses in queue.
     */
    private suspend fun fetchQueueOrderFromContract(blockId: String): List<String> {
        val queueOrder = mutableListOf<String>()

        // Get first queued validator
        val firstClause = ValidatorDecoder.buildFirstQueuedClause(stakerSC)
        val firstResponse =
            thorClient.inspectClauses(listOf(firstClause), BlockRevision.Id(blockId))

        if (firstResponse.isEmpty()) return emptyList()

        // decodeFirstQueued returns null if zero address (empty queue)
        var current =
            ValidatorDecoder.decodeFirstQueued(firstResponse.first()) ?: return emptyList()

        queueOrder.add(current)

        // Iterate through queue with next() until zero address
        while (true) {
            val nextClause = ValidatorDecoder.buildNextQueuedClause(stakerSC, current)
            val nextResponse =
                thorClient.inspectClauses(listOf(nextClause), BlockRevision.Id(blockId))

            if (nextResponse.isEmpty()) break

            // decodeNextQueued returns null if zero address (end of queue)
            val next = ValidatorDecoder.decodeNextQueued(nextResponse.first()) ?: break

            queueOrder.add(next)
            current = next
        }

        return queueOrder
    }

    /**
     * Update queue positions for validators based on the fetched order.
     *
     * @param queueOrder Ordered list of validator addresses.
     */
    private fun updateQueuePositions(queueOrder: List<String>) {
        val validators = repository.findAllById(queueOrder).associateBy { it.id }

        val updates =
            queueOrder.mapIndexedNotNull { index, validatorId ->
                val validator = validators[validatorId] ?: return@mapIndexedNotNull null
                validator.copy(queuePosition = (index + 1).toLong())
            }

        if (updates.isNotEmpty()) {
            repository.saveAll(updates)
        }
    }

    /**
     * Loads and caches ABI function definitions needed for validator processing.
     * - Loads from `abis/stargate` path.
     * - Only runs once; subsequent calls reuse cached ABIs.
     *
     * @param functionNames List of ABI function names to load.
     */
    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)
        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }
}
