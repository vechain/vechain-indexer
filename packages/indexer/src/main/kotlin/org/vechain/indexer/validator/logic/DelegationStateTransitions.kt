package org.vechain.indexer.validator.logic

import org.vechain.indexer.validator.Status

object DelegationStateTransitions {
    fun handleBlockUpdatesInContext(context: DelegationContext): DelegationContext {
        val toUpdate =
            context.delegations.values.filter {
                eventToCheck(it.status) && it.validatorNextCycle == context.blockNumber
            }

        if (toUpdate.isEmpty()) return context

        toUpdate.forEach { delegation ->
            delegation.copy(
                status = determineStatus(delegation.status),
                notify = true,
                blockNumber = context.blockNumber,
                blockTimestamp = context.blockTimestamp,
                blockId = context.blockId,
            )
            context.put(delegation)
        }
        return context
    }

    private fun eventToCheck(status: Status): Boolean =
        (status == Status.QUEUED) || (status == Status.LEAVING_QUE) || (status == Status.EXITING)

    private fun determineStatus(status: Status): Status {
        if (status == Status.EXITING) return Status.EXITED
        return Status.ACTIVE
    }
}
