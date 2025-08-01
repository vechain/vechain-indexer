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
                val optionsList = basicInfo?.get("options") as? List<*>
                val optionsArray = basicInfo?.get("options") as? Array<*>
                val options = optionsList ?: optionsArray

                when (options) {
                    is List<*> -> options.map { it.toString().trim { c -> c == '\u0000' } }
                    is Array<*> ->
                        options.toList().map { it.toString().trim { c -> c == '\u0000' } }
                    else -> null
                }
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
                val tallyList = tally?.get("tally") as? List<*>
                val tallyArray = tally?.get("tally") as? Array<*>
                val tallyData = tallyList ?: tallyArray

                when (tallyData) {
                    is List<*> -> tallyData.map { (it as? Number)?.toLong() ?: 0L }
                    is Array<*> -> tallyData.toList().map { (it as? Number)?.toLong() ?: 0L }
                    else -> null
                }
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
        steeringCommitteeAddress: String,
        allStakeholdersAddress: String,
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
