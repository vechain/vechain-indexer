package org.vechain.indexer.history

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
import org.vechain.indexer.b3tr.action.SustainabilityProofV2
import org.vechain.indexer.b3tr.voting.AppVote
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("history")
@Repository
open class PostgresHistoryRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : HistoryRepository {

    private fun tableName(): String = "history_events"

    private fun mapRow(rs: ResultSet): IndexedHistoryEvent {
        val proofJson = rs.getString("proof")
        val proof = proofJson?.let { objectMapper.readValue(it, SustainabilityProofV2::class.java) }

        val appVotesJson = rs.getString("app_votes")
        val appVotes =
            appVotesJson?.let {
                objectMapper.readValue(it, object : TypeReference<List<AppVote>>() {})
            }

        val tokenIdsJson = rs.getString("token_ids")
        val tokenIds =
            tokenIdsJson?.let {
                objectMapper.readValue(it, object : TypeReference<List<String>>() {})
            }

        val supportStr = rs.getString("support")
        val support = supportStr?.let { Support.valueOf(it) }

        return IndexedHistoryEvent(
            id = rs.getString("id"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            txId = rs.getString("tx_id"),
            origin = rs.getString("origin"),
            gasPayer = rs.getString("gas_payer"),
            reverted = rs.getObject("reverted") as? Boolean,
            contractAddress = rs.getString("contract_address"),
            tokenId = rs.getString("token_id"),
            eventName = HistoryEventName.valueOf(rs.getString("event_name")),
            to = rs.getString("to_address"),
            from = rs.getString("from_address"),
            value = rs.getString("value"),
            appId = rs.getString("app_id"),
            proof = proof,
            roundId = rs.getString("round_id"),
            appVotes = appVotes,
            support = support,
            votePower = rs.getString("vote_power"),
            voteWeight = rs.getString("vote_weight"),
            reason = rs.getString("reason"),
            proposalId = rs.getString("proposal_id"),
            oldLevel = rs.getString("old_level"),
            newLevel = rs.getString("new_level"),
            inputToken = rs.getString("input_token"),
            outputToken = rs.getString("output_token"),
            inputValue = rs.getString("input_value"),
            outputValue = rs.getString("output_value"),
            tokenAddress = rs.getString("token_address"),
            levelId = rs.getString("level_id"),
            owner = rs.getString("owner"),
            vetGeneratedVthoRewards = rs.getString("vet_generated_vtho_rewards"),
            delegationRewards = rs.getString("delegation_rewards"),
            migrated = rs.getObject("migrated") as? Boolean,
            autorenew = rs.getObject("autorenew") as? Boolean,
            tokenIds = tokenIds,
            validator = rs.getString("validator"),
            delegationId = rs.getString("delegation_id"),
            periodClaimed = rs.getObject("period_claimed") as? Long,
            boostedBlocks = rs.getString("boosted_blocks"),
            isBlacklisted = rs.getObject("is_blacklisted") as? Boolean,
        )
    }

    private fun insertColumns(): String =
        """
        id, block_id, block_number, block_timestamp, tx_id, origin, gas_payer, reverted,
        contract_address, token_id, event_name, to_address, from_address, value, app_id,
        proof, round_id, app_votes, support, vote_power, vote_weight, reason, proposal_id,
        old_level, new_level, input_token, output_token, input_value, output_value,
        token_address, level_id, owner, vet_generated_vtho_rewards, delegation_rewards,
        migrated, autorenew, token_ids, validator, delegation_id, period_claimed,
        boosted_blocks, is_blacklisted
        """
            .trimIndent()

    private fun insertPlaceholders(): String =
        "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?"

    private fun insertParams(event: IndexedHistoryEvent): Array<Any?> =
        arrayOf(
            event.id,
            event.blockId,
            event.blockNumber,
            event.blockTimestamp,
            event.txId,
            event.origin,
            event.gasPayer,
            event.reverted,
            event.contractAddress,
            event.tokenId,
            event.eventName.name,
            event.to,
            event.from,
            event.value,
            event.appId,
            event.proof?.let { objectMapper.writeValueAsString(it) },
            event.roundId,
            event.appVotes?.let { objectMapper.writeValueAsString(it) },
            event.support?.name,
            event.votePower,
            event.voteWeight,
            event.reason,
            event.proposalId,
            event.oldLevel,
            event.newLevel,
            event.inputToken,
            event.outputToken,
            event.inputValue,
            event.outputValue,
            event.tokenAddress,
            event.levelId,
            event.owner,
            event.vetGeneratedVthoRewards,
            event.delegationRewards,
            event.migrated,
            event.autorenew,
            event.tokenIds?.let { objectMapper.writeValueAsString(it) },
            event.validator,
            event.delegationId,
            event.periodClaimed,
            event.boostedBlocks,
            event.isBlacklisted,
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(events: List<IndexedHistoryEvent>) {
        if (events.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (id) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_number = EXCLUDED.block_number,
                block_timestamp = EXCLUDED.block_timestamp,
                tx_id = EXCLUDED.tx_id,
                origin = EXCLUDED.origin,
                gas_payer = EXCLUDED.gas_payer,
                reverted = EXCLUDED.reverted,
                contract_address = EXCLUDED.contract_address,
                token_id = EXCLUDED.token_id,
                event_name = EXCLUDED.event_name,
                to_address = EXCLUDED.to_address,
                from_address = EXCLUDED.from_address,
                value = EXCLUDED.value,
                app_id = EXCLUDED.app_id,
                proof = EXCLUDED.proof,
                round_id = EXCLUDED.round_id,
                app_votes = EXCLUDED.app_votes,
                support = EXCLUDED.support,
                vote_power = EXCLUDED.vote_power,
                vote_weight = EXCLUDED.vote_weight,
                reason = EXCLUDED.reason,
                proposal_id = EXCLUDED.proposal_id,
                old_level = EXCLUDED.old_level,
                new_level = EXCLUDED.new_level,
                input_token = EXCLUDED.input_token,
                output_token = EXCLUDED.output_token,
                input_value = EXCLUDED.input_value,
                output_value = EXCLUDED.output_value,
                token_address = EXCLUDED.token_address,
                level_id = EXCLUDED.level_id,
                owner = EXCLUDED.owner,
                vet_generated_vtho_rewards = EXCLUDED.vet_generated_vtho_rewards,
                delegation_rewards = EXCLUDED.delegation_rewards,
                migrated = EXCLUDED.migrated,
                autorenew = EXCLUDED.autorenew,
                token_ids = EXCLUDED.token_ids,
                validator = EXCLUDED.validator,
                delegation_id = EXCLUDED.delegation_id,
                period_claimed = EXCLUDED.period_claimed,
                boosted_blocks = EXCLUDED.boosted_blocks,
                is_blacklisted = EXCLUDED.is_blacklisted
            """
                .trimIndent(),
            events.map { insertParams(it) },
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun blacklist(contractAddresses: List<String>) {
        if (contractAddresses.isEmpty()) return

        namedJdbcTemplate.update(
            """
            UPDATE ${tableName()}
            SET is_blacklisted = true
            WHERE contract_address IN (:addresses)
            """
                .trimIndent(),
            mapOf("addresses" to contractAddresses),
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun whitelist(contractAddresses: List<String>) {
        if (contractAddresses.isEmpty()) return

        namedJdbcTemplate.update(
            """
            UPDATE ${tableName()}
            SET is_blacklisted = false
            WHERE contract_address IN (:addresses)
            """
                .trimIndent(),
            mapOf("addresses" to contractAddresses),
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

    // Query methods for pagination
    private fun queryWithPagination(
        whereClause: String,
        pageable: Pageable,
        vararg params: Any?,
    ): Slice<IndexedHistoryEvent> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause AND (is_blacklisted IS NULL OR is_blacklisted = false)
                ORDER BY block_timestamp DESC
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

    override fun findAllByToAndEventName(
        to: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination("to_address = ? AND event_name = ?", pageable, to, eventName)
    }

    override fun findAllByToAndEventNameAndBlockTimestampAfter(
        to: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "to_address = ? AND event_name = ? AND block_timestamp > ?",
            pageable,
            to,
            eventName,
            start,
        )
    }

    override fun findAllByToAndEventNameAndBlockTimestampBefore(
        to: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "to_address = ? AND event_name = ? AND block_timestamp < ?",
            pageable,
            to,
            eventName,
            end,
        )
    }

    override fun findAllByToAndEventNameAndBlockTimestampBetween(
        to: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "to_address = ? AND event_name = ? AND block_timestamp >= ? AND block_timestamp <= ?",
            pageable,
            to,
            eventName,
            start,
            end,
        )
    }

    override fun findAllByToAndAppIdAndEventNameAndBlockTimestampBetween(
        to: String,
        appId: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "to_address = ? AND app_id = ? AND event_name = ? AND block_timestamp >= ? AND block_timestamp <= ?",
            pageable,
            to,
            appId,
            eventName,
            start,
            end,
        )
    }

    override fun findAllByToAndAppIdAndEventNameAndBlockTimestampAfter(
        to: String,
        appId: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "to_address = ? AND app_id = ? AND event_name = ? AND block_timestamp > ?",
            pageable,
            to,
            appId,
            eventName,
            start,
        )
    }

    override fun findAllByToAndAppIdAndEventNameAndBlockTimestampBefore(
        to: String,
        appId: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "to_address = ? AND app_id = ? AND event_name = ? AND block_timestamp < ?",
            pageable,
            to,
            appId,
            eventName,
            end,
        )
    }

    override fun findAllByToAndAppIdAndEventName(
        to: String,
        appId: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "to_address = ? AND app_id = ? AND event_name = ?",
            pageable,
            to,
            appId,
            eventName,
        )
    }

    override fun findAllByAppIdAndEventNameAndBlockTimestampBetween(
        appId: String,
        eventName: String,
        start: Long,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "app_id = ? AND event_name = ? AND block_timestamp >= ? AND block_timestamp <= ?",
            pageable,
            appId,
            eventName,
            start,
            end,
        )
    }

    override fun findAllByAppIdAndEventNameAndBlockTimestampAfter(
        appId: String,
        eventName: String,
        start: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "app_id = ? AND event_name = ? AND block_timestamp > ?",
            pageable,
            appId,
            eventName,
            start,
        )
    }

    override fun findAllByAppIdAndEventNameAndBlockTimestampBefore(
        appId: String,
        eventName: String,
        end: Long,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination(
            "app_id = ? AND event_name = ? AND block_timestamp < ?",
            pageable,
            appId,
            eventName,
            end,
        )
    }

    override fun findAllByAppIdAndEventName(
        appId: String,
        eventName: String,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        return queryWithPagination("app_id = ? AND event_name = ?", pageable, appId, eventName)
    }

    override fun findUserHistoryByFilters(
        account: String?,
        eventNames: List<String>?,
        searchFields: List<String>?,
        contractAddress: String?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        // Add account search condition
        if (!searchFields.isNullOrEmpty() && !account.isNullOrBlank()) {
            val fieldConditions =
                searchFields.mapIndexed { idx, field ->
                    val columnName = fieldToColumn(field)
                    params["account$idx"] = account
                    "$columnName = :account$idx"
                }
            conditions.add("(${fieldConditions.joinToString(" OR ")})")
        } else if (!account.isNullOrBlank()) {
            params["account"] = account
            conditions.add(
                "(origin = :account OR gas_payer = :account OR to_address = :account OR from_address = :account OR owner = :account)"
            )
        }

        // Add contract address filter
        if (!contractAddress.isNullOrBlank()) {
            params["contractAddress"] = contractAddress
            conditions.add("contract_address = :contractAddress")
        }

        // Add event names filter
        if (!eventNames.isNullOrEmpty()) {
            params["eventNames"] = eventNames
            conditions.add("event_name IN (:eventNames)")
        }

        // Add timestamp filters
        if (before != null && after != null) {
            params["before"] = before
            params["after"] = after
            conditions.add("block_timestamp >= :after AND block_timestamp <= :before")
        } else if (before != null) {
            params["before"] = before
            conditions.add("block_timestamp <= :before")
        } else if (after != null) {
            params["after"] = after
            conditions.add("block_timestamp >= :after")
        }

        // Add blacklist filter
        conditions.add("(is_blacklisted IS NULL OR is_blacklisted = false)")

        val whereClause = if (conditions.isEmpty()) "1=1" else conditions.joinToString(" AND ")

        val limit = pageable.pageSize + 1
        val offset = pageable.offset
        params["limit"] = limit
        params["offset"] = offset

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause
                ORDER BY block_timestamp DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                params,
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findTokenIdHistoryByFilters(
        tokenId: String?,
        eventNames: List<String>?,
        contractAddress: String?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        // Add tokenId filter
        if (!tokenId.isNullOrBlank()) {
            params["tokenId"] = tokenId
            conditions.add("token_id = :tokenId")
        }

        // Add contract address filter
        if (!contractAddress.isNullOrBlank()) {
            params["contractAddress"] = contractAddress
            conditions.add("contract_address = :contractAddress")
        }

        // Add event names filter
        if (!eventNames.isNullOrEmpty()) {
            params["eventNames"] = eventNames
            conditions.add("event_name IN (:eventNames)")
        }

        // Add timestamp filters
        if (before != null && after != null) {
            params["before"] = before
            params["after"] = after
            conditions.add("block_timestamp >= :after AND block_timestamp <= :before")
        } else if (before != null) {
            params["before"] = before
            conditions.add("block_timestamp <= :before")
        } else if (after != null) {
            params["after"] = after
            conditions.add("block_timestamp >= :after")
        }

        // Add blacklist filter
        conditions.add("(is_blacklisted IS NULL OR is_blacklisted = false)")

        val whereClause = if (conditions.isEmpty()) "1=1" else conditions.joinToString(" AND ")

        val limit = pageable.pageSize + 1
        val offset = pageable.offset
        params["limit"] = limit
        params["offset"] = offset

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause
                ORDER BY block_timestamp DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                params,
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    private fun fieldToColumn(field: String): String =
        when (field) {
            "origin" -> "origin"
            "gasPayer" -> "gas_payer"
            "to" -> "to_address"
            "from" -> "from_address"
            "owner" -> "owner"
            else -> field
        }
}
