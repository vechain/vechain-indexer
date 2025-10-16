package org.vechain.indexer.validator

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
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
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

        // Apply event changes from blockchain logs
        applyEventChanges(matchedEvents, working)

        if (callResponses.none { it.hasAbiData() }) {
            return working.values.toList() to emptyList()
        }

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

    private fun applyChainUpdates(
        chainUpdates: List<Validator>,
        working: MutableMap<String, Validator>,
    ) {
        chainUpdates.forEach { v ->
            val existing = working[v.id]
            working[v.id] =
                if (existing != null) {
                    v.copy(beneficiary = existing.beneficiary) // keep latest beneficiary
                    v.copy(
                        queuedValidatorVetStaked = existing.queuedValidatorVetStaked
                    ) // keep pending stake
                    v.copy(
                        exitingValidatorVetStaked = existing.exitingValidatorVetStaked
                    ) // keep pending stake
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
            // Old blocks → only fetch validators referenced in events
            val ids = matchedEvents.mapNotNull { it.params.getAsString("validator") }.distinct()
            if (ids.isEmpty()) emptyMap() else repository.findAllById(ids).associateBy { it.id }
        } else {
            // Recent blocks → load all non-exited validators
            repository.findByStatusNot(Status.EXITED).associateBy { it.id }
        }

    private fun applyEventChanges(
        events: List<IndexedEvent>,
        working: MutableMap<String, Validator>,
    ) {
        events.forEach { ev ->
            val validatorId = ev.params.getAsString("validator") ?: return@forEach
            val base = working[validatorId] ?: return@forEach

            working[validatorId] =
                base.copy(
                    blockId = ev.blockId,
                    blockNumber = ev.blockNumber,
                    blockTimestamp = ev.blockTimestamp,
                    beneficiary = ev.params.getAsString("beneficiary") ?: base.beneficiary,
                    queuedValidatorVetStaked =
                        ValidatorCalculator.updatePendingValidatorVET(
                            ev.params.getAsBigInteger("added"),
                            base.queuedValidatorVetStaked,
                        ),
                    exitingValidatorVetStaked =
                        ValidatorCalculator.updatePendingValidatorVET(
                            ev.params.getAsBigInteger("removed"),
                            base.exitingValidatorVetStaked,
                        ),
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
