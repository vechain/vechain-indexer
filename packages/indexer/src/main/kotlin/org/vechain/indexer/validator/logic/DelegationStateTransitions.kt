package org.vechain.indexer.validator.logic

import org.vechain.indexer.stargate.TokenLevel
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
                        Status.EXITING -> removeDelegation(updated, delegationId, level)
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
        val updatedDelegations =
            validator.delegations.toMutableMap().apply {
                this[level] = (this[level] ?: 0L) + 1
                this[TokenLevel.All] = (this[TokenLevel.All] ?: 0L) + 1
            }

        val updatedDelegationInfo =
            validator.delegationInfo.toMutableMap().apply {
                this[delegationId] = (level to Status.ACTIVE)
            }

        return validator.copy(
            delegations = updatedDelegations,
            delegationInfo = updatedDelegationInfo,
        )
    }

    private fun removeDelegation(
        validator: Validator,
        delegationId: String,
        level: TokenLevel,
    ): Validator {
        val updatedDelegations =
            validator.delegations.toMutableMap().apply {
                this[level] = (this[level] ?: 1L) - 1
                this[TokenLevel.All] = (this[TokenLevel.All] ?: 1L) - 1
            }

        val updatedDelegationInfo = validator.delegationInfo - delegationId

        return validator.copy(
            delegations = updatedDelegations,
            delegationInfo = updatedDelegationInfo,
        )
    }
}
