package org.vechain.indexer.vevote

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("vevote", "vevote-historic-proposals")
@Repository
open class PostgresHistoricProposalsRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : HistoricProposalsRepository {

    private fun tableName(): String = "historic_proposals"

    private fun mapRow(rs: ResultSet): HistoricProposals {
        val choicesJson = rs.getString("choices")
        val choices: List<String>? =
            choicesJson?.let {
                objectMapper.readValue(it, object : TypeReference<List<String>>() {})
            }

        val voteTalliesJson = rs.getString("vote_tallies")
        val voteTallies: List<Long>? =
            voteTalliesJson?.let {
                objectMapper.readValue(it, object : TypeReference<List<Long>>() {})
            }

        return HistoricProposals(
            id = rs.getString("id"),
            proposalId = rs.getString("proposal_id"),
            contractAddress = rs.getString("contract_address"),
            createdDate = rs.getString("created_date"),
            proposer = rs.getString("proposer"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            proposalType = rs.getObject("proposal_type") as? Int,
            choices = choices,
            test = rs.getBoolean("test"),
            createTime = rs.getObject("create_time") as? Long,
            votingStartTime = rs.getObject("voting_start_time") as? Long,
            votingEndTime = rs.getObject("voting_end_time") as? Long,
            voteTallies = voteTallies,
            totalVotes = rs.getObject("total_votes") as? Long,
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
    }

    private fun insertColumns(): String =
        """
        id, block_id, block_number, block_timestamp, proposal_id, contract_address, created_date,
        proposer, title, description, proposal_type, choices, test, create_time, voting_start_time,
        voting_end_time, vote_tallies, total_votes
        """
            .trimIndent()

    private fun insertPlaceholders(): String =
        "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?"

    private fun insertParams(proposal: HistoricProposals): Array<Any?> =
        arrayOf(
            proposal.id,
            proposal.blockId,
            proposal.blockNumber,
            proposal.blockTimestamp,
            proposal.proposalId,
            proposal.contractAddress,
            proposal.createdDate,
            proposal.proposer,
            proposal.title,
            proposal.description,
            proposal.proposalType,
            proposal.choices?.let { objectMapper.writeValueAsString(it) },
            proposal.test,
            proposal.createTime,
            proposal.votingStartTime,
            proposal.votingEndTime,
            proposal.voteTallies?.let { objectMapper.writeValueAsString(it) },
            proposal.totalVotes,
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(proposals: List<HistoricProposals>) {
        if (proposals.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (id) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_number = EXCLUDED.block_number,
                block_timestamp = EXCLUDED.block_timestamp,
                proposal_id = EXCLUDED.proposal_id,
                contract_address = EXCLUDED.contract_address,
                created_date = EXCLUDED.created_date,
                proposer = EXCLUDED.proposer,
                title = EXCLUDED.title,
                description = EXCLUDED.description,
                proposal_type = EXCLUDED.proposal_type,
                choices = EXCLUDED.choices,
                test = EXCLUDED.test,
                create_time = EXCLUDED.create_time,
                voting_start_time = EXCLUDED.voting_start_time,
                voting_end_time = EXCLUDED.voting_end_time,
                vote_tallies = EXCLUDED.vote_tallies,
                total_votes = EXCLUDED.total_votes
            """
                .trimIndent(),
            proposals.map { insertParams(it) },
        )
    }

    override fun findById(id: String): HistoricProposals? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE id = ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                id,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findAllById(ids: List<String>): List<HistoricProposals> {
        if (ids.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE id IN (:ids)
            """
                .trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateVoteTallies(id: String, voteTallies: List<Long>, totalVotes: Long) {
        jdbcTemplate.update(
            """
            UPDATE ${tableName()}
            SET vote_tallies = ?::jsonb, total_votes = ?
            WHERE id = ?
            """
                .trimIndent(),
            objectMapper.writeValueAsString(voteTallies),
            totalVotes,
            id,
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

    private fun queryWithPagination(
        whereClause: String,
        pageable: Pageable,
        vararg params: Any?,
    ): Slice<HistoricProposals> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                *params,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAll(pageable: Pageable): Slice<HistoricProposals> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<HistoricProposals> {
        return queryWithPagination("proposal_id = ?", pageable, proposalId)
    }

    override fun findByContractAddress(
        contractAddress: String,
        pageable: Pageable,
    ): Slice<HistoricProposals> {
        return queryWithPagination("contract_address = ?", pageable, contractAddress)
    }

    override fun findByContractAddressAndTest(
        contractAddress: String,
        test: Boolean,
        pageable: Pageable,
    ): Slice<HistoricProposals> {
        return queryWithPagination(
            "contract_address = ? AND test = ?",
            pageable,
            contractAddress,
            test,
        )
    }

    override fun findByTest(test: Boolean, pageable: Pageable): Slice<HistoricProposals> {
        return queryWithPagination("test = ?", pageable, test)
    }
}
