package org.vechain.indexer.validator

import java.math.BigInteger
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.logic.DelegationEventMutations
import org.vechain.indexer.validator.logic.DelegationStateTransitions
import org.vechain.indexer.validator.logic.ValidatorCycleContext
import org.vechain.indexer.validator.logic.ValidatorInfoDecoder

@Profile("validator")
@Service
class ValidatorService(
    private val repository: ValidatorRepository,
    private val archiveService: ArchiveService<Validator, ValidatorArchive>,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    private val getAllValidatorInfoSC: String,
    @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}") private val stakerSC: String,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    fun processBlock(block: Block, matchedEvents: List<IndexedEvent>) {
        // Load existing docs from DB once
        val existingDocs = repository.findAll().associateBy { it.id }

        // Determine if indexer is ready to start getting latest block info
        val bestBlock = thorService.getBestBlock()
        val cutoffBlock = bestBlock.number - 25

        // Build working context
        var context =
            if (block.number < cutoffBlock) {
                // Build context only from DB — skip SC calls
                ValidatorCycleContext(
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    _validators = existingDocs.toMutableMap(),
                )
            } else {
                // Full path: fetch validator info from SC
                val responses = thorService.executeReadOnlyCode(buildClauses())
                ValidatorInfoDecoder.getLatestValidatorInfo(
                    responses,
                    cachedGetValidatorsAbi,
                    existingDocs,
                    block.id,
                    block.number,
                    block.timestamp,
                )
            }

        // Apply block-based updates (i.e Transitions that  will happen after a certain block such
        // as moving form queued state to active)
        context = DelegationStateTransitions.handleBlockUpdatesInContext(context)

        // Apply event-based mutations, these events all relate to delegations
        if (matchedEvents.isNotEmpty()) {
            context = DelegationEventMutations.applyEvents(context, matchedEvents)
        }

        // Persist once
        repository.saveAll(context.snapshot())

        // Archive old state
        if (existingDocs.isNotEmpty()) {
            archiveService.saveAll(existingDocs.values.toList())
        }

        // 8. Delete missing validators
        val toDelete = existingDocs.keys.minus(context.validators.keys)
        if (toDelete.isNotEmpty()) {
            repository.deleteAllById(toDelete)
        }
    }

    fun handleBlockUpdates(blockNumber: Long) {
        val validators = repository.findByBlockNumberAndDelegationsToBeActionedNotEmpty(blockNumber)

        if (validators.isEmpty()) return
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

    private fun resolveNextCycleBlock(validatorId: String): Long {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getValidatorsAbiFunctions("getValidationPeriodDetails"),
                validatorId.toBigInteger(), // if ABI expects validatorId as arg
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))

        val decodedPeriodInfo =
            FunctionReturnDecoder.decode(
                response[0].data,
                getValidatorsAbiFunctions("getValidationPeriodDetails").outputs,
            )

        val startBlock = (decodedPeriodInfo["startBlock"] as BigInteger).toLong()
        val periodLength = (decodedPeriodInfo["period"] as BigInteger).toLong()
        val completedPeriods = (decodedPeriodInfo["completedPeriods"] as BigInteger).toLong()

        return startBlock + (periodLength * (completedPeriods + 1L))
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
