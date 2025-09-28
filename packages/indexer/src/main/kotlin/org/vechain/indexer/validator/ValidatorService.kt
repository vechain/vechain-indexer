package org.vechain.indexer.validator

import kotlin.collections.set
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
import org.vechain.indexer.validator.logic.ValidatorInfoDecoder

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
        // Load existing docs from DB once
        val existingDocs = repository.findAll().associateBy { it.id }

        // Build working context
        val responses = thorService.executeReadOnlyCode(buildClauses())
        val (validators, toDelete) =
            ValidatorInfoDecoder.getLatestValidatorInfo(
                responses,
                cachedGetValidatorsAbi,
                existingDocs,
                block.id,
                block.number,
                block.timestamp,
            )

        // Anything that isn't getting deleted can get archived
        val archive = existingDocs.minus(toDelete)

        return Triple(validators, archive.values.toList(), toDelete)
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
