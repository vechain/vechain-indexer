package org.vechain.indexer.b3tr.challenges

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.postgres.PostgresText

/** One challenge's apps, as the creation event listed them. */
data class ChallengeApps(
    val challengeId: Long,
    val blockNumber: Long,
    val appIds: List<String>,
)

/** Everything one entry adds to the schema. */
data class ChallengeUpdate(
    val challenges: List<B3trChallenge> = emptyList(),
    val members: List<ChallengeMember> = emptyList(),
    val apps: List<ChallengeApps> = emptyList(),
    val userChallenges: List<B3trUserChallenge> = emptyList(),
) {
    fun isEmpty(): Boolean =
        challenges.isEmpty() && members.isEmpty() && apps.isEmpty() && userChallenges.isEmpty()
}

/** The `b3tr_challenges` schema: the challenges, who is in them, and what each wallet is to one. */
@Repository
@ConditionalOnPostgres
open class ChallengeWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Everything one entry adds, written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: ChallengeUpdate) {
        update.challenges.groupBy { it.blockNumber }.toSortedMap().forEach(::saveChallenges)
        update.members.groupBy { it.blockNumber }.toSortedMap().forEach(::saveMembers)
        saveApps(update.apps)
        update.userChallenges.groupBy { it.blockNumber }.toSortedMap().forEach(::saveUsers)
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveChallenges(blockNumber: Long, block: List<B3trChallenge>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { it.challengeId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? WHERE challenge_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, c ->
            ps.setLong(1, blockNumber)
            ps.setLong(2, c.challengeId)
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, c -> ChallengeRowMapping.bind(ps, c) }
    }

    private fun saveMembers(blockNumber: Long, block: List<ChallengeMember>) {
        val rows = block.associateBy { Triple(it.challengeId, it.wallet, it.role) }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $MEMBER_TABLE SET superseded_at = ? WHERE challenge_id = ? AND wallet = ? " +
                "AND role = CAST(? AS b3tr_challenges.member_role) AND superseded_at IS NULL " +
                "AND block_number < ?",
            rows,
            rows.size,
        ) { ps, m ->
            ps.setLong(1, blockNumber)
            ps.setLong(2, m.challengeId)
            ps.setBytes(3, bytes(m.wallet))
            ps.setString(4, m.role.name)
            ps.setLong(5, blockNumber)
        }
        jdbc.batchUpdate(MEMBER_INSERT, rows, rows.size) { ps, m ->
            ChallengeRowMapping.bindMember(ps, m)
        }
    }

    private fun saveApps(apps: List<ChallengeApps>) {
        val rows = apps.flatMap { challenge ->
            challenge.appIds.mapIndexed { index, appId -> Triple(challenge, index + 1, appId) }
        }
        if (rows.isEmpty()) return
        jdbc.batchUpdate(APP_INSERT, rows, rows.size) { ps, (challenge, position, appId) ->
            ps.setLong(1, challenge.challengeId)
            ps.setInt(2, position)
            ps.setString(3, PostgresText.escape(appId))
            ps.setLong(4, challenge.blockNumber)
        }
    }

    private fun saveUsers(blockNumber: Long, block: List<B3trUserChallenge>) {
        val rows = block.associateBy { it.wallet to it.challengeId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $USER_TABLE SET superseded_at = ? WHERE wallet = ? AND challenge_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, u ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(u.wallet))
            ps.setLong(3, u.challengeId)
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(USER_INSERT, rows, rows.size) { ps, u ->
            UserChallengeRowMapping.bind(ps, u)
        }
    }

    /** The current row of every challenge the batch touches, without its members. */
    open fun findCurrent(challengeIds: Set<Long>): List<B3trChallenge> =
        if (challengeIds.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $TABLE WHERE challenge_id = ANY(?) AND superseded_at IS NULL",
                { rs, _ -> ChallengeRowMapping.read(rs) },
                challengeIds.toTypedArray(),
            )

    /** Who currently holds which role on each of [challengeIds]. */
    open fun findCurrentMembers(
        challengeIds: Set<Long>
    ): Map<Long, Map<ChallengeMemberRole, List<String>>> =
        if (challengeIds.isEmpty()) emptyMap()
        else
            jdbc
                .query(
                    "SELECT * FROM $MEMBER_TABLE WHERE challenge_id = ANY(?) " +
                        "AND superseded_at IS NULL AND present ORDER BY block_number, wallet",
                    { rs, _ -> ChallengeRowMapping.readMember(rs) },
                    challengeIds.toTypedArray(),
                )
                .groupBy { it.challengeId }
                .mapValues { (_, members) ->
                    members.groupBy({ it.role }, { it.wallet })
                }

    /**
     * The challenges a round boundary moves, with the participants they are judged on: a challenge
     * becomes Active or Invalid when its start round arrives, and passes its end round later.
     */
    open fun findCurrentByRounds(lowerBound: Int, upperBound: Int): List<B3trChallenge> =
        jdbc.query(
            "SELECT c.*, coalesce(m.participants, 0) AS participants FROM $TABLE c " +
                "LEFT JOIN LATERAL (SELECT count(*) AS participants FROM $MEMBER_TABLE mm " +
                "WHERE mm.challenge_id = c.challenge_id AND mm.superseded_at IS NULL " +
                "AND mm.present AND mm.role = 'PARTICIPANT') m ON TRUE " +
                "WHERE c.superseded_at IS NULL AND ((c.start_round > ? AND c.start_round <= ?) " +
                "OR (c.end_round >= ? AND c.end_round < ?)) ORDER BY c.challenge_id",
            { rs, _ ->
                ChallengeRowMapping.read(
                    rs,
                    counts = mapOf(ChallengeMemberRole.PARTICIPANT to rs.getInt("participants")),
                )
            },
            lowerBound,
            upperBound,
            lowerBound,
            upperBound,
        )

    /** The current row of every (wallet, challenge) pair the batch touches. */
    open fun findCurrentUsers(keys: Set<Pair<String, Long>>): List<B3trUserChallenge> =
        if (keys.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $USER_TABLE WHERE wallet = ANY(?) AND challenge_id = ANY(?) " +
                    "AND superseded_at IS NULL",
                { rs, _ -> UserChallengeRowMapping.read(rs) },
                keys.map { bytes(it.first) }.distinct().toTypedArray(),
                keys.map { it.second }.distinct().toTypedArray(),
            )

    /** Every wallet with a row on [challengeId], which a completion fans out over. */
    open fun findCurrentUsersOfChallenge(challengeIds: Set<Long>): List<B3trUserChallenge> =
        if (challengeIds.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $USER_TABLE WHERE challenge_id = ANY(?) AND superseded_at IS NULL",
                { rs, _ -> UserChallengeRowMapping.read(rs) },
                challengeIds.toTypedArray(),
            )

    override fun rollbackFrom(blockNumber: Long) {
        listOf(TABLE, MEMBER_TABLE, USER_TABLE).forEach { table ->
            jdbc.update("DELETE FROM $table WHERE block_number >= ?", blockNumber)
            jdbc.update(
                "UPDATE $table SET superseded_at = NULL WHERE superseded_at >= ?",
                blockNumber,
            )
        }
        jdbc.update("DELETE FROM $APP_TABLE WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE $TABLE, $MEMBER_TABLE, $APP_TABLE, $USER_TABLE")
    }

    override fun prune(before: Long): Int =
        listOf(TABLE, MEMBER_TABLE, USER_TABLE).sumOf { table ->
            jdbc.update("DELETE FROM $table WHERE superseded_at < ?", before)
        }

    companion object {
        private const val TABLE = ChallengeRowMapping.TABLE
        private const val MEMBER_TABLE = ChallengeRowMapping.MEMBER_TABLE
        private const val APP_TABLE = ChallengeRowMapping.APP_TABLE
        private const val USER_TABLE = UserChallengeRowMapping.TABLE

        private val INSERT =
            "INSERT INTO $TABLE (challenge_id, block_number, " +
                ChallengeRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                ChallengeRowMapping.COLUMNS.joinToString { column ->
                    ChallengeRowMapping.ENUM_COLUMNS[column]?.let { "CAST(? AS $it)" } ?: "?"
                } +
                ") ON CONFLICT (challenge_id, block_number) DO UPDATE SET " +
                ChallengeRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }

        private const val MEMBER_INSERT =
            "INSERT INTO $MEMBER_TABLE (challenge_id, wallet, role, block_number, present) " +
                "VALUES (?, ?, CAST(? AS b3tr_challenges.member_role), ?, ?) " +
                "ON CONFLICT (challenge_id, wallet, role, block_number) DO UPDATE " +
                "SET present = EXCLUDED.present"

        private const val APP_INSERT =
            "INSERT INTO $APP_TABLE (challenge_id, position, app_id, block_number) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT (challenge_id, position) DO UPDATE " +
                "SET app_id = EXCLUDED.app_id, block_number = EXCLUDED.block_number"

        private val USER_INSERT =
            "INSERT INTO $USER_TABLE (wallet, challenge_id, block_number, " +
                UserChallengeRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, ?, " +
                UserChallengeRowMapping.COLUMNS.joinToString {
                    if (it == "participant_status") "CAST(? AS b3tr_challenges.participant_status)"
                    else "?"
                } +
                ") ON CONFLICT (wallet, challenge_id, block_number) DO UPDATE SET " +
                UserChallengeRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
