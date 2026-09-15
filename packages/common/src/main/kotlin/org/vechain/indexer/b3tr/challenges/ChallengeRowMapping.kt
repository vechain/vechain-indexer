package org.vechain.indexer.b3tr.challenges

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresText

/** One wallet's hold on one role of a challenge, as of the block that gave or took it. */
data class ChallengeMember(
    val challengeId: Long,
    val wallet: String,
    val role: ChallengeMemberRole,
    val blockNumber: Long,
    val present: Boolean,
)

/** The address lists the challenge document carried, one role each. */
enum class ChallengeMemberRole {
    PARTICIPANT,
    INVITED,
    DECLINED,
    WINNER,
    ELIGIBLE_INVITEE,
    CLAIMED,
    REFUNDED,
}

/** A `b3tr_challenges.challenge` row to a [B3trChallenge] and back. */
object ChallengeRowMapping {
    const val TABLE = "b3tr_challenges.challenge"
    const val MEMBER_TABLE = "b3tr_challenges.challenge_member"
    const val APP_TABLE = "b3tr_challenges.challenge_app"

    /** The columns after `(challenge_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "kind",
            "visibility",
            "challenge_type",
            "on_chain_status",
            "status",
            "settlement_mode",
            "creator",
            "title",
            "description",
            "image_uri",
            "metadata_uri",
            "stake_amount",
            "start_round",
            "end_round",
            "threshold",
            "num_winners",
            "winners_claimed",
            "prize_per_winner",
            "all_apps",
            "total_prize",
            "best_score",
            "best_count",
            "payouts_claimed",
            "creator_refunded",
            "end_round_passed",
            "created_at_block_number",
            "created_at_block_timestamp",
            "created_tx_id",
        )

    /** The enum columns, which the insert casts because the driver sends them as text. */
    val ENUM_COLUMNS =
        mapOf(
            "kind" to "b3tr_challenges.kind",
            "visibility" to "b3tr_challenges.visibility",
            "challenge_type" to "b3tr_challenges.challenge_type",
            "on_chain_status" to "b3tr_challenges.status",
            "status" to "b3tr_challenges.status",
            "settlement_mode" to "b3tr_challenges.settlement_mode",
        )

    fun bind(ps: PreparedStatement, c: B3trChallenge) {
        ps.setLong(1, c.challengeId)
        ps.setLong(2, c.blockNumber)
        ps.setBytes(3, bytes(c.blockId))
        ps.setLong(4, c.blockTimestamp)
        ps.setString(5, c.kind.name)
        ps.setString(6, c.visibility.name)
        ps.setString(7, c.challengeType.name)
        ps.setString(8, c.onChainStatus.name)
        ps.setString(9, c.status.name)
        ps.setString(10, c.settlementMode.name)
        ps.setBytes(11, bytes(c.creator))
        // A title, a description and a URI are chain strings, which can carry NUL.
        ps.setString(12, PostgresText.escape(c.title))
        ps.setString(13, PostgresText.escape(c.description))
        ps.setString(14, PostgresText.escape(c.imageURI))
        ps.setString(15, PostgresText.escape(c.metadataURI))
        ps.setBigDecimal(16, BigDecimal(c.stakeAmount))
        ps.setInt(17, c.startRound)
        ps.setInt(18, c.endRound)
        ps.setBigDecimal(19, BigDecimal(c.threshold))
        ps.setInt(20, c.numWinners)
        ps.setInt(21, c.winnersClaimed)
        ps.setBigDecimal(22, BigDecimal(c.prizePerWinner))
        ps.setBoolean(23, c.allApps)
        ps.setBigDecimal(24, BigDecimal(c.totalPrize))
        ps.setBigDecimal(25, BigDecimal(c.bestScore))
        ps.setInt(26, c.bestCount)
        ps.setInt(27, c.payoutsClaimed)
        ps.setBoolean(28, c.creatorRefunded)
        ps.setBoolean(29, c.endRoundPassed)
        ps.setLong(30, c.createdAtBlockNumber)
        ps.setLong(31, c.createdAtBlockTimestamp)
        ps.setBytes(32, bytes(c.createdTxId))
    }

    fun bindMember(ps: PreparedStatement, m: ChallengeMember) {
        ps.setLong(1, m.challengeId)
        ps.setBytes(2, bytes(m.wallet))
        ps.setString(3, m.role.name)
        ps.setLong(4, m.blockNumber)
        ps.setBoolean(5, m.present)
    }

    /**
     * The row, with the counts and the address lists the reader joined in. A list arrives empty
     * where the caller only asked for the counts.
     */
    fun read(
        rs: ResultSet,
        counts: Map<ChallengeMemberRole, Int> = emptyMap(),
        members: Map<ChallengeMemberRole, List<String>> = emptyMap(),
        apps: List<String> = emptyList(),
        appCount: Int = apps.size,
    ): B3trChallenge {
        val startRound = rs.getInt("start_round")
        val endRound = rs.getInt("end_round")
        return B3trChallenge(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            challengeId = rs.getLong("challenge_id"),
            kind = ChallengeKind.valueOf(rs.getString("kind")),
            visibility = ChallengeVisibility.valueOf(rs.getString("visibility")),
            challengeType = ChallengeType.valueOf(rs.getString("challenge_type")),
            onChainStatus = ChallengeStatus.valueOf(rs.getString("on_chain_status")),
            status = ChallengeStatus.valueOf(rs.getString("status")),
            settlementMode = SettlementMode.valueOf(rs.getString("settlement_mode")),
            creator = hex(rs.getBytes("creator")),
            title = PostgresText.unescape(rs.getString("title")),
            description = PostgresText.unescape(rs.getString("description")),
            imageURI = PostgresText.unescape(rs.getString("image_uri")),
            metadataURI = PostgresText.unescape(rs.getString("metadata_uri")),
            stakeAmount = rs.getBigDecimal("stake_amount").toBigIntegerExact(),
            startRound = startRound,
            endRound = endRound,
            duration = endRound - startRound + 1,
            threshold = rs.getBigDecimal("threshold").toBigIntegerExact(),
            numWinners = rs.getInt("num_winners"),
            winnersClaimed = rs.getInt("winners_claimed"),
            prizePerWinner = rs.getBigDecimal("prize_per_winner").toBigIntegerExact(),
            allApps = rs.getBoolean("all_apps"),
            totalPrize = rs.getBigDecimal("total_prize").toBigIntegerExact(),
            participantCount = counts[ChallengeMemberRole.PARTICIPANT] ?: 0,
            invitedCount = counts[ChallengeMemberRole.INVITED] ?: 0,
            declinedCount = counts[ChallengeMemberRole.DECLINED] ?: 0,
            selectedAppsCount = appCount,
            winnersCount = counts[ChallengeMemberRole.WINNER] ?: 0,
            bestScore = rs.getBigDecimal("best_score").toBigIntegerExact(),
            bestCount = rs.getInt("best_count"),
            payoutsClaimed = rs.getInt("payouts_claimed"),
            participants = members[ChallengeMemberRole.PARTICIPANT].orEmpty(),
            invited = members[ChallengeMemberRole.INVITED].orEmpty(),
            declined = members[ChallengeMemberRole.DECLINED].orEmpty(),
            selectedApps = apps,
            winners = members[ChallengeMemberRole.WINNER].orEmpty(),
            eligibleInvitees = members[ChallengeMemberRole.ELIGIBLE_INVITEE].orEmpty(),
            claimedBy = members[ChallengeMemberRole.CLAIMED].orEmpty(),
            refundedBy = members[ChallengeMemberRole.REFUNDED].orEmpty(),
            creatorRefunded = rs.getBoolean("creator_refunded"),
            endRoundPassed = rs.getBoolean("end_round_passed"),
            createdAtBlockNumber = rs.getLong("created_at_block_number"),
            createdAtBlockTimestamp = rs.getLong("created_at_block_timestamp"),
            createdTxId = hex(rs.getBytes("created_tx_id")),
        )
    }

    fun readMember(rs: ResultSet): ChallengeMember =
        ChallengeMember(
            challengeId = rs.getLong("challenge_id"),
            wallet = hex(rs.getBytes("wallet")),
            role = ChallengeMemberRole.valueOf(rs.getString("role")),
            blockNumber = rs.getLong("block_number"),
            present = rs.getBoolean("present"),
        )
}

/** A `b3tr_challenges.user_challenge` row to a [B3trUserChallenge] and back. */
object UserChallengeRowMapping {
    const val TABLE = "b3tr_challenges.user_challenge"

    /** The columns after `(wallet, challenge_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "challenge_created_at_block_timestamp",
            "participant_status",
            "is_creator",
            "is_winner",
            "has_claimed_prize",
            "has_claimed_refund",
        )

    fun bind(ps: PreparedStatement, u: B3trUserChallenge) {
        ps.setBytes(1, bytes(u.wallet))
        ps.setLong(2, u.challengeId)
        ps.setLong(3, u.blockNumber)
        ps.setBytes(4, bytes(u.blockId))
        ps.setLong(5, u.blockTimestamp)
        ps.setLong(6, u.challengeCreatedAtBlockTimestamp)
        ps.setString(7, u.participantStatus.name)
        ps.setBoolean(8, u.isCreator)
        ps.setBoolean(9, u.isWinner)
        ps.setBoolean(10, u.hasClaimedPrize)
        ps.setBoolean(11, u.hasClaimedRefund)
    }

    fun read(rs: ResultSet): B3trUserChallenge =
        B3trUserChallenge(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            wallet = hex(rs.getBytes("wallet")),
            challengeId = rs.getLong("challenge_id"),
            challengeCreatedAtBlockTimestamp = rs.getLong("challenge_created_at_block_timestamp"),
            participantStatus = ParticipantStatus.valueOf(rs.getString("participant_status")),
            isCreator = rs.getBoolean("is_creator"),
            isWinner = rs.getBoolean("is_winner"),
            hasClaimedPrize = rs.getBoolean("has_claimed_prize"),
            hasClaimedRefund = rs.getBoolean("has_claimed_refund"),
        )
}
