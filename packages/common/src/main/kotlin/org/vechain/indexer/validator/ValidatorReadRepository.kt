package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.thor.model.BlockIdentifier

/** Reads of the current validator set, for the API and the indexers under `.dependsOn(...)`. */
@Repository
@ConditionalOnPostgres
open class ValidatorReadRepository(@Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    open fun findAll(): List<Validator> = query("$CURRENT ORDER BY id", MapSqlParameterSource())

    /** Current rows after [since], plus those at [since], whose block id proves it stands. */
    open fun snapshotsSince(since: Long?): List<ValidatorSnapshotRow> =
        jdbc.query(
            "SELECT id, block_number, block_id, superseded_at IS NULL AS current, " +
                "cycle_period_length, start_block, exit_block FROM validator.state WHERE " +
                if (since == null) "superseded_at IS NULL"
                else "(block_number > :since AND superseded_at IS NULL) OR block_number = :since",
            MapSqlParameterSource("since", since),
        ) { rs, _ ->
            ValidatorSnapshotRow(
                block =
                    BlockIdentifier(
                        rs.getLong("block_number"),
                        PostgresHex.hex(rs.getBytes("block_id")),
                    ),
                current = rs.getBoolean("current"),
                snapshot =
                    ValidatorSnapshot(
                        validatorId = PostgresHex.hex(rs.getBytes("id")),
                        stakingPeriodLength = rs.getLong("cycle_period_length"),
                        startBlock = rs.getLong("start_block"),
                        exitBlock = rs.getLong("exit_block"),
                    ),
            )
        }

    open fun findById(id: String): Validator? =
        query("$CURRENT AND id = :id", MapSqlParameterSource("id", PostgresHex.bytes(id)))
            .firstOrNull()

    open fun findAllById(ids: Collection<String>): List<Validator> =
        if (ids.isEmpty()) emptyList()
        else
            query(
                "$CURRENT AND id = ANY(:ids) ORDER BY id",
                MapSqlParameterSource("ids", ids.map(PostgresHex::bytes).toTypedArray()),
            )

    open fun findByStatusIn(statuses: Collection<Status>): List<Validator> =
        query(
            "$CURRENT AND status = ANY(CAST(:statuses AS validator.status[])) ORDER BY id",
            MapSqlParameterSource("statuses", statuses.map { it.name }.toTypedArray()),
        )

    /**
     * The `/validators` page: optional id, endorser and status filters, ordered by one of
     * [SORT_COLUMNS] with nulls where Mongo put them (lowest) and the id as tiebreak.
     */
    open fun find(
        id: String?,
        endorser: String?,
        statuses: Collection<Status>?,
        sortField: String,
        direction: Direction,
        offset: Long,
        limit: Int,
    ): List<Validator> {
        val column =
            requireNotNull(SORT_COLUMNS[sortField]) { "Unsupported validator sort: $sortField" }
        val order = if (direction == Direction.DESC) "DESC NULLS LAST" else "ASC NULLS FIRST"
        val params =
            MapSqlParameterSource("id", PostgresHex.bytesOrNull(id))
                .addValue("endorser", PostgresHex.bytesOrNull(endorser))
                .addValue("statuses", statuses?.map { it.name }?.toTypedArray())
                .addValue("offset", offset)
                .addValue("limit", limit)
        return query(
            CURRENT +
                (if (id == null) "" else " AND id = :id") +
                (if (endorser == null) "" else " AND endorser = :endorser") +
                (if (statuses == null) ""
                else " AND status = ANY(CAST(:statuses AS validator.status[]))") +
                " ORDER BY $column $order, id ASC OFFSET :offset LIMIT :limit",
            params,
        )
    }

    private fun query(sql: String, params: MapSqlParameterSource): List<Validator> =
        jdbc.query(sql, params) { rs, _ -> ValidatorRowMapping.read(rs) }

    companion object {
        private const val CURRENT = "SELECT * FROM validator.state WHERE superseded_at IS NULL"
        val SORT_COLUMNS =
            mapOf(
                Validator::validatorVetStaked.name to "validator_vet_staked",
                Validator::vetStaked.name to "vet_staked",
                Validator::validatorLockedWeight.name to "validator_locked_weight",
                Validator::delegatorVetStaked.name to "delegator_vet_staked",
            )
    }
}
