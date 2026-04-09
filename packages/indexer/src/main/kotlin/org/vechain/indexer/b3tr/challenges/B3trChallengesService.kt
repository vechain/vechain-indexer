package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr-challenges")
@Service
open class B3trChallengesService(
    private val repository: B3trChallengeRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.CHALLENGES_CONTRACT}")
    private val challengesContract: String,
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

    private val functionAbis: Map<String, AbiElement> by lazy {
        AbiLoader.loadFunctions(
                basePath = "abis/b3tr",
                functionNames =
                    listOf(
                        "getChallenge",
                        "getChallengeParticipants",
                        "getChallengeInvited",
                        "getChallengeDeclined",
                        "getChallengeSelectedApps",
                    ),
            )
            .associateBy { it.name ?: error("ABI function without name") }
    }

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

        groupByBlock(challengeEvents).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            blockEvents
                .groupBy { getChallengeId(it) }
                .forEach { (challengeId, eventsForChallenge) ->
                    val recordId = B3trChallenge.documentId(challengeId)
                    val (existing, nextVersion) = accumulator.resolve(recordId)
                    val snapshot = fetchSnapshot(challengeId, blockDetails.blockId)
                    val updated =
                        buildChallengeDocument(
                            challengeId = challengeId,
                            existing = existing,
                            snapshot = snapshot,
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

    internal open suspend fun fetchSnapshot(
        challengeId: Long,
        blockId: String,
    ): ChallengeContractSnapshot {
        val revision = BlockRevision.Id(blockId)
        val clauses =
            listOf(
                ContractUtils.createClause(
                    challengesContract,
                    getFunctionAbi("getChallenge"),
                    BigInteger.valueOf(challengeId),
                ),
                ContractUtils.createClause(
                    challengesContract,
                    getFunctionAbi("getChallengeParticipants"),
                    BigInteger.valueOf(challengeId),
                ),
                ContractUtils.createClause(
                    challengesContract,
                    getFunctionAbi("getChallengeInvited"),
                    BigInteger.valueOf(challengeId),
                ),
                ContractUtils.createClause(
                    challengesContract,
                    getFunctionAbi("getChallengeDeclined"),
                    BigInteger.valueOf(challengeId),
                ),
                ContractUtils.createClause(
                    challengesContract,
                    getFunctionAbi("getChallengeSelectedApps"),
                    BigInteger.valueOf(challengeId),
                ),
            )

        val responses = thorClient.inspectClauses(clauses, revision)
        require(responses.size == clauses.size) {
            "Unexpected response count for challenge $challengeId: ${responses.size}"
        }

        val challenge = decodeResponseMap(responses[0].data, "getChallenge")
        val participants = decodeResponseMap(responses[1].data, "getChallengeParticipants")
        val invited = decodeResponseMap(responses[2].data, "getChallengeInvited")
        val declined = decodeResponseMap(responses[3].data, "getChallengeDeclined")
        val selectedApps = decodeResponseMap(responses[4].data, "getChallengeSelectedApps")

        return ChallengeContractSnapshot(
            kind = ChallengeKind.fromOrdinal(toIntValue(challenge["kind"])),
            visibility = ChallengeVisibility.fromOrdinal(toIntValue(challenge["visibility"])),
            thresholdMode = ThresholdMode.fromOrdinal(toIntValue(challenge["thresholdMode"])),
            status = ChallengeStatus.fromOrdinal(toIntValue(challenge["status"])),
            settlementMode = SettlementMode.fromOrdinal(toIntValue(challenge["settlementMode"])),
            creator = normaliseAddress(challenge["creator"]),
            stakeAmount = toBigIntegerValue(challenge["stakeAmount"]),
            startRound = toIntValue(challenge["startRound"]),
            endRound = toIntValue(challenge["endRound"]),
            duration = toIntValue(challenge["duration"]),
            threshold = toBigIntegerValue(challenge["threshold"]),
            allApps = toBooleanValue(challenge["allApps"]),
            totalPrize = toBigIntegerValue(challenge["totalPrize"]),
            participantCount = toIntValue(challenge["participantCount"]),
            invitedCount = toIntValue(challenge["invitedCount"]),
            declinedCount = toIntValue(challenge["declinedCount"]),
            selectedAppsCount = toIntValue(challenge["selectedAppsCount"]),
            bestScore = toBigIntegerValue(challenge["bestScore"]),
            bestCount = toIntValue(challenge["bestCount"]),
            qualifiedCount = toIntValue(challenge["qualifiedCount"]),
            payoutsClaimed = toIntValue(challenge["payoutsClaimed"]),
            participants = addressList(participants["participants"]),
            invited = addressList(invited["invited"]),
            declined = addressList(declined["declined"]),
            selectedApps = stringList(selectedApps["selectedApps"]),
        )
    }

    private fun buildChallengeDocument(
        challengeId: Long,
        existing: B3trChallenge?,
        snapshot: ChallengeContractSnapshot,
        eventsForChallenge: List<IndexedEvent>,
        version: Int,
    ): B3trChallenge {
        val latestEvent = eventsForChallenge.last()
        val createdEvent = eventsForChallenge.firstOrNull { it.eventType == "ChallengeCreated" }
        val newEligibleInvitees =
            eventsForChallenge
                .filter { it.eventType == "ChallengeInviteAdded" }
                .mapNotNull { event ->
                    event.params.getReturnValues()["invitee"]?.let(::normaliseAddress)
                }

        val claimedBy =
            mergeAddresses(
                existing?.claimedBy,
                eventsForChallenge
                    .filter { it.eventType == "ChallengePayoutClaimed" }
                    .mapNotNull { event ->
                        event.params.getReturnValues()["account"]?.let(::normaliseAddress)
                    },
            )
        val refundedBy =
            mergeAddresses(
                existing?.refundedBy,
                eventsForChallenge
                    .filter { it.eventType == "ChallengeRefundClaimed" }
                    .mapNotNull { event ->
                        event.params.getReturnValues()["account"]?.let(::normaliseAddress)
                    },
            )
        val eligibleInvitees =
            mergeAddresses(
                existing?.eligibleInvitees,
                snapshot.invited + snapshot.declined + newEligibleInvitees,
            )

        return B3trChallenge(
            version = version,
            blockId = latestEvent.blockId,
            blockNumber = latestEvent.blockNumber,
            blockTimestamp = latestEvent.blockTimestamp,
            challengeId = challengeId,
            kind = snapshot.kind,
            visibility = snapshot.visibility,
            thresholdMode = snapshot.thresholdMode,
            status = snapshot.status,
            settlementMode = snapshot.settlementMode,
            creator = snapshot.creator,
            stakeAmount = snapshot.stakeAmount,
            startRound = snapshot.startRound,
            endRound = snapshot.endRound,
            duration = snapshot.duration,
            threshold = snapshot.threshold,
            allApps = snapshot.allApps,
            totalPrize = snapshot.totalPrize,
            participantCount = snapshot.participantCount,
            invitedCount = snapshot.invitedCount,
            declinedCount = snapshot.declinedCount,
            selectedAppsCount = snapshot.selectedAppsCount,
            bestScore = snapshot.bestScore,
            bestCount = snapshot.bestCount,
            qualifiedCount = snapshot.qualifiedCount,
            payoutsClaimed = snapshot.payoutsClaimed,
            participants = snapshot.participants,
            invited = snapshot.invited,
            declined = snapshot.declined,
            selectedApps = snapshot.selectedApps,
            eligibleInvitees = eligibleInvitees,
            claimedBy = claimedBy,
            refundedBy = refundedBy,
            createdAtBlockNumber =
                existing?.createdAtBlockNumber
                    ?: createdEvent?.blockNumber
                    ?: latestEvent.blockNumber,
            createdAtBlockTimestamp =
                existing?.createdAtBlockTimestamp
                    ?: createdEvent?.blockTimestamp
                    ?: latestEvent.blockTimestamp,
            createdTxId = existing?.createdTxId ?: createdEvent?.txId ?: latestEvent.txId,
        )
    }

    private fun getChallengeId(event: IndexedEvent): Long =
        toLongValue(event.params.getReturnValues()["challengeId"])

    private fun getFunctionAbi(name: String): AbiElement =
        functionAbis[name] ?: throw IllegalArgumentException("Function '$name' not found")

    private fun decodeResponseMap(data: String?, functionName: String): Map<String, Any?> {
        require(!data.isNullOrBlank() && data != "0x") { "Missing response data for $functionName" }

        return FunctionReturnDecoder.decode(data, getFunctionAbi(functionName).outputs)
    }

    private fun addressList(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it?.let(::normaliseAddress) }?.distinct() ?: emptyList()

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it?.toString() }?.distinct() ?: emptyList()

    private fun mergeAddresses(existing: List<String>?, additional: List<String>): List<String> =
        (existing.orEmpty() + additional).distinct()

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

internal data class ChallengeContractSnapshot(
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
)
