package org.vechain.indexer.validator

import kotlin.collections.set
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
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.ValidatorUtils.hasAbiData

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

        // Skip old irrelevant blocks
        if (matchedEvents.isEmpty() && block.number < threshold) {
            return Pair(emptyList(), emptyList())
        }

        // Load docs once
        val existingDocs = loadExistingDocs(block, matchedEvents, threshold)
        val working = existingDocs.toMutableMap()

        // Apply beneficiary changes directly into the working map
        applyBeneficiaryChanges(matchedEvents, working)

        // For old blocks → only beneficiary changes matter or if responses have no ABI data
        if (block.number < threshold || callResponses.none { it.hasAbiData() }) {
            return working.values.toList() to emptyList()
        }

        // Fetch ABIs for decoding
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

        // Decode into validators + delete list
        val chainUpdates =
            ValidatorUtils.getLatestValidatorInfo(
                responses = callResponses,
                validatorsAbi = cachedGetValidatorsAbi,
                existingDocs = existingDocs,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )
        applyChainUpdates(chainUpdates, working)

        return working.values.toList() to existingDocs.values.toList()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updates: List<Validator>, archive: List<Validator>) {
        // Persist once
        repository.saveAll(updates)

        // Archive old state
        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }
    }

    private fun applyChainUpdates(
        chainUpdates: List<Validator>,
        working: MutableMap<String, Validator>,
    ) {
        chainUpdates.forEach { v ->
            val existing = working[v.id]
            working[v.id] =
                if (existing != null) {
                    v.copy(beneficiary = existing.beneficiary) // keep latest beneficiary change
                } else {
                    v
                }
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

    private fun applyBeneficiaryChanges(
        events: List<IndexedEvent>,
        working: MutableMap<String, Validator>,
    ) {
        events.forEach { ev ->
            val validatorId = ev.params.getAsString("validator")!!
            val beneficiary = ev.params.getAsString("beneficiary")!!

            val base =
                working[validatorId]
                    ?: Validator(
                        id = validatorId,
                        blockId = ev.blockId,
                        blockNumber = ev.blockNumber,
                        blockTimestamp = ev.blockTimestamp,
                        beneficiary = beneficiary,
                        version = 0,
                    )

            working[validatorId] =
                base.copy(
                    blockId = ev.blockId,
                    blockNumber = ev.blockNumber,
                    blockTimestamp = ev.blockTimestamp,
                    beneficiary = beneficiary,
                )
        }
    }

    private fun getThreshold(): Long {
        val bestBlock = thorService.getBestBlock()
        return bestBlock.number - 25
    }

    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return // already loaded

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)

        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }
}
