package org.vechain.indexer.validator

import java.math.BigInteger
import kotlin.collections.set
import kotlin.random.Random
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

        val decodedStakerBalance =
            FunctionReturnDecoder.decode(
                responses[1].data,
                getValidatorsAbiFunctions("stakerBalance").outputs,
            )
        val stakerBalance = decodedStakerBalance["stakerBalance"] as BigInteger

        val decodedTotalStake =
            FunctionReturnDecoder.decode(
                responses[2].data,
                getValidatorsAbiFunctions("totalStake").outputs,
            )
        val totalStake = decodedTotalStake["totalStake"] as BigInteger
        val totalWeight = decodedTotalStake["totalWeight"] as BigInteger

        val decodedQueuedStake =
            FunctionReturnDecoder.decode(
                responses[3].data,
                getValidatorsAbiFunctions("queuedStake").outputs,
            )
        val queuedStake = decodedQueuedStake["queuedStake"] as BigInteger
        /*        val decodedGetBalance = FunctionReturnDecoder.decode(responses[4].data, getValidatorsAbiFunctions("getBalance").outputs)
        val getBalance = decodedGetBalance["balance"] as BigInteger*/

        val decodedTotalSupply =
            FunctionReturnDecoder.decode(
                responses[4].data,
                getValidatorsAbiFunctions("vthoTotalSupply").outputs,
            )
        val vthoTotalSupply = decodedTotalSupply["vthoTotalSupply"] as BigInteger

        val decodedTotalBurned =
            FunctionReturnDecoder.decode(
                responses[5].data,
                getValidatorsAbiFunctions("totalBurned").outputs,
            )
        val totalBurned = decodedTotalBurned["totalBurned"] as BigInteger

        // 2. Fetch existing DB docs
        val existingDocs: Map<String, Validator> = repository.findAll().associateBy { it.id }

        // Get latest validators info
        val latestValidators =
            unpackValidators(
                decodedValidators,
                existingDocs,
                stakerBalance,
                totalStake,
                totalWeight,
                queuedStake,
                vthoTotalSupply,
                totalBurned,
                blockId,
                blockNumber,
                blockTimestamp,
            )

        // 4. Persist
        repository.saveAll(latestValidators)
    }

    fun buildClauses(): List<Clause> {
        val functionNames =
            listOf(
                "getValidators",
                "stakerBalance",
                "totalStake",
                "queuedStake",
                "vthoTotalSupply",
                "totalBurned",
            )

        return functionNames.map { fnName ->
            ContractUtils.createClause(contractAddress, getValidatorsAbiFunctions(fnName))
        }
    }

    // -------------------------------- Event Handling --------------------------------

    fun handleValidatorEvents(events: List<IndexedEvent>) {
        val sortedEvents =
            events.sortedWith(
                compareBy<IndexedEvent>(
                    { it.blockNumber },
                    { it.clauseIndex },
                    { event ->
                        when (event.eventType) {
                            "DelegationInitiated" -> 0
                            "DelegationAdded" -> 1
                            "DelegationWithdrawn" -> 2
                            else -> 3
                        }
                    },
                )
            )

        // Group by validator ID to handle multiple events for the same validator
        val groupedByDelegation =
            sortedEvents.groupBy {
                it.params.getAsString("delegationId") ?: it.params.getAsString("delegationID")
            }

        println("Grouped by validator: $groupedByDelegation")

        // Process per validator
        groupedByDelegation.forEach { (delegationId, validatorEvents) ->
            if (delegationId == null) return@forEach

            var validator = repository.findById(delegationId).orElse(null)

            validatorEvents.forEach { event ->
                when (EventUtils.determineValidatorEventType(event.params)) {
                    ValidatorAction.DELEGATION_INITIATED -> {
                        println("DELEGATION_INITIATED")
                        validator = initiateDelegation(validator, event)
                    }
                    ValidatorAction.DELEGATION_APPLIED -> {
                        println("DELEGATION_APPLIED")
                        validator = applyDelegation(validator, event)
                    }
                    ValidatorAction.DELEGATION_REMOVED -> {
                        println("DELEGATION_REMOVED")
                        validator = removeDelegation(validator, event)
                    }
                    // add more cases as you add event mappings
                    else -> {}
                }
            }

            // Persist only once at the end
            if (validator != null) {
                repository.save(validator)
            }
        }
    }

    fun applyDelegation(existing: Validator, event: IndexedEvent): Validator {
        println("Applying delegation for event: $event")
        println("existing: $existing")

        val delegationId = event.params.getAsLong("delegationID")!!.toString()

        println("delegation id: $delegationId")

        // Look up level from delegationIds (must have been queued first so it exists)
        val level = existing.delegationIds[delegationId] ?: TokenLevel.None

        // Update delegations map
        val updatedDelegations =
            existing.delegations.toMutableMap().apply { this[level] = (this[level] ?: 0L) + 1 }

        return existing.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegations = updatedDelegations,
        )
    }

    private fun initiateDelegation(existing: Validator?, event: IndexedEvent): Validator {
        val id = event.params.getAsString("validator")!!
        println("Initiating delegation for validator ID: $id")
        val delegationId = event.params.getAsLong("delegationId")!!

        /*val tokenLevel =
                   event.params
                       .getAsInt("level")
                       ?.let { TokenLevel.fromOrdinal(it) }!!
        */
        // TODO: Update when levelId is incldued in event
        val randomLevel = Random.nextInt(2, 10) // upper bound is exclusive, so 10 → gives 2..9
        val tokenLevel = TokenLevel.fromOrdinal(randomLevel)!!

        val base =
            existing
                ?: Validator(
                    id = id,
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    delegationIds = emptyMap(),
                    delegations = emptyMap(),
                    totalVTHOSupply = 1.toBigDecimal(),
                )

        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegationIds = base.delegationIds + (delegationId.toString() to tokenLevel),
        )
    }

    fun removeDelegation(existing: Validator, event: IndexedEvent): Validator {
        val delegationId = event.params.getAsLong("delegationID")!!.toString()

        // Look up level from delegationIds (must have been queued first so it exists)
        val level = existing.delegationIds[delegationId] ?: TokenLevel.None

        // Update delegations map
        val updatedDelegations =
            existing.delegations.toMutableMap().apply { this[level] = this[level]!! - 1 }

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
