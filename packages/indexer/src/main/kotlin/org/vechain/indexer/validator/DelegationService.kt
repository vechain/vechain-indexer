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
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("delegation")
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
        events: List<IndexedEvent>,
    ): Pair<List<Delegation>, List<Delegation>> {
        val archive = mutableListOf<Delegation>()

        // Delegations due this block
        val due =
            repository.findByValidatorNextCycleAndStatusIn(
                block.number,
                listOf(Status.QUEUED, Status.EXITING),
            )

        // Delegations referenced by events
        val eventDelegationIds = events.mapNotNull { getDelegationIdFromParams(it.params) }.toSet()
        val eventDelegations =
            if (eventDelegationIds.isNotEmpty()) {
                repository.findAllById(eventDelegationIds).toList()
            } else {
                emptyList()
            }

        // Delegations for validators that requested exit
        val exitValidators =
            events
                .filter { it.eventType == "ValidatorExitRequested" }
                .mapNotNull { it.params.getAsString("validator") }
                .toSet()
        val validatorExitDelegations =
            if (exitValidators.isNotEmpty()) {
                repository.findByValidatorIn(exitValidators.toList())
            } else {
                emptyList()
            }

        // Seed working set (deduped map)
        val delegations =
            (due + eventDelegations + validatorExitDelegations).associateBy { it.id }.toMutableMap()

        val delegationsToArchive = delegations.values.toMutableList()

        // Apply block transitions
        delegations.values
            .filter {
                (it.status == Status.QUEUED || it.status == Status.EXITING) &&
                    it.validatorNextCycle == block.number
            }
            .forEach { d ->
                delegations[d.id] =
                    d.copy(
                        status = nextStatus(d.status),
                        notify = true,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = d.version + 1,
                    )
            }

        // Apply event mutations
        applyEventMutations(events, delegations, block)

        return delegations.values.toList() to delegationsToArchive
    }

    @Transactional
    open fun save(updates: List<Delegation>, archive: List<Delegation>) {
        if (updates.isNotEmpty()) repository.saveAll(updates)
        if (archive.isNotEmpty()) archiveService.saveAll(archive)
    }

    private fun getDelegationIdFromParams(params: AbiEventParameters): String? =
        (params.getAsString("delegationId") ?: params.getAsString("delegationID")) as? String

    private fun nextStatus(status: Status): Status =
        if (status == Status.EXITING) Status.EXITED else Status.ACTIVE

    private fun resolveNextCycleBlock(
        lastCycleEnd: Long?,
        cycleLength: Long,
        currentBlock: Long,
    ): Long {
        val base = lastCycleEnd ?: currentBlock
        return if (base > currentBlock) base
        else base + ((currentBlock - base) / cycleLength + 1) * cycleLength
    }

    private fun getExitBlock(validatorId: String): Long {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))
        val decoded =
            FunctionReturnDecoder.decode(
                response[0].data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )
        return (decoded["exitBlock"] as BigInteger).toLong()
    }

    private fun applyEventMutations(
        events: List<IndexedEvent>,
        delegations: MutableMap<String, Delegation>,
        block: Block,
    ) {
        events.forEach { ev ->
            when (ev.eventType) {
                "DelegationInitiated" -> handleDelegationInitiated(ev, delegations, block)
                "DelegationExitRequested" -> handleDelegationExitRequested(ev, delegations, block)
                "DelegationWithdrawn" -> handleDelegationWithdrawn(ev, delegations, block)
                "DelegationRewardsClaimed" -> handleDelegationRewardsClaimed(ev, delegations, block)
                "ValidatorExitRequested" -> handleValidatorExitRequested(ev, delegations, block)
            }
        }
    }

    private fun getValidatorPeriodInfo(validatorId: String, currentBlock: Long): Pair<Long, Long> {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))
        val decoded =
            FunctionReturnDecoder.decode(
                response[0].data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )
        val startBlock = (decoded["startBlock"] as BigInteger).toLong()
        val periodLength = (decoded["period"] as BigInteger).toLong()
        val offset = currentBlock - startBlock
        val positionInCycle = offset % periodLength
        val currentCycleStart = currentBlock - positionInCycle
        val nextCycleStart = currentCycleStart + periodLength
        return periodLength to nextCycleStart
    }

    private fun handleDelegationInitiated(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val validator = ev.params.getAsString("validator")!!
        val (cycleLength, nextCycle) = getValidatorPeriodInfo(validator, block.number)

        val newDelegation =
            Delegation(
                id = delegationId,
                validator = validator,
                tokenId = ev.params.getAsString("tokenId")!!,
                tokenLevel = TokenLevel.fromOrdinal(ev.params.getAsString("levelId")!!.toInt())!!,
                status = Status.QUEUED,
                stakedAmount = ev.params.getAsString("amount")!!,
                totalRewardsClaimed = BigInteger.ZERO,
                owner = ev.origin!!,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                version = 0,
                validatorNextCycle = nextCycle,
                validatorCycleLength = cycleLength,
                txId = ev.txId,
            )

        delegations[delegationId] = newDelegation
    }

    private fun handleDelegationExitRequested(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        delegations[delegationId]?.let { d ->
            if (d.status == Status.EXITED) return@let
            delegations[delegationId] =
                d.copy(
                    status = Status.EXITING,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    validatorNextCycle =
                        resolveNextCycleBlock(
                            d.validatorNextCycle,
                            d.validatorCycleLength,
                            block.number,
                        ),
                    version = d.version + 1,
                )
        }
    }

    private fun handleDelegationWithdrawn(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationID")!!
        delegations[delegationId]?.let { d ->
            delegations[delegationId] =
                d.copy(
                    status = Status.EXITED,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = d.version + 1,
                )
        }
    }

    private fun handleDelegationRewardsClaimed(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        block: Block,
    ) {
        val delegationId = ev.params.getAsString("delegationId")!!
        val amount = ev.params.getAsBigInteger("amount")!!
        delegations[delegationId]?.let { d ->
            delegations[delegationId] =
                d.copy(
                    totalRewardsClaimed = d.totalRewardsClaimed + amount,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    blockId = block.id,
                    version = d.version + 1,
                )
        }
    }

    private fun handleValidatorExitRequested(
        ev: IndexedEvent,
        delegations: MutableMap<String, Delegation>,
        block: Block,
    ) {
        val validatorId = ev.params.getAsString("validator")!!
        val exitAt = getExitBlock(validatorId)

        delegations.values
            .filter { it.validator == validatorId && it.status != Status.EXITED }
            .forEach { d ->
                if (d.status == Status.EXITING) return@forEach
                delegations[d.id] =
                    d.copy(
                        status = Status.EXITING,
                        validatorNextCycle = exitAt,
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        version = d.version + 1,
                    )
            }
    }

    private fun getDelegationsAbiFunctions(name: String): AbiElement =
        cachedGetDelegationAbi[name]
            ?: run {
                val abis = AbiLoader.loadFunctions("abis/stargate", listOf(name))
                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException("Function '$name' not found in ABI")
                cachedGetDelegationAbi[name] = abi
                abi
            }
}
