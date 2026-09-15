package org.vechain.indexer.b3tr.challenges

import com.fasterxml.jackson.annotation.JsonIgnore
import org.vechain.indexer.IndexedDocument

/** What one wallet is to one challenge; its creation time is carried so a wallet's rows sort. */
data class B3trUserChallenge(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val wallet: String,
    val challengeId: Long,
    val challengeCreatedAtBlockTimestamp: Long,
    val participantStatus: ParticipantStatus = ParticipantStatus.None,
    val isCreator: Boolean = false,
    val isWinner: Boolean = false,
    val hasClaimedPrize: Boolean = false,
    val hasClaimedRefund: Boolean = false,
) : IndexedDocument
