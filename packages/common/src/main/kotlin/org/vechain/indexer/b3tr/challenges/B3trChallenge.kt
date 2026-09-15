package org.vechain.indexer.b3tr.challenges

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

/** A challenge as of [blockNumber]; the counts and the address lists come from its member rows. */
data class B3trChallenge(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val challengeId: Long,
    val kind: ChallengeKind,
    val visibility: ChallengeVisibility,
    val challengeType: ChallengeType,
    @JsonIgnore val onChainStatus: ChallengeStatus,
    val status: ChallengeStatus,
    val settlementMode: SettlementMode,
    val creator: String,
    val title: String,
    val description: String,
    val imageURI: String,
    val metadataURI: String,
    val stakeAmount: BigInteger,
    val startRound: Int,
    val endRound: Int,
    val duration: Int,
    val threshold: BigInteger,
    val numWinners: Int,
    val winnersClaimed: Int,
    val prizePerWinner: BigInteger,
    val allApps: Boolean,
    val totalPrize: BigInteger,
    val participantCount: Int = 0,
    val invitedCount: Int = 0,
    val declinedCount: Int = 0,
    val selectedAppsCount: Int = 0,
    val winnersCount: Int = 0,
    val bestScore: BigInteger,
    val bestCount: Int,
    val payoutsClaimed: Int,
    val participants: List<String> = emptyList(),
    val invited: List<String> = emptyList(),
    val declined: List<String> = emptyList(),
    val selectedApps: List<String> = emptyList(),
    val winners: List<String> = emptyList(),
    val eligibleInvitees: List<String> = emptyList(),
    val claimedBy: List<String> = emptyList(),
    val refundedBy: List<String> = emptyList(),
    val creatorRefunded: Boolean,
    val endRoundPassed: Boolean,
    val createdAtBlockNumber: Long,
    val createdAtBlockTimestamp: Long,
    val createdTxId: String,
) : IndexedDocument

enum class ChallengeKind {
    Stake,
    Sponsored;

    companion object {
        fun fromOrdinal(ordinal: Int): ChallengeKind =
            entries.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown ChallengeKind ordinal: $ordinal")
    }
}

enum class ChallengeVisibility {
    Public,
    Private;

    companion object {
        fun fromOrdinal(ordinal: Int): ChallengeVisibility =
            entries.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown ChallengeVisibility ordinal: $ordinal")
    }
}

/**
 * Discriminates the two challenge mechanics.
 * - `MaxActions`: capped participant pool, top scorer wins after completion.
 * - `SplitWin`: uncapped participants, sponsored only, first-to-claim wins one of `numWinners`
 *   slots.
 */
enum class ChallengeType {
    MaxActions,
    SplitWin;

    companion object {
        fun fromOrdinal(ordinal: Int): ChallengeType =
            entries.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown ChallengeType ordinal: $ordinal")
    }
}

enum class ChallengeStatus {
    Pending,
    Active,
    Completed,
    Cancelled,
    Invalid;

    companion object {
        fun fromOrdinal(ordinal: Int): ChallengeStatus =
            entries.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown ChallengeStatus ordinal: $ordinal")
    }
}

enum class SettlementMode {
    None,
    TopWinners,
    CreatorRefund,
    SplitWinCompleted;

    companion object {
        fun fromOrdinal(ordinal: Int): SettlementMode =
            entries.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown SettlementMode ordinal: $ordinal")
    }
}

enum class ParticipantStatus {
    None,
    Invited,
    Declined,
    Joined;

    companion object {
        fun fromOrdinal(ordinal: Int): ParticipantStatus =
            entries.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown ParticipantStatus ordinal: $ordinal")
    }
}
