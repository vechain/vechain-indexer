package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Profile("vevote", "vevote-historic-proposals")
@Service
open class HistoricProposalTallyService(
    private val jdbcTemplate: JdbcTemplate,
    private val repository: HistoricProposalsRepository,
) {
    fun aggregateAllTallies(collectionName: String) {
        // Aggregate vote tallies from historic_proposals_votes table
        // Group by proposalId and contract, counting votes per choice
        val results =
            jdbcTemplate.query(
                """
                SELECT 
                    proposal_id,
                    contract,
                    choice,
                    COUNT(*) as vote_count
                FROM historic_proposals_votes,
                     LATERAL jsonb_array_elements_text(choices) AS choice
                GROUP BY proposal_id, contract, choice
                ORDER BY proposal_id, contract, choice
                """
                    .trimIndent()
            ) { rs, _ ->
                VoteTallyRow(
                    proposalId = rs.getString("proposal_id"),
                    contract = rs.getString("contract"),
                    choice = rs.getInt("choice"),
                    voteCount = rs.getLong("vote_count"),
                )
            }

        // Group results by composite id (contract-proposalId)
        val groupedResults = results.groupBy { "${it.contract}-${it.proposalId}" }

        groupedResults.forEach { (compositeId, rows) ->
            val proposalId = rows.first().proposalId
            val contract = rows.first().contract

            val talliesMap = rows.associate { it.choice to it.voteCount }

            val proposal = repository.findById(compositeId)

            val orderedTallies =
                proposal?.choices?.mapIndexed { idx, _ -> talliesMap[idx + 1] ?: 0L }
                    ?: (1..(talliesMap.keys.maxOrNull() ?: 0)).map { idx -> talliesMap[idx] ?: 0L }

            val totalVotes = rows.sumOf { it.voteCount }

            // Update or insert the proposal with vote tallies
            if (proposal != null) {
                repository.updateVoteTallies(compositeId, orderedTallies, totalVotes)
            } else {
                // Create a minimal proposal record if it doesn't exist
                repository.saveAll(
                    listOf(
                        HistoricProposals(
                            id = compositeId,
                            proposalId = proposalId,
                            contractAddress = contract,
                            createdDate = "",
                            proposer = null,
                            title = null,
                            description = null,
                            proposalType = null,
                            choices = null,
                            test = false,
                            createTime = null,
                            votingStartTime = null,
                            votingEndTime = null,
                            voteTallies = orderedTallies,
                            totalVotes = totalVotes,
                            blockId = "",
                            blockNumber = 0L,
                            blockTimestamp = 0L,
                        )
                    )
                )
            }
        }
    }
}

private data class VoteTallyRow(
    val proposalId: String,
    val contract: String,
    val choice: Int,
    val voteCount: Long,
)
