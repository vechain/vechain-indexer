package org.vechain.indexer.validator.logic

import org.bson.types.Decimal128
import org.slf4j.LoggerFactory
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsLong
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorAction

/**
 * Contains mutation logic for applying validator-related events to the in-memory
 * ValidatorCycleContext.
 */
object DelegationEventMutations {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private fun extractDelegationId(event: IndexedEvent): String =
        event.params.getAsString("delegationId")
            ?: event.params.getAsString("delegationID")
            ?: throw IllegalStateException("delegationId/ delegationID missing in event")

    private fun ValidatorCycleContext.getOrCreateValidator(
        validatorId: String,
        event: IndexedEvent,
        context: ValidatorCycleContext,
    ): Validator {
        val (cycleLength, currentCycleEnd) =
            context.resolveNextCycle(validatorId, context.blockNumber)
        return this.validators[validatorId]
            ?: Validator(
                id = validatorId,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                totalVTHOSupply = Decimal128(1),
                version = 0,
                cycleEndBlock = currentCycleEnd,
                cyclePeriodLength = cycleLength,
            )
    }

    fun initiateDelegation(
        context: ValidatorCycleContext,
        validatorId: String,
        event: IndexedEvent,
    ): ValidatorCycleContext {
        val delegationId = extractDelegationId(event)
        val levelId = event.params.getAsLong("levelId")!!
        val tokenLevel = TokenLevel.fromOrdinal(levelId.toInt())!!

        val base = context.getOrCreateValidator(validatorId, event, context)

        val updated =
            base.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                delegationsToBeActioned = base.delegationsToBeActioned + delegationId,
                delegationInfo =
                    base.delegationInfo + (delegationId to (tokenLevel to Status.QUEUED)),
                cycleEndBlock =
                    resolveNextCycleBlock(
                        base.cycleEndBlock,
                        base.cyclePeriodLength,
                        context.blockNumber,
                    ),
                incomingDelegations =
                    EventUtils.incrementCounts(base.incomingDelegations, tokenLevel),
            )

        context.put(updated)
        return context
    }

    fun requestExitDelegation(
        context: ValidatorCycleContext,
        validatorId: String,
        event: IndexedEvent,
    ): ValidatorCycleContext {
        val validator = context.requireValidator(validatorId)
        val delegationId = extractDelegationId(event)

        val (level, state) =
            validator.delegationInfo[delegationId] ?: (TokenLevel.All to Status.QUEUED)

        var status = Status.EXITING
        val updatedIncomingDelegations =
            when (state) {
                Status.QUEUED -> {
                    status = Status.LEAVING_QUE
                    EventUtils.decrementCounts(validator.incomingDelegations, level)
                }
                else -> {
                    validator.incomingDelegations
                }
            }

        val updated =
            validator.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                delegationsToBeActioned = validator.delegationsToBeActioned + delegationId,
                delegationInfo = validator.delegationInfo + (delegationId to (level to status)),
                cycleEndBlock =
                    resolveNextCycleBlock(
                        validator.cycleEndBlock,
                        validator.cyclePeriodLength,
                        context.blockNumber,
                    ),
                incomingDelegations = updatedIncomingDelegations,
                outgoingDelegations =
                    EventUtils.incrementCounts(validator.outgoingDelegations, level),
            )

        context.put(updated)
        return context
    }

    fun removeDelegation(
        context: ValidatorCycleContext,
        validatorId: String,
        event: IndexedEvent,
    ): ValidatorCycleContext {
        val validator = context.requireValidator(validatorId)
        val delegationId = extractDelegationId(event)
        val (level, state) = validator.delegationInfo[delegationId] ?: return context

        val updatedDelegations: Map<TokenLevel, Long>
        val updatedOutgoingDelegations: Map<TokenLevel, Long>
        val updatedIncomingDelegations: Map<TokenLevel, Long>
        when (state) {
            Status.QUEUED -> {
                updatedDelegations = validator.delegations
                updatedOutgoingDelegations = validator.outgoingDelegations
                updatedIncomingDelegations =
                    EventUtils.decrementCounts(validator.incomingDelegations, level)
            }
            Status.LEAVING_QUE -> {
                updatedDelegations = validator.delegations
                updatedOutgoingDelegations =
                    EventUtils.decrementCounts(validator.outgoingDelegations, level)
                updatedIncomingDelegations = validator.incomingDelegations
            }
            Status.EXITING -> {
                updatedDelegations = EventUtils.decrementCounts(validator.delegations, level)
                updatedOutgoingDelegations =
                    EventUtils.decrementCounts(validator.outgoingDelegations, level)
                updatedIncomingDelegations = validator.incomingDelegations
            }
            else -> {
                updatedDelegations = EventUtils.decrementCounts(validator.delegations, level)
                updatedOutgoingDelegations = validator.outgoingDelegations
                updatedIncomingDelegations = validator.incomingDelegations
            }
        }

        val updated =
            validator.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                delegations = updatedDelegations,
                outgoingDelegations = updatedOutgoingDelegations,
                incomingDelegations = updatedIncomingDelegations,
                delegationInfo = validator.delegationInfo - delegationId,
            )

        context.put(updated)
        return context
    }

    fun setBeneficiary(
        context: ValidatorCycleContext,
        validatorId: String,
        event: IndexedEvent,
    ): ValidatorCycleContext {
        val beneficiary = event.params.getAsString("beneficiary")!!
        val base = context.getOrCreateValidator(validatorId, event, context)
        val updated =
            base.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                beneficiary = beneficiary,
            )
        context.put(updated)
        return context
    }

    /** Apply a whole list of events in sequence to the context. */
    fun applyEvents(
        context: ValidatorCycleContext,
        events: List<IndexedEvent>,
    ): ValidatorCycleContext {
        val delegationToValidator = mutableMapOf<String, String>()

        // build initial delegation → validator map
        context.validators.forEach { (validatorId, v) ->
            v.delegationInfo.keys.forEach { dId -> delegationToValidator[dId] = validatorId }
        }

        val sortedEvents =
            events.sortedWith(
                compareBy<IndexedEvent> {
                    when (it.eventType) {
                        "DelegationInitiated" -> 0
                        "DelegationExitRequested" -> 1
                        "DelegationWithdrawn" -> 2
                        else -> 3
                    }
                }
            )

        var ctx = context
        sortedEvents.forEach { ev ->
            ctx =
                when (EventUtils.determineValidatorEventType(ev.params)) {
                    ValidatorAction.DELEGATION_INITIATED -> {
                        val validatorId = ev.params.getAsString("validator")!!
                        val newCtx = initiateDelegation(ctx, validatorId, ev)
                        delegationToValidator[extractDelegationId(ev)] = validatorId
                        newCtx
                    }
                    ValidatorAction.DELEGATION_EXIT_REQUESTED -> {
                        val dId = extractDelegationId(ev)
                        val validatorId =
                            delegationToValidator[dId]
                                ?: throw IllegalStateException(
                                    "No validator mapping yet for delegation $dId"
                                )
                        val newCtx = requestExitDelegation(ctx, validatorId, ev)
                        delegationToValidator[extractDelegationId(ev)] = validatorId
                        newCtx
                    }
                    ValidatorAction.DELEGATION_REMOVED -> {
                        val dId = extractDelegationId(ev)
                        val validatorId = delegationToValidator[dId] ?: return ctx
                        val newCtx = removeDelegation(ctx, validatorId, ev)
                        newCtx
                    }
                    ValidatorAction.BENIFICIARY_SET -> {
                        val validatorId = ev.params.getAsString("validator")!!
                        val newCtx = setBeneficiary(ctx, validatorId, ev)
                        newCtx
                    }
                    else -> {
                        logger.debug("Skipping unsupported validator event type: ${ev.eventType}")
                        ctx
                    }
                }
        }
        return ctx
    }

    fun resolveNextCycleBlock(lastCycleEnd: Long?, cycleLength: Long, currentBlock: Long): Long {
        val base = lastCycleEnd ?: currentBlock
        return if (base > currentBlock) {
            base // already ahead
        } else {
            base + ((currentBlock - base) / cycleLength + 1) * cycleLength
        }
    }
}
