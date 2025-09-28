package org.vechain.indexer.validator.logic

import org.bson.types.Decimal128
import org.slf4j.LoggerFactory
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsLong
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorAction

/**
 * Contains mutation logic for applying validator-related events to the in-memory
 * ValidatorCycleContext.
 */
object DelegationEventMutations {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun initiateDelegation(
        context: DelegationContext,
        validatorId: String,
        event: IndexedEvent,
    ): DelegationContext {
        val delegationId = event.params.getAsString("delegationId")!!
        val levelId = event.params.getAsLong("levelId")!!
        val tokenLevel = TokenLevel.fromOrdinal(levelId.toInt())!!
        val tokenId = event.params.getAsString("tokenId")!!
        val vetStaked = event.params.getAsString("amount")!!

        val (cycleLength, currentCycleEnd) =
            context.resolveNextCycle(delegationId, context.blockNumber)

        val delegation =
            Delegation(
                id = delegationId,
                validator = validatorId,
                tokenId = tokenId,
                tokenLevel = tokenLevel,
                status = Status.QUEUED,
                stakedAmount = vetStaked,
                totalRewardsClaimed = Decimal128(0),
                owner = event.origin!!,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                version = 0,
                validatorNextCycle = currentCycleEnd,
                validatorCycleLength = cycleLength,
                txId = event.txId,
            )

        context.put(delegation)
        return context
    }

    fun requestExitDelegation(
        context: DelegationContext,
        delegationId: String,
        event: IndexedEvent,
    ): DelegationContext {
        val delegation = context.requireDelegation(delegationId)

        val updated =
            delegation.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                status = Status.EXITING,
                validatorNextCycle =
                    resolveNextCycleBlock(
                        delegation.validatorNextCycle,
                        delegation.validatorCycleLength,
                        event.blockNumber,
                    ),
            )

        context.put(updated)
        return context
    }

    fun validatorRequestedExit(
        context: DelegationContext,
        delegationId: String,
        event: IndexedEvent,
    ): DelegationContext {
        val delegation = context.requireDelegation(delegationId)

        val updated =
            delegation.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                status = Status.EXITING,
                validatorNextCycle = context.resolveValidatorExitBlock(delegation.validator),
            )

        context.put(updated)
        return context
    }

    fun removeDelegation(
        context: DelegationContext,
        delegationId: String,
        event: IndexedEvent,
    ): DelegationContext {
        val delegation = context.requireDelegation(delegationId)

        val updated =
            delegation.copy(
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                status = Status.EXITED,
            )

        context.put(updated)
        return context
    }

    /** Apply a whole list of events in sequence to the context. */
    fun applyEvents(context: DelegationContext, events: List<IndexedEvent>): DelegationContext {
        val delegationToValidator = mutableMapOf<String, String>()

        // build initial delegation → validator map
        context.delegations.forEach { (id, v) ->
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
                    ValidatorAction.VALIDATOR_EXIT_REQUESTED -> {
                        val validatorId = ev.params.getAsString("validator")!!
                        val newCtx = removeDelegation(ctx, validatorId, ev)
                        newCtx
                    }
                    ValidatorAction.DELEGATION_TRANSFERRED -> {
                        val validatorId = ev.params.getAsString("validator")!!
                        val newCtx = removeDelegation(ctx, validatorId, ev)
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
