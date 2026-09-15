package org.vechain.indexer.b3tr.challenges

import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresText

/** The challenges behind the `/b3tr/challenges` endpoints, each read in one query. */
@Repository
@ConditionalOnPostgres
open class ChallengeReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    /** Public challenges, optionally narrowed to one status. */
    open fun findPublic(
        status: ChallengeStatus?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<B3trChallenge> {
        val filter = if (status == null) "" else "AND c.status = CAST(? AS b3tr_challenges.status)"
        val args = listOfNotNull<Any>(status?.name)
        return jdbc.query(
            "$SUMMARY_SELECT WHERE c.superseded_at IS NULL " +
                "AND c.visibility = 'Public' $filter ${order(direction)} OFFSET ? LIMIT ?",
            { rs, _ -> summary(rs) },
            *(args + offset + limit).toTypedArray(),
        )
    }

    /**
     * Public challenges of one status that [wallet] is not in: a wallet counts as involved only
     * while it is the creator or holds a participant status, so leaving one brings it back here.
     */
    open fun findOpenTo(
        wallet: String,
        status: ChallengeStatus,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<B3trChallenge> =
        jdbc.query(
            "$SUMMARY_SELECT WHERE c.superseded_at IS NULL AND c.visibility = 'Public' " +
                "AND c.status = CAST(? AS b3tr_challenges.status) AND NOT EXISTS (" +
                "SELECT 1 FROM ${UserChallengeRowMapping.TABLE} u " +
                "WHERE u.challenge_id = c.challenge_id AND u.wallet = ? " +
                "AND u.superseded_at IS NULL AND (u.is_creator OR u.participant_status <> 'None')" +
                ") ${order(direction)} OFFSET ? LIMIT ?",
            { rs, _ -> summary(rs) },
            status.name,
            bytes(wallet),
            offset,
            limit,
        )

    /** A wallet's challenges in one bucket, sorted by when the challenge it holds was created. */
    open fun findByFilter(
        wallet: String,
        filter: ChallengeFilter,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<B3trChallenge> {
        val predicate =
            when (filter) {
                ChallengeFilter.MyChallenges -> MY_CHALLENGES
                ChallengeFilter.History -> HISTORY
                ChallengeFilter.NeededAction -> NEEDED_ACTION
                ChallengeFilter.OpenToJoin,
                ChallengeFilter.OthersActive ->
                    throw IllegalArgumentException("Filter $filter is not served by findByFilter")
            }
        val order = direction.name
        return jdbc.query(
            "$SUMMARY_SELECT JOIN ${UserChallengeRowMapping.TABLE} u " +
                "ON u.challenge_id = c.challenge_id AND u.superseded_at IS NULL " +
                "WHERE c.superseded_at IS NULL AND u.wallet = ? AND ($predicate) " +
                "ORDER BY u.challenge_created_at_block_timestamp $order, c.challenge_id $order " +
                "OFFSET ? LIMIT ?",
            { rs, _ -> summary(rs) },
            bytes(wallet),
            offset,
            limit,
        )
    }

    /** One challenge with the address lists it carries. */
    open fun findById(challengeId: Long): B3trChallenge? =
        jdbc
            .query(
                "$DETAIL_SELECT WHERE c.superseded_at IS NULL AND c.challenge_id = ?",
                { rs, _ ->
                    ChallengeRowMapping.read(
                        rs,
                        counts = counts(rs),
                        members = ChallengeMemberRole.entries.associateWith { addresses(rs, it) },
                        apps = strings(rs, "app_ids"),
                    )
                },
                challengeId,
            )
            .firstOrNull()

    open fun latestBlockNumber(): Long =
        jdbc.queryForObject(
            "SELECT coalesce(max(block_number), 0) FROM ${ChallengeRowMapping.TABLE}",
            Long::class.java,
        )!!

    open fun latestUserBlockNumber(): Long =
        jdbc.queryForObject(
            "SELECT coalesce(max(block_number), 0) FROM ${UserChallengeRowMapping.TABLE}",
            Long::class.java,
        )!!

    private fun summary(rs: ResultSet) =
        ChallengeRowMapping.read(rs, counts = counts(rs), appCount = rs.getInt("app_count"))

    private fun counts(rs: ResultSet) =
        ChallengeMemberRole.entries.associateWith { rs.getInt("${it.name.lowercase()}_count") }

    private fun addresses(rs: ResultSet, role: ChallengeMemberRole): List<String> =
        (rs.getArray("${role.name.lowercase()}_wallets")?.array as? Array<*>)?.mapNotNull {
            (it as? ByteArray)?.let(::hex)
        } ?: emptyList()

    private fun strings(rs: ResultSet, column: String): List<String> =
        (rs.getArray(column)?.array as? Array<*>)?.map {
            PostgresText.unescape(it as? String ?: "")
        } ?: emptyList()

    private fun order(direction: Direction) =
        "ORDER BY c.created_at_block_timestamp ${direction.name}, c.challenge_id ${direction.name}"

    companion object {
        private val ROLE_COUNTS =
            ChallengeMemberRole.entries.joinToString(", ") {
                "count(*) FILTER (WHERE m.role = '${it.name}') AS ${it.name.lowercase()}_count"
            }

        private val ROLE_WALLETS =
            ChallengeMemberRole.entries.joinToString(", ") {
                "array_agg(m.wallet ORDER BY m.block_number, m.wallet) " +
                    "FILTER (WHERE m.role = '${it.name}') AS ${it.name.lowercase()}_wallets"
            }

        private fun members(aggregates: String) =
            "LEFT JOIN LATERAL (SELECT $aggregates FROM ${ChallengeRowMapping.MEMBER_TABLE} m " +
                "WHERE m.challenge_id = c.challenge_id AND m.superseded_at IS NULL AND m.present" +
                ") mem ON TRUE"

        private val SUMMARY_SELECT =
            "SELECT c.*, mem.*, coalesce(app.app_count, 0) AS app_count " +
                "FROM ${ChallengeRowMapping.TABLE} c " +
                members(ROLE_COUNTS) +
                " LEFT JOIN LATERAL (SELECT count(*) AS app_count " +
                "FROM ${ChallengeRowMapping.APP_TABLE} a WHERE a.challenge_id = c.challenge_id" +
                ") app ON TRUE"

        private val DETAIL_SELECT =
            "SELECT c.*, mem.*, coalesce(app.app_count, 0) AS app_count, app.app_ids " +
                "FROM ${ChallengeRowMapping.TABLE} c " +
                members("$ROLE_COUNTS, $ROLE_WALLETS") +
                " LEFT JOIN LATERAL (SELECT count(*) AS app_count, " +
                "array_agg(a.app_id ORDER BY a.position) AS app_ids " +
                "FROM ${ChallengeRowMapping.APP_TABLE} a WHERE a.challenge_id = c.challenge_id" +
                ") app ON TRUE"

        private const val MY_CHALLENGES =
            "(u.is_creator OR u.participant_status = 'Joined') " +
                "AND c.status IN ('Pending', 'Active')"

        // A non-creator reaches None only by leaving after joining, which History surfaces too.
        private const val HISTORY =
            "c.status IN ('Completed', 'Cancelled', 'Invalid') " +
                "OR (NOT u.is_creator AND u.participant_status IN ('None', 'Declined'))"

        // Refunds mirror the contract: a staker reclaims their stake, a sponsor their pool.
        private const val NEEDED_ACTION =
            "(u.participant_status = 'Invited' AND c.status IN ('Pending', 'Active')) " +
                "OR (u.is_winner AND NOT u.has_claimed_prize AND c.status = 'Completed' " +
                "AND c.challenge_type = 'MaxActions') " +
                "OR (c.challenge_type = 'MaxActions' AND c.status = 'Active' " +
                "AND c.end_round_passed AND (u.participant_status = 'Joined' OR u.is_creator)) " +
                "OR (NOT u.has_claimed_refund AND c.status IN ('Cancelled', 'Invalid') " +
                "AND c.kind = 'Stake' AND u.participant_status = 'Joined') " +
                "OR (NOT u.has_claimed_refund AND c.status IN ('Cancelled', 'Invalid') " +
                "AND c.kind = 'Sponsored' AND u.is_creator) " +
                "OR (u.is_creator AND c.challenge_type = 'SplitWin' " +
                "AND c.status IN ('Active', 'Completed') AND c.end_round_passed " +
                "AND NOT u.has_claimed_refund AND c.winners_claimed < c.num_winners)"
    }
}
