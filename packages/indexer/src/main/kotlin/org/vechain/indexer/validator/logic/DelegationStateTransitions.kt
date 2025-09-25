package org.vechain.indexer.validator.logic

import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator

object DelegationStateTransitions {
    fun handleBlockUpdatesInContext(context: ValidatorCycleContext): ValidatorCycleContext {
        val toUpdate =
            context.validators.values.filter {
                it.delegationsToBeActioned.isNotEmpty() && it.cycleEndBlock == context.blockNumber
            }

        if (toUpdate.isEmpty()) return context

        toUpdate.forEach { validator ->
            var updated = validator

            validator.delegationsToBeActioned.forEach { delegationId ->
                val (level, status) = validator.delegationInfo[delegationId] ?: return@forEach

                updated =
                    when (status) {
                        Status.QUEUED -> applyDelegation(updated, delegationId, level)
                        Status.LEAVING_QUE,
                        Status.EXITING -> removeDelegation(updated, delegationId, level, status)
                        else -> updated // ACTIVE stays untouched
                    }
            }

            // Clear delegationsToBeActioned after applying
            updated =
                updated.copy(
                    delegationsToBeActioned = emptyList(),
                    cycleEndBlock = context.blockNumber + updated.cyclePeriodLength,
                )
            context.put(updated)
        }
        return context
    }

    private fun applyDelegation(
        validator: Validator,
        delegationId: String,
        level: TokenLevel,
    ): Validator {
        val updatedDelegationInfo =
            validator.delegationInfo.toMutableMap().apply {
                this[delegationId] = (level to Status.ACTIVE)
            }

        return validator.copy(
            delegations = EventUtils.incrementCounts(validator.delegations, level),
            incomingDelegations = EventUtils.decrementCounts(validator.incomingDelegations, level),
            delegationInfo = updatedDelegationInfo,
        )
    }

    private fun removeDelegation(
        validator: Validator,
        delegationId: String,
        level: TokenLevel,
        status: Status,
    ): Validator {
        val updatedDelegationInfo = validator.delegationInfo - delegationId

        val updatedDelegations = validator.delegations.toMutableMap()
        if (status != Status.LEAVING_QUE) EventUtils.decrementCounts(validator.delegations, level)

        return validator.copy(
            delegations = updatedDelegations,
            outgoingDelegations = EventUtils.decrementCounts(validator.outgoingDelegations, level),
            delegationInfo = updatedDelegationInfo,
        )
    }
}
