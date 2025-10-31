package org.vechain.indexer.history

// TODO: Remove this when veworld have migrated to V2 History API
object HistoryUtils {
    private val oldToNew =
        mapOf(
            "STARGATE_DELEGATE" to "STARGATE_DELEGATE_LEGACY",
            "STARGATE_UNDELEGATE" to "STARGATE_UNDELEGATE_LEGACY",
            "STARGATE_CLAIM_REWARDS_BASE" to "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
            "STARGATE_CLAIM_REWARDS_DELEGATE" to "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
            "STARGATE_DELEGATION_REMOVED" to "STARGATE_DELEGATION_REMOVED_LEGACY",
        )

    private val newToOld =
        mapOf(
            HistoryEventName.STARGATE_DELEGATE_LEGACY to "STARGATE_DELEGATE",
            HistoryEventName.STARGATE_CLAIM_REWARDS_BASE_LEGACY to "STARGATE_CLAIM_REWARDS_BASE",
            HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY to
                "STARGATE_CLAIM_REWARDS_DELEGATE",
            HistoryEventName.STARGATE_UNDELEGATE_LEGACY to "STARGATE_UNDELEGATE",
        )

    fun mapInputToNew(names: List<String>?): List<String>? =
        names
            ?.filter { it != "STARGATE_DELEGATE_ONLY" } // remove unwanted names
            ?.map { oldToNew[it] ?: it }

    fun mapEnumToOldStringForLegacy(name: HistoryEventName): String = newToOld[name] ?: name.name
}
