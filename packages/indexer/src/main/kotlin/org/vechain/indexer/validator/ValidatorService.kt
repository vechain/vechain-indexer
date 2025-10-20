package org.vechain.indexer.validator

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
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
    private val archiveService: ArchiveService<Validator, ValidatorArchive>,
    private val thorService: ThorService,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    open fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Validator>, List<Validator>> {
        val threshold = getThreshold()

        // Load docs once
        val existingDocs = loadExistingDocs(block, matchedEvents, threshold)
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

        // Determine if old block
        val isOldBlock = block.number < threshold

        // Apply event changes from blockchain logs
        applyEventChanges(matchedEvents, working, callResponses, isOldBlock)

        // For old blocks → only beneficiary changes matter or if responses have no ABI data
        if (isOldBlock || callResponses.none { it.hasAbiData() }) {
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

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updates: List<Validator>, archive: List<Validator>) {
        repository.saveAll(updates)

        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }
    }

    private fun loadExistingDocs(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        threshold: Long,
    ): Map<String, Validator> =
        if (block.number < threshold) {
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

            working[validatorId] =
                base.copy(
                    blockId = ev.blockId,
                    blockNumber = ev.blockNumber,
                    blockTimestamp = ev.blockTimestamp,
                    beneficiary = ev.params.getAsString("beneficiary") ?: base.beneficiary,
                    exitingValidatorVetStaked =
                        ValidatorCalculator.updatePendingValidatorVET(
                            ev.params.getAsBigInteger("removed"),
                            base.exitingValidatorVetStaked,
                            base.blockNumber,
                            ev.blockNumber,
                            base.startBlock,
                            base.cyclePeriodLength,
                        ),
                    startBlock = base.startBlock ?: periodInfo?.get(validatorId)?.first,
                    cyclePeriodLength =
                        base.cyclePeriodLength ?: periodInfo?.get(validatorId)?.second,
                )
        }
    }

    private fun getThreshold(): Long {
        val bestBlock = thorService.getBestBlock()
        return bestBlock.number - 25
    }

    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)
        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }
}
