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
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.logic.DelegationContext
import org.vechain.indexer.validator.logic.DelegationEventMutations
import org.vechain.indexer.validator.logic.DelegationStateTransitions

@Profile("validator")
@Service
open class DelegationService(
    private val repository: DelegationRepository,
    private val archiveService: ArchiveService<Delegation, DelegationArchive>,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}") private val stakerSC: String,
) {
    private val cachedGetDelegationAbi: MutableMap<String, AbiElement> = mutableMapOf()

    open fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
    ): Pair<List<Delegation>, List<Delegation>> {
        // Load existing docs from DB once
        val existingDocs = repository.findAll().associateBy { it.id }

        var context =
            DelegationContext(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                _delegations = existingDocs.toMutableMap(),
            )

        // Apply block-based updates (i.e Transitions that will happen after a certain block such
        // as moving form queued state to active)
        context = DelegationStateTransitions.handleBlockUpdatesInContext(context)

        // Apply event-based mutations
        if (matchedEvents.isNotEmpty()) {
            // Add callback to the function to context
            val contextWithCallback =
                DelegationContext(
                    blockId = context.blockId,
                    blockNumber = context.blockNumber,
                    blockTimestamp = context.blockTimestamp,
                    _delegations = context.delegations.toMutableMap(),
                    nextCycleResolver = { validatorId, blockNumber ->
                        getValidatorPeriodInfo(validatorId, blockNumber)
                    },
                    validatorExitBlockResolver = { validatorId -> getExitBlock(validatorId) },
                )
            context = DelegationEventMutations.applyEvents(contextWithCallback, matchedEvents)
        }

        return Pair(context.delegations.values.toMutableList(), existingDocs.values.toMutableList())
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updates: List<Delegation>, archive: List<Delegation>) {
        // Persist once
        repository.saveAll(updates)

        // Archive old state
        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }
    }

    private fun getExitBlock(validatorId: String): Long {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )

        val response = thorService.executeReadOnlyCode(listOf(clause))
        val decodedPeriodInfo =
            FunctionReturnDecoder.decode(
                response[0].data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )

        return (decodedPeriodInfo["exitBlock"] as BigInteger).toLong()
    }

    private fun getValidatorPeriodInfo(validatorId: String, currentBlock: Long): Pair<Long, Long> {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )

        val response = thorService.executeReadOnlyCode(listOf(clause))
        val decodedPeriodInfo =
            FunctionReturnDecoder.decode(
                response[0].data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )

        val startBlock = (decodedPeriodInfo["startBlock"] as BigInteger).toLong()
        val periodLength = (decodedPeriodInfo["period"] as BigInteger).toLong()

        val offset = currentBlock - startBlock
        val positionInCycle = offset % periodLength
        val currentCycleStart = currentBlock - positionInCycle
        val nextCycleStart = currentCycleStart + periodLength

        return (periodLength to nextCycleStart)
    }

    private fun getDelegationsAbiFunctions(name: String): AbiElement =
        cachedGetDelegationAbi[name]
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
                cachedGetDelegationAbi[name] = abi
                abi
            }
}
