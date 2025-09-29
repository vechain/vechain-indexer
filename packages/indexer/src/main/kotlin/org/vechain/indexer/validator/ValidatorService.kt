package org.vechain.indexer.validator

import kotlin.collections.set
import org.bson.types.Decimal128
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("validator")
@Service
open class ValidatorService(
    private val repository: ValidatorRepository,
    private val archiveService: ArchiveService<Validator, ValidatorArchive>,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    private val getAllValidatorInfoSC: String,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    open fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
    ): Triple<List<Validator>, List<Validator>, List<String>> {
        val threshold = getThreshold()

        // Skip old irrelevant blocks
        if (matchedEvents.isEmpty() && block.number < threshold) {
            return Triple(emptyList(), emptyList(), emptyList())
        }

        // Load docs once
        val existingDocs = loadExistingDocs(block, matchedEvents, threshold)
        val working = existingDocs.toMutableMap()

        // Apply beneficiary changes directly into the working map
        applyBeneficiaryChanges(matchedEvents, working)

        // For old blocks → only beneficiary changes matter
        if (block.number < threshold) {
            return Triple(working.values.toList(), emptyList(), emptyList())
        }

        // For recent blocks → also fetch chain state
        val (chainUpdates, toDelete) = fetchAndDecodeValidators(block, working)
        applyChainUpdates(chainUpdates, working)

        // Archive everything that existed before but is not deleted
        val archive = existingDocs.filterKeys { it !in toDelete }.values.toList()

        return Triple(working.values.toList(), archive, toDelete)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun saveAndDelete(
        updates: List<Validator>,
        archive: List<Validator>,
        delete: List<String>,
    ) {
        // Persist once
        repository.saveAll(updates)

        // Archive old state
        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }

        // Delete ancient exited validators
        if (delete.isNotEmpty()) {
            repository.deleteAllById(delete)
        }
    }

    private fun fetchAndDecodeValidators(
        block: Block,
        existingDocs: Map<String, Validator>,
    ): Pair<List<Validator>, List<String>> {
        // Build the batch of contract calls
        val clauses = buildClauses()

        // Execute all calls in one read-only request
        val responses = thorService.executeReadOnlyCode(clauses)

        // Decode into validators + delete list
        return ValidatorUtils.getLatestValidatorInfo(
            responses = responses,
            validatorsAbi = cachedGetValidatorsAbi,
            existingDocs = existingDocs,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
        )
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

    fun buildClauses(): List<Clause> {
        val functionNames =
            listOf(
                "getValidators",
                "totalStake",
                "vthoTotalSupply",
                "getVetPriceUsd",
                "getVthoPriceUsd",
            )

        return functionNames.map { fnName ->
            ContractUtils.createClause(getAllValidatorInfoSC, getValidatorsAbiFunctions(fnName))
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
            repository.findAll().associateBy { it.id }
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
                        totalVTHOSupply = Decimal128(0),
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

    private fun getValidatorsAbiFunctions(name: String): AbiElement =
        cachedGetValidatorsAbi[name]
            ?: run {
                val abis =
                    AbiLoader.loadFunctions(
                        basePath = "abis/stargate",
                        functionNames = listOf(name),
                    )

                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException(
                            "Function '$name' not found in authority-node ABI"
                        )
                cachedGetValidatorsAbi[name] = abi
                abi
            }
}
