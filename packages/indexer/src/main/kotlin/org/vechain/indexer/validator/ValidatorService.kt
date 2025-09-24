package org.vechain.indexer.validator

import java.math.BigInteger
import kotlin.collections.set
import org.bson.types.Decimal128
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.archive.ArchiveService
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
import org.vechain.indexer.validator.utils.ValidatorUtils

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

        // 6. Archive
        if (existingDocs.isNotEmpty()) {
            archiveService.saveAll(existingDocs.values.toList())
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

    // -------------------------------- Event Handling --------------------------------

    fun handleBlockUpdates(blockNumber: Long) {
        val validators = repository.findByBlockNumberAndDelegationsToBeActionedNotEmpty(blockNumber)

        if (validators.isEmpty()) return
    }

    fun handleValidatorEvents(events: List<IndexedEvent>) {
        // global sort once
        val sorted =
            events.sortedWith(
                compareBy<IndexedEvent>(
                    { it.blockNumber },
                    {
                        when (it.eventType) {
                            "DelegationInitiated" -> 0
                            "DelegationExitRequested" -> 1
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

        val validators = repository.findByIdsOrDelegations(validatorIds, delegationIds)

        // main lookup by id
        val validatorsById = validators.associateBy { it.id }.toMutableMap()

        // delegation → validator lookup
        val delegationToValidator = mutableMapOf<String, String>()
        validators.forEach { v ->
            v.delegationInfo.keys.forEach { dId -> delegationToValidator[dId] = v.id }
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

                ValidatorAction.DELEGATION_EXIT_REQUESTED -> {
                    val validatorId = ev.params.getAsString("validator")!!
                    val validator = initiateDelegation(validatorsById[validatorId], ev)
                    validatorsById[validatorId] = validator

                    val dId =
                        ev.params.getAsString("delegationId")
                            ?: ev.params.getAsString("delegationID")!!
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

        val toSave = validatorsById.values.map { v -> v.copy(version = v.version + 1) }

        repository.saveAll(toSave)

        if (validators.isNotEmpty()) {
            archiveService.saveAll(validators)
        }
    }

    fun applyDelegation(existing: Validator, event: IndexedEvent): Validator {
        val delegationId = event.params.getAsLong("delegationID")!!.toString()

        // lookup token level from delegationInfo
        val (level, _) = existing.delegationInfo[delegationId] ?: (TokenLevel.All to Status.QUEUED)

        val updatedDelegations =
            existing.delegations.toMutableMap().apply {
                this[level] = (this[level] ?: 0L) + 1
                this[TokenLevel.All] = (this[TokenLevel.All] ?: 0L) + 1
            }

        val updatedDelegationInfo =
            existing.delegationInfo.toMutableMap().apply {
                this[delegationId] = (level to Status.ACTIVE)
            }

        return existing.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegations = updatedDelegations,
            delegationInfo = updatedDelegationInfo,
        )
    }

    private fun initiateDelegation(existing: Validator?, event: IndexedEvent): Validator {
        val id = event.params.getAsString("validator")!!
        val delegationId = event.params.getAsLong("delegationId")!!

        // Get level info
        val levelId = event.params.getAsLong("levelId")!!
        val tokenLevel = TokenLevel.fromOrdinal(levelId.toInt())!!

        val base =
            existing
                ?: Validator(
                    id = id,
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    totalVTHOSupply = Decimal128(1),
                    version = 0,
                    nextCycleBlock = getNextPeriodStartBlock(id),
                )

        return base.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegationIdList = base.delegationIdList + delegationId.toString(),
            delegationsToBeActioned = base.delegationsToBeActioned + delegationId.toString(),
            delegationInfo =
                base.delegationInfo + (delegationId.toString() to (tokenLevel to Status.QUEUED)),
        )
    }

    private fun requestExitDelegation(existing: Validator, event: IndexedEvent): Validator {
        val delegationId = event.params.getAsString("delegationId")!!

        // lookup token level from delegationInfo
        val (level, _) = existing.delegationInfo[delegationId] ?: (TokenLevel.All to Status.QUEUED)

        val updatedDelegationInfo =
            existing.delegationInfo.toMutableMap().apply {
                this[delegationId] = (level to Status.EXITING)
            }

        return existing.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegationsToBeActioned = existing.delegationsToBeActioned + delegationId,
            delegationInfo = updatedDelegationInfo,
        )
    }

    fun removeDelegation(existing: Validator, event: IndexedEvent): Validator {
        val delegationId = event.params.getAsLong("delegationID")!!.toString()

        // lookup token level from delegationInfo
        val (level, _) = existing.delegationInfo[delegationId] ?: (TokenLevel.All to Status.QUEUED)

        // Update delegations map
        val updatedDelegations =
            existing.delegations.toMutableMap().apply {
                this[level] = this[level]!! - 1
                this[TokenLevel.All] = this[TokenLevel.All]!! - 1
            }

        // Remove delegationId from map
        val updatedDelegationIds = existing.delegationInfo - delegationId

        return existing.copy(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            delegations = updatedDelegations,
            delegationInfo = updatedDelegationIds,
        )
    }

    private fun getNextPeriodStartBlock(validator: String): Long {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getValidatorsAbiFunctions("getValidationPeriodDetails"),
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))

        val decodedPeriodInfo =
            FunctionReturnDecoder.decode(
                response[0].data,
                getValidatorsAbiFunctions("getValidationPeriodDetails").outputs,
            )

        val startBlock = decodedPeriodInfo["startBlock"] as Long
        val periodLength = decodedPeriodInfo["period"] as Long
        val completedPeriods = decodedPeriodInfo["completedPeriods"] as Long

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
