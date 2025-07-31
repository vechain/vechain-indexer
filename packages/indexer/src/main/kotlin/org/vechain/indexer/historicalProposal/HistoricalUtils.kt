package org.vechain.indexer.historicalProposal

object HistoricalUtils {

    fun extractChoices(
        basicInfo: Map<String, Any?>?,
        contractAddress: String,
        steeringCommitteeAddress: String,
        allStakeholdersAddress: String,
    ): List<String>? {
        return when (contractAddress.lowercase()) {
            steeringCommitteeAddress.lowercase() -> {
                val options = basicInfo?.get("options") as? Array<*>
                options?.map { it.toString().trim { c -> c == '\u0000' } }
            }
            allStakeholdersAddress.lowercase() -> {
                val options = basicInfo?.get("options") as? List<*>
                options?.map { it.toString() }
            }
            else -> null
        }
    }

    fun extractVoteTallies(
        tally: Map<String, Any?>?,
        contractAddress: String,
        steeringCommitteeAddress: String,
        allStakeholdersAddress: String,
    ): List<Long>? {
        return when (contractAddress.lowercase()) {
            steeringCommitteeAddress.lowercase() -> {
                val tallyArray = tally?.get("tally") as? Array<*>
                tallyArray?.map { (it as? Number)?.toLong() ?: 0L }
            }
            allStakeholdersAddress.lowercase() -> {
                val tallyList = tally?.get("tally") as? List<*>
                tallyList?.map { (it as? Number)?.toLong() ?: 0L }
            }
            else -> null
        }
    }

    fun calculateTotalVotes(
        tally: Map<String, Any?>?,
        contractAddress: String,
        allStakeholdersAddress: String,
        steeringCommitteeAddress: String,
    ): Long {
        val tallies =
            extractVoteTallies(
                tally,
                contractAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )
        return tallies?.sum() ?: 0L
    }
}
