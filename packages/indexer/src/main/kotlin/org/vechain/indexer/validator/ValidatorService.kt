package org.vechain.indexer.validator

import java.math.BigInteger
import kotlin.collections.set
import kotlin.random.Random
import org.bson.types.Decimal128
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsLong
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
class ValidatorService(
    private val repository: ValidatorRepository,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
    private val contractAddress: String,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    fun refreshValidators(blockId: String, blockNumber: Long, blockTimestamp: Long) {
        // 1. Call chain
        val responses = thorService.executeReadOnlyCode(buildClauses())

        val decodedValidators =
            FunctionReturnDecoder.decode(
                responses[0].data,
                getValidatorsAbiFunctions("getValidators").outputs,
            )

        val decodedTotalStake =
            FunctionReturnDecoder.decode(
                responses[1].data,
                getValidatorsAbiFunctions("totalStake").outputs,
            )
        val totalWeight = decodedTotalStake["totalWeight"] as BigInteger

        val decodedTotalSupply =
            FunctionReturnDecoder.decode(
                responses[2].data,
                getValidatorsAbiFunctions("vthoTotalSupply").outputs,
            )
        val vthoTotalSupply = decodedTotalSupply["vthoTotalSupply"] as BigInteger

        val decodedVetPriceUsd =
            FunctionReturnDecoder.decode(
                responses[3].data,
                getValidatorsAbiFunctions("getVetPriceUsd").outputs,
            )
        val vetPriceUsd = decodedVetPriceUsd["vetPriceUsd"] as BigInteger

        val decodedVthoPriceUsd =
            FunctionReturnDecoder.decode(
                responses[4].data,
                getValidatorsAbiFunctions("getVthoPriceUsd").outputs,
            )
        val vthoPriceUsd = decodedVthoPriceUsd["vthoPriceUsd"] as BigInteger

        // 2. Fetch existing DB docs
        val existingDocs: Map<String, Validator> = repository.findAll().associateBy { it.id }

        // Get latest validators info
        val (updatedValidators, toDelete) =
            ValidatorUtils.unpackValidators(
                decodedValidators,
                existingDocs,
                totalWeight,
                vthoTotalSupply,
                vetPriceUsd,
                vthoPriceUsd,
                blockId,
                blockNumber,
                blockTimestamp,
            )

        // 4. Persist
        repository.saveAll(updatedValidators)

        // 5. Delete removed validators
        if (toDelete.isNotEmpty()) {
            repository.deleteAllById(toDelete)
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
            ContractUtils.createClause(contractAddress, getValidatorsAbiFunctions(fnName))
        }
    }

    // -------------------------------- Event Handling --------------------------------

    fun handleValidatorEvents(events: List<IndexedEvent>) {
        // global sort once
        val sorted =
            events.sortedWith(
                compareBy<IndexedEvent>(
                    { it.blockNumber },
                    {
                        when (it.eventType) {
                            "DelegationInitiated" -> 0
                            "DelegationAdded" -> 1
                            "DelegationWithdrawn" -> 2
                            else -> 3
                        }
                    },
                )
            )

        val validatorIds = sorted.mapNotNull { it.params.getAsString("validator") }.distinct()

        val delegationIds =
            sorted
                .mapNotNull {
                    it.params.getAsString("delegationId") ?: it.params.getAsString("delegationID")
                }
                .distinct()

        val validatorsById =
            repository.findAllById(validatorIds).associateBy { it.id }.toMutableMap()
        val validatorsByDelegation = repository.findByDelegationIdListIn(delegationIds)

        // merge delegation-sourced validators into the main map
        validatorsByDelegation.forEach { v -> validatorsById.putIfAbsent(v.id, v) }

        // also build delegation→validator lookup
        val delegationToValidator = mutableMapOf<String, String>()
        validatorsByDelegation.forEach { v ->
            v.delegationIds.keys.forEach { dId -> delegationToValidator[dId] = v.id }
        }
        // walk once in order
        sorted.forEach { ev ->
            when (EventUtils.determineValidatorEventType(ev.params)) {
                ValidatorAction.DELEGATION_INITIATED -> {
                    val validatorId = ev.params.getAsString("validator")!!
                    val validator = initiateDelegation(validatorsById[validatorId], ev)
                    validatorsById[validatorId] = validator

                    val dId =
                        ev.params.getAsString("delegationId")
                            ?: ev.params.getAsString("delegationID")!!
                    delegationToValidator[dId] = validatorId
                }

                ValidatorAction.DELEGATION_APPLIED -> {
                    val dId =
                        ev.params.getAsString("delegationId")
                            ?: ev.params.getAsString("delegationID")!!
                    val validatorId =
                        delegationToValidator[dId]
                            ?: ev.params.getAsString("validator") // fallback if event has validator
                            ?: throw IllegalStateException(
                                "no validator mapping yet for delegation $dId"
                            )
                    val updated = applyDelegation(requireNotNull(validatorsById[validatorId]), ev)
                    validatorsById[validatorId] = updated
                    delegationToValidator[dId] = validatorId
                }

                ValidatorAction.DELEGATION_REMOVED -> {
                    val dId =
                        ev.params.getAsString("delegationId")
                            ?: ev.params.getAsString("delegationID")!!
                    val validatorId =
                        delegationToValidator[dId]
                            ?: throw IllegalStateException(
                                "no validator mapping yet for delegation $dId"
                            )

                    val updated = removeDelegation(requireNotNull(validatorsById[validatorId]), ev)
                    validatorsById[validatorId] = updated
                }
                else -> {
                    println(
                        "Skipping unsupported event type for validator processing: ${ev.eventType}"
                    )
                }
            }
        }

        repository.saveAll(validatorsById.values)
    }

    fun applyDelegation(existing: Validator, event: IndexedEvent): Validator {
        val delegationId = event.params.getAsLong("delegationID")!!.toString()

        val level = existing.delegationIds[delegationId] ?: TokenLevel.All

        // If already counted, skip increment
        val alreadyCounted = existing.delegations[level]?.let { it > 0 } ?: false

        val updatedDelegations =
            existing.delegations.toMutableMap().apply {
                if (!alreadyCounted) {
                    this[level] = (this[level] ?: 0L) + 1
                    this[TokenLevel.All] = (this[TokenLevel.All] ?: 0L) + 1
                }
            }

        return existing.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegations = updatedDelegations,
        )
    }

    private fun initiateDelegation(existing: Validator?, event: IndexedEvent): Validator {
        val id = event.params.getAsString("validator")!!
        val delegationId = event.params.getAsLong("delegationId")!!

        // For testing, assign a random level between 2 and 10
        val tokenLevel = TokenLevel.fromOrdinal(Random.nextInt(2, 10))!!

        val base =
            existing
                ?: Validator(
                    id = id,
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    delegationIds = emptyMap(),
                    delegations = emptyMap(),
                    delegationIdList = emptyList(),
                    totalVTHOSupply = Decimal128(1),
                )

        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegationIds = base.delegationIds + (delegationId.toString() to tokenLevel),
            delegationIdList = base.delegationIdList + delegationId.toString(),
        )
    }

    fun removeDelegation(existing: Validator, event: IndexedEvent): Validator {
        val delegationId = event.params.getAsLong("delegationID")!!.toString()

        // Look up level from delegationIds (must have been queued first so it exists)
        val level = existing.delegationIds[delegationId] ?: TokenLevel.All

        // Update delegations map
        val updatedDelegations =
            existing.delegations.toMutableMap().apply {
                this[level] = this[level]!! - 1
                this[TokenLevel.All] = this[TokenLevel.All]!! - 1
            }

        // Remove delegationId from map
        val updatedDelegationIds = existing.delegationIds - delegationId

        return existing.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegations = updatedDelegations,
            delegationIds = updatedDelegationIds,
        )
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
