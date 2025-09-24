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
    ): Validator =
        this.validators[validatorId]
            ?: Validator(
                id = validatorId,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                totalVTHOSupply = Decimal128(1),
                version = 0,
                nextCycleBlock = 0L,
            )

    fun initiateDelegation(
        context: ValidatorCycleContext,
        validatorId: String,
        event: IndexedEvent,
    ): ValidatorCycleContext {
        val delegationId = extractDelegationId(event)
        val levelId = event.params.getAsLong("levelId")!!
        val tokenLevel = TokenLevel.fromOrdinal(levelId.toInt())!!

        val base = context.getOrCreateValidator(validatorId, event)

        val updated =
            base.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                delegationIdList = base.delegationIdList + delegationId,
                delegationsToBeActioned = base.delegationsToBeActioned + delegationId,
                delegationInfo =
                    base.delegationInfo + (delegationId to (tokenLevel to Status.QUEUED)),
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

        val (level, _) = validator.delegationInfo[delegationId] ?: (TokenLevel.All to Status.QUEUED)

        val updated =
            validator.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                delegationsToBeActioned = validator.delegationsToBeActioned + delegationId,
                delegationInfo =
                    validator.delegationInfo + (delegationId to (level to Status.EXITING)),
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

        val (level, _) = validator.delegationInfo[delegationId] ?: return context

        val updatedDelegations =
            validator.delegations.toMutableMap().apply {
                this[level] = maxOf((this[level] ?: 1L) - 1, 0L)
                this[TokenLevel.All] = maxOf((this[TokenLevel.All] ?: 1L) - 1, 0L)
            }

        val updated =
            validator.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                delegations = updatedDelegations,
                delegationInfo = validator.delegationInfo - delegationId,
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

        var ctx = context
        events.forEach { ev ->
            ctx =
                when (EventUtils.determineValidatorEventType(ev.params)) {
                    ValidatorAction.DELEGATION_INITIATED -> {
                        val validatorId = ev.params.getAsString("validator")!!
                        val newCtx = initiateDelegation(ctx, validatorId, ev)
                        delegationToValidator[extractDelegationId(ev)] = validatorId
                        newCtx
                    }
                    ValidatorAction.DELEGATION_EXIT_REQUESTED -> {
                        val validatorId = ev.params.getAsString("validator")!!
                        val newCtx = requestExitDelegation(ctx, validatorId, ev)
                        delegationToValidator[extractDelegationId(ev)] = validatorId
                        newCtx
                    }
                    ValidatorAction.DELEGATION_REMOVED -> {
                        val dId = extractDelegationId(ev)
                        val validatorId =
                            delegationToValidator[dId]
                                ?: throw IllegalStateException(
                                    "No validator mapping yet for delegation $dId"
                                )
                        removeDelegation(ctx, validatorId, ev)
                    }
                    else -> {
                        logger.debug("Skipping unsupported validator event type: ${ev.eventType}")
                        ctx
                    }
                }
        }
        return ctx
    }
}
