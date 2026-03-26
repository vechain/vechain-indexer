package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.domain.ValidatorDecoder
import org.vechain.indexer.validator.logic.ValidatorAssembler
import org.vechain.indexer.validator.logic.ValidatorCalculator

@Profile("validator", "validator-stats")
@Service
open class ValidatorService(
    private val repository: ValidatorRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val cachedGetValidatorsAbi: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    /**
     * Processes a block to update validator state.
     * - Loads current validator documents.
     * - Falls back to event-only updates until helper-backed snapshot data becomes available.
     * - Applies same-block event overlays for fields not fully derivable from chain call data.
     * - Decodes full validator state from chain calls and derives current block state.
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
    ): Pair<List<Validator>, List<Validator>> {
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

        val existingDocs = loadExistingDocs()
        val carriedDocs = existingDocs.toMutableMap()
        applyEventChanges(matchedEvents, carriedDocs)

        val decodedInfo =
            ValidatorDecoder.decodeResponseInfo(callResponses, cachedGetValidatorsAbi)
                ?: run {
                    if (canUseEventOnlyFallback(existingDocs)) {
                        logger.info(
                            "Using event-only validator fallback for block {} because getValidators helper call data is unavailable before snapshot-backed validator state exists",
                            block.number,
                        )
                        return buildEventOnlyUpdates(existingDocs, carriedDocs)
                    }
                    throw IllegalStateException(
                        "Missing or invalid validator call data for block ${block.number}"
                    )
                }

        val chainUpdates =
            ValidatorAssembler.unpackValidators(
                rows = ValidatorDecoder.decodeRows(decodedInfo.decodedValidators),
                persistedDocs = existingDocs,
                carriedDocs = carriedDocs,
                totalWeight = decodedInfo.totalWeight,
                vetPriceUsd = decodedInfo.vetPriceUsd,
                vthoPriceUsd = decodedInfo.vthoPriceUsd,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )

        return chainUpdates to archiveDocsForUpdates(existingDocs, chainUpdates)
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

    private fun loadExistingDocs(): Map<String, Validator> =
        repository.findByStatusNot(Status.EXITED).associateBy { it.id }

    private fun canUseEventOnlyFallback(existingDocs: Map<String, Validator>): Boolean =
        existingDocs.isEmpty() || existingDocs.values.none(::hasSnapshotBackedState)

    private fun hasSnapshotBackedState(validator: Validator): Boolean =
        validator.endorser != null ||
            validator.status != null ||
            validator.startBlock != null ||
            validator.cyclePeriodLength != null

    private fun buildEventOnlyUpdates(
        existingDocs: Map<String, Validator>,
        carriedDocs: Map<String, Validator>,
    ): Pair<List<Validator>, List<Validator>> {
        val updates =
            carriedDocs.values.mapNotNull { carried ->
                val existing = existingDocs[carried.id]
                val candidate = carried.copy(version = (existing?.version ?: 0) + 1)
                candidate.takeUnless { existing != null && candidate.isEquivalentTo(existing) }
            }

        return updates to archiveDocsForUpdates(existingDocs, updates)
    }

    private fun archiveDocsForUpdates(
        existingDocs: Map<String, Validator>,
        updates: List<Validator>,
    ): List<Validator> = updates.mapNotNull { existingDocs[it.id] }

    /**
     * Applies validator updates based on blockchain events.
     * - Sets/updates `beneficiary` and `exitingValidatorVetStaked`.
     * - Only fields not fully derivable from `getValidators` should be handled here.
     *
     * @param events Blockchain events from this block.
     * @param working Mutable map of validators being updated.
     */
    private fun applyEventChanges(
        events: List<IndexedEvent>,
        working: MutableMap<String, Validator>,
    ) {
        events.forEach { ev ->
            val validatorId = ev.params.getAsString("validator")!!
            val base =
                working[validatorId]
                    ?: Validator(
                        id = validatorId,
                        blockId = ev.blockId,
                        blockNumber = ev.blockNumber,
                        blockTimestamp = ev.blockTimestamp,
                        beneficiary = ev.params.getAsString("beneficiary"),
                    )

            val updatedExitingStake =
                when (ev.eventType) {
                    "StakeIncreased" -> {
                        val added =
                            NumberUtils.toVET(ev.params.getAsBigInteger("added") ?: BigInteger.ZERO)
                        (base.exitingValidatorVetStaked - added).max(BigDecimal.ZERO)
                    }
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
                )
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
