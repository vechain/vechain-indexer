package org.vechain.indexer.vevote

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("vevote", "vevote-historic-proposals")
@Repository
open class PostgresHistoricProposalsVoteRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : HistoricProposalsVoteRepository {

    private fun tableName(): String = "historic_proposals_votes"

    private fun mapRow(rs: ResultSet): HistoricProposalsVote {
        val choicesJson = rs.getString("choices")
        val choices: List<Int> =
            objectMapper.readValue(choicesJson, object : TypeReference<List<Int>>() {})

        return HistoricProposalsVote(
            id = rs.getString("id"),
            proposalId = rs.getString("proposal_id"),
            contract = rs.getString("contract"),
            choices = choices,
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
    }

    private fun insertColumns(): String =
        """
        id, block_id, block_number, block_timestamp, proposal_id, contract, choices
        """
            .trimIndent()

    private fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?::jsonb"

    private fun insertParams(vote: HistoricProposalsVote): Array<Any?> =
        arrayOf(
            vote.id,
            vote.blockId,
            vote.blockNumber,
            vote.blockTimestamp,
            vote.proposalId,
            vote.contract,
            objectMapper.writeValueAsString(vote.choices),
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(votes: List<HistoricProposalsVote>) {
        if (votes.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (id) DO NOTHING
            """
                .trimIndent(),
            votes.map { insertParams(it) },
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update("DELETE FROM ${tableName()} WHERE block_number >= ?", blockNumber)
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                BlockIdentifier(number = rs.getLong("block_number"), id = rs.getString("block_id"))
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun getCollectionName(): String = tableName()
}
