package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trChallengesService(
    private val repository: B3trChallengeRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    private val trackedEventTypes =
        setOf(
            "ChallengeCreated",
            "ChallengeInviteAdded",
            "ChallengeJoined",
            "ChallengeLeft",
            "ChallengeDeclined",
            "ChallengeCancelled",
            "ChallengeActivated",
            "ChallengeInvalidated",
            "ChallengeFinalized",
            "ChallengePayoutClaimed",
            "ChallengeRefundClaimed",
        )

    open fun findByChallengeId(challengeId: Long): B3trChallenge? =
        repository.findByIdOrNull(B3trChallenge.documentId(challengeId))

    open suspend fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<B3trChallenge>, List<B3trChallenge>> {
        val challengeEvents = events.filter { it.eventType in trackedEventTypes }
        if (challengeEvents.isEmpty()) return emptyList<B3trChallenge>() to emptyList()

        val allRecordIds =
            challengeEvents.map { B3trChallenge.documentId(getChallengeId(it)) }.toSet()

        val preloaded =
            if (allRecordIds.isNotEmpty()) {
                repository.findAllById(allRecordIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<B3trChallenge>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) }
            )

        groupByBlock(challengeEvents).forEach { (_, blockEvents) ->
            accumulator.startBlock()
            blockEvents
                .groupBy { getChallengeId(it) }
                .forEach { (challengeId, eventsForChallenge) ->
                    val recordId = B3trChallenge.documentId(challengeId)
                    val (existing, nextVersion) = accumulator.resolve(recordId)
                    val updated =
                        buildChallengeDocument(
                            challengeId = challengeId,
                            existing = existing,
                            eventsForChallenge = eventsForChallenge,
                            version = nextVersion,
                        )

                    if (existing != updated) {
                        accumulator.put(recordId, existing, updated)
                    }
                }
        }

        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<B3trChallenge>, existing: List<B3trChallenge>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
    }

    private fun buildChallengeDocument(
        challengeId: Long,
        existing: B3trChallenge?,
        eventsForChallenge: List<IndexedEvent>,
        version: Int,
    ): B3trChallenge {
        val latestEvent = eventsForChallenge.last()
        val createdEvent = eventsForChallenge.firstOrNull { it.eventType == "ChallengeCreated" }

        require(existing == null || createdEvent == null) {
            "Unexpected ChallengeCreated event for existing challenge $challengeId"
        }

        val state =
            existing?.toMutableState()
                ?: createChallengeState(
                    createdEvent ?: error("Missing ChallengeCreated for $challengeId")
                )

        eventsForChallenge
            .filterNot { it.eventType == "ChallengeCreated" }
            .forEach { event -> applyEvent(challengeId, state, event) }

        return state.toDocument(challengeId, version, latestEvent)
    }

    private fun createChallengeState(createEvent: IndexedEvent): MutableChallengeState {
        val kind = ChallengeKind.fromOrdinal(toIntValue(eventValue(createEvent, "kind")))
        val creator = getAddress(createEvent, "creator")
        val stakeAmount = toBigIntegerValue(eventValue(createEvent, "stakeAmount"))

        return MutableChallengeState(
            kind = kind,
            visibility =
                ChallengeVisibility.fromOrdinal(toIntValue(eventValue(createEvent, "visibility"))),
            thresholdMode =
                ThresholdMode.fromOrdinal(toIntValue(eventValue(createEvent, "thresholdMode"))),
            status = ChallengeStatus.Pending,
            settlementMode = SettlementMode.None,
            creator = creator,
            stakeAmount = stakeAmount,
            startRound = toIntValue(eventValue(createEvent, "startRound")),
            endRound = toIntValue(eventValue(createEvent, "endRound")),
            threshold = toBigIntegerValue(eventValue(createEvent, "threshold")),
            allApps = toBooleanValue(eventValue(createEvent, "allApps")),
            totalPrize = stakeAmount,
            bestScore = BigInteger.ZERO,
            bestCount = 0,
            qualifiedCount = 0,
            payoutsClaimed = 0,
            participants =
                if (kind == ChallengeKind.Stake) {
                    mutableListOf(creator)
                } else {
                    mutableListOf()
                },
            invited = mutableListOf(),
            declined = mutableListOf(),
            selectedApps = stringList(eventValue(createEvent, "selectedApps")),
            eligibleInvitees = mutableListOf(),
            claimedBy = mutableListOf(),
            refundedBy = mutableListOf(),
            createdAtBlockNumber = createEvent.blockNumber,
            createdAtBlockTimestamp = createEvent.blockTimestamp,
            createdTxId = createEvent.txId,
        )
    }

    private fun applyEvent(challengeId: Long, state: MutableChallengeState, event: IndexedEvent) {
        when (event.eventType) {
            "ChallengeInviteAdded" -> {
                val invitee = getAddress(event, "invitee")
                swapRemove(state.declined, invitee)
                addDistinct(state.invited, invitee)
                addDistinct(state.eligibleInvitees, invitee)
            }

            "ChallengeJoined" -> {
                val participant = getAddress(event, "participant")
                swapRemove(state.invited, participant)
                swapRemove(state.declined, participant)
                if (
                    addDistinct(state.participants, participant) &&
                        state.kind == ChallengeKind.Stake
                ) {
                    state.totalPrize += state.stakeAmount
                }
            }

            "ChallengeLeft" -> {
                val participant = getAddress(event, "participant")
                if (
                    swapRemove(state.participants, participant) && state.kind == ChallengeKind.Stake
                ) {
                    state.totalPrize -= state.stakeAmount
                }
                if (participant in state.eligibleInvitees) {
                    addDistinct(state.invited, participant)
                }
            }

            "ChallengeDeclined" -> {
                val participant = getAddress(event, "participant")
                if (
                    swapRemove(state.participants, participant) && state.kind == ChallengeKind.Stake
                ) {
                    state.totalPrize -= state.stakeAmount
                }
                swapRemove(state.invited, participant)
                swapRemove(state.declined, participant)
                addDistinct(state.declined, participant)
                addDistinct(state.eligibleInvitees, participant)
            }

            "ChallengeCancelled" -> state.status = ChallengeStatus.Cancelled

            "ChallengeActivated" -> state.status = ChallengeStatus.Active

            "ChallengeInvalidated" -> state.status = ChallengeStatus.Invalid

            "ChallengeFinalized" -> {
                state.status = ChallengeStatus.Finalized
                state.settlementMode =
                    SettlementMode.fromOrdinal(toIntValue(eventValue(event, "settlementMode")))
                state.bestScore = toBigIntegerValue(eventValue(event, "bestScore"))
                state.bestCount = toIntValue(eventValue(event, "bestCount"))
                state.qualifiedCount = toIntValue(eventValue(event, "qualifiedCount"))
            }

            "ChallengePayoutClaimed" -> {
                addDistinct(state.claimedBy, getAddress(event, "account"))
                state.payoutsClaimed++
            }

            "ChallengeRefundClaimed" -> addDistinct(state.refundedBy, getAddress(event, "account"))

            else -> error("Unsupported challenge event ${event.eventType} for $challengeId")
        }
    }

    private fun B3trChallenge.toMutableState() =
        MutableChallengeState(
            kind = kind,
            visibility = visibility,
            thresholdMode = thresholdMode,
            status = status,
            settlementMode = settlementMode,
            creator = creator,
            stakeAmount = stakeAmount,
            startRound = startRound,
            endRound = endRound,
            threshold = threshold,
            allApps = allApps,
            totalPrize = totalPrize,
            bestScore = bestScore,
            bestCount = bestCount,
            qualifiedCount = qualifiedCount,
            payoutsClaimed = payoutsClaimed,
            participants = participants.toMutableList(),
            invited = invited.toMutableList(),
            declined = declined.toMutableList(),
            selectedApps = selectedApps,
            eligibleInvitees = eligibleInvitees.toMutableList(),
            claimedBy = claimedBy.toMutableList(),
            refundedBy = refundedBy.toMutableList(),
            createdAtBlockNumber = createdAtBlockNumber,
            createdAtBlockTimestamp = createdAtBlockTimestamp,
            createdTxId = createdTxId,
        )

    private fun MutableChallengeState.toDocument(
        challengeId: Long,
        version: Int,
        latestEvent: IndexedEvent,
    ) =
        B3trChallenge(
            version = version,
            blockId = latestEvent.blockId,
            blockNumber = latestEvent.blockNumber,
            blockTimestamp = latestEvent.blockTimestamp,
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
            duration = endRound - startRound + 1,
            threshold = threshold,
            allApps = allApps,
            totalPrize = totalPrize,
            participantCount = participants.size,
            invitedCount = invited.size,
            declinedCount = declined.size,
            selectedAppsCount = selectedApps.size,
            bestScore = bestScore,
            bestCount = bestCount,
            qualifiedCount = qualifiedCount,
            payoutsClaimed = payoutsClaimed,
            participants = participants.toList(),
            invited = invited.toList(),
            declined = declined.toList(),
            selectedApps = selectedApps,
            eligibleInvitees = eligibleInvitees.toList(),
            claimedBy = claimedBy.toList(),
            refundedBy = refundedBy.toList(),
            createdAtBlockNumber = createdAtBlockNumber,
            createdAtBlockTimestamp = createdAtBlockTimestamp,
            createdTxId = createdTxId,
        )

    private fun eventValue(event: IndexedEvent, key: String): Any? =
        event.params.getReturnValues()[key]

    private fun getChallengeId(event: IndexedEvent): Long =
        toLongValue(eventValue(event, "challengeId"))

    private fun getAddress(event: IndexedEvent, key: String): String =
        normaliseAddress(eventValue(event, key))

    private fun addDistinct(addresses: MutableList<String>, address: String): Boolean {
        if (address in addresses) return false
        addresses.add(address)
        return true
    }

    private fun swapRemove(addresses: MutableList<String>, address: String): Boolean {
        val index = addresses.indexOf(address)
        if (index == -1) return false

        val lastIndex = addresses.lastIndex
        if (index != lastIndex) {
            addresses[index] = addresses[lastIndex]
        }
        addresses.removeAt(lastIndex)
        return true
    }

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it?.toString() }?.distinct() ?: emptyList()

    private fun normaliseAddress(value: Any?): String {
        val address = value?.toString() ?: error("Expected address value")
        return HexUtils.normalise(address)
    }

    private fun toBooleanValue(value: Any?): Boolean =
        value as? Boolean ?: error("Expected boolean value, got $value")

    private fun toBigIntegerValue(value: Any?): BigInteger =
        when (value) {
            is BigInteger -> value
            is Number -> BigInteger.valueOf(value.toLong())
            is String -> value.toBigInteger()
            else -> error("Expected numeric value, got $value")
        }

    private fun toIntValue(value: Any?): Int = toBigIntegerValue(value).toInt()

    private fun toLongValue(value: Any?): Long = toBigIntegerValue(value).toLong()
}

private data class MutableChallengeState(
    var kind: ChallengeKind,
    var visibility: ChallengeVisibility,
    var thresholdMode: ThresholdMode,
    var status: ChallengeStatus,
    var settlementMode: SettlementMode,
    var creator: String,
    var stakeAmount: BigInteger,
    var startRound: Int,
    var endRound: Int,
    var threshold: BigInteger,
    var allApps: Boolean,
    var totalPrize: BigInteger,
    var bestScore: BigInteger,
    var bestCount: Int,
    var qualifiedCount: Int,
    var payoutsClaimed: Int,
    val participants: MutableList<String>,
    val invited: MutableList<String>,
    val declined: MutableList<String>,
    val selectedApps: List<String>,
    val eligibleInvitees: MutableList<String>,
    val claimedBy: MutableList<String>,
    val refundedBy: MutableList<String>,
    val createdAtBlockNumber: Long,
    val createdAtBlockTimestamp: Long,
    val createdTxId: String,
)
