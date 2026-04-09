package org.vechain.indexer.b3tr.challenges

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.B3TR_CHALLENGES.COLLECTION)
data class B3trChallenge
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val challengeId: Long,
    val kind: ChallengeKind,
    val visibility: ChallengeVisibility,
    val thresholdMode: ThresholdMode,
    val status: ChallengeStatus,
    val settlementMode: SettlementMode,
    val creator: String,
    val stakeAmount: BigInteger,
    val startRound: Int,
    val endRound: Int,
    val duration: Int,
    val threshold: BigInteger,
    val allApps: Boolean,
    val totalPrize: BigInteger,
    val participantCount: Int,
    val invitedCount: Int,
    val declinedCount: Int,
    val selectedAppsCount: Int,
    val bestScore: BigInteger,
    val bestCount: Int,
    val qualifiedCount: Int,
    val payoutsClaimed: Int,
    val participants: List<String>,
    val invited: List<String>,
    val declined: List<String>,
    val selectedApps: List<String>,
    val eligibleInvitees: List<String>,
    val claimedBy: List<String>,
    val refundedBy: List<String>,
    val createdAtBlockNumber: Long,
    val createdAtBlockTimestamp: Long,
    val createdTxId: String,
) : VersionedDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        challengeId: Long,
        kind: ChallengeKind,
        visibility: ChallengeVisibility,
        thresholdMode: ThresholdMode,
        status: ChallengeStatus,
        settlementMode: SettlementMode,
        creator: String,
        stakeAmount: BigInteger,
        startRound: Int,
        endRound: Int,
        duration: Int,
        threshold: BigInteger,
        allApps: Boolean,
        totalPrize: BigInteger,
        participantCount: Int,
        invitedCount: Int,
        declinedCount: Int,
        selectedAppsCount: Int,
        bestScore: BigInteger,
        bestCount: Int,
        qualifiedCount: Int,
        payoutsClaimed: Int,
        participants: List<String>,
        invited: List<String>,
        declined: List<String>,
        selectedApps: List<String>,
        eligibleInvitees: List<String>,
        claimedBy: List<String>,
        refundedBy: List<String>,
        createdAtBlockNumber: Long,
        createdAtBlockTimestamp: Long,
        createdTxId: String,
    ) : this(
        id = documentId(challengeId),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        challengeId = challengeId,
        kind = kind,
        visibility = visibility,
        thresholdMode = thresholdMode,
        status = status,
        settlementMode = settlementMode,
        creator = creator,
        stakeAmount = stakeAmount,
        startRound = startRound,
        endRound = endRound,
        duration = duration,
        threshold = threshold,
        allApps = allApps,
        totalPrize = totalPrize,
        participantCount = participantCount,
        invitedCount = invitedCount,
        declinedCount = declinedCount,
        selectedAppsCount = selectedAppsCount,
        bestScore = bestScore,
        bestCount = bestCount,
        qualifiedCount = qualifiedCount,
        payoutsClaimed = payoutsClaimed,
        participants = participants,
        invited = invited,
        declined = declined,
        selectedApps = selectedApps,
        eligibleInvitees = eligibleInvitees,
        claimedBy = claimedBy,
        refundedBy = refundedBy,
        createdAtBlockNumber = createdAtBlockNumber,
        createdAtBlockTimestamp = createdAtBlockTimestamp,
        createdTxId = createdTxId,
    )

    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        fun documentId(challengeId: Long): String = challengeId.toString()
    }
}

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

enum class ThresholdMode {
    None,
    SplitAboveThreshold,
    TopAboveThreshold;

    companion object {
        fun fromOrdinal(ordinal: Int): ThresholdMode =
            entries.getOrNull(ordinal)
                ?: throw IllegalArgumentException("Unknown ThresholdMode ordinal: $ordinal")
    }
}

enum class ChallengeStatus {
    Pending,
    Active,
    Finalized,
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
    QualifiedSplit,
    CreatorRefund;

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
