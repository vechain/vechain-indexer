package org.vechain.indexer.delegation

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.stargate.Delegation
import org.vechain.indexer.stargate.DelegationArchive
import org.vechain.indexer.stargate.DelegationRepository
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.logic.DelegationEventMutations
import org.vechain.indexer.validator.logic.DelegationStateTransitions
import org.vechain.indexer.validator.logic.ValidatorCycleContext
import org.vechain.indexer.validator.logic.ValidatorInfoDecoder
import java.math.BigInteger
import kotlin.collections.set

@Profile("validator")
@Service
open class DelegationService(
    private val repository: DelegationRepository,
    private val archiveService: ArchiveService<Delegation, DelegationArchive>,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    private val getAllValidatorInfoSC: String,
    @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}") private val stakerSC: String,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    open fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
    ): Pair<List<Delegation>, List<Delegation>> {
        // Load existing docs from DB once
        val existingDocs = repository.findAll().associateBy { it.id }

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

        // Apply block-based updates (i.e Transitions that will happen after a certain block such
        // as moving form queued state to active)
        context = DelegationStateTransitions.handleBlockUpdatesInContext(context)

        // Apply event-based mutations, these events relate to delegations and validator beneficiary
        if (matchedEvents.isNotEmpty()) {
            // Add callback to the function to context
            val contextWithCallback =
                ValidatorCycleContext(
                    blockId = context.blockId,
                    blockNumber = context.blockNumber,
                    blockTimestamp = context.blockTimestamp,
                    _validators = context.validators.toMutableMap(),
                    nextCycleResolver = { validatorId, blockNumber ->
                        getValidatorPeriodInfo(validatorId, blockNumber)
                    },
                )
            context = DelegationEventMutations.applyEvents(contextWithCallback, matchedEvents)
        }

        // Get latest validator info
        val newState = context.snapshot()
        // Delete validators that have exited over X amount of time ago
        val toDelete = existingDocs.keys.minus(newState.keys)

        // Separate updated docs (new) and originals (for archive)
        val updatedDocs = mutableListOf<Delegation>()
        val originalsForArchive = mutableListOf<Delegation>()

        for (newVal in newState.values) {
            val oldVal = existingDocs[newVal.id]
            if (oldVal == null || oldVal != newVal) {
                updatedDocs += newVal.copy(version = (oldVal?.version ?: 0) + 1)
                if (oldVal != null) {
                    originalsForArchive += oldVal
                }
            }
        }

        return Triple(updatedDocs, originalsForArchive.toList(), toDelete)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun saveAndDelete(
        updates: List<Delegation>,
        archive: List<Delegation>,
    ) {
        // Persist once
        repository.saveAll(updates)

        // Archive old state
        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }
    }

    fun buildClause(): List<Clause> = ContractUtils.createClause(stakerSC, getValidatorsAbiFunctions(fnName))

    private fun getValidatorPeriodInfo(
        validatorId: String,
        currentBlock: Long,
    ): Pair<Long, Long> {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getValidatorsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )

        val response = thorService.executeReadOnlyCode(listOf(clause))
        val decodedPeriodInfo =
            FunctionReturnDecoder.decode(
                response[0].data,
                getValidatorsAbiFunctions("getValidationPeriodDetails").outputs,
            )

        val startBlock = (decodedPeriodInfo["startBlock"] as BigInteger).toLong()
        val periodLength = (decodedPeriodInfo["period"] as BigInteger).toLong()

        val offset = currentBlock - startBlock
        val positionInCycle = offset % periodLength
        val currentCycleStart = currentBlock - positionInCycle
        val nextCycleStart = currentCycleStart + periodLength

        return (periodLength to nextCycleStart)
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
                            "Function '$name' not found in authority-node ABI",
                        )
                cachedGetValidatorsAbi[name] = abi
                abi
            }
}
