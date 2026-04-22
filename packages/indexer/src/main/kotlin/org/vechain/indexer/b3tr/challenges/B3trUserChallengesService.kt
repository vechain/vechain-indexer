package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.challenges.repository.B3trUserChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trUserChallengesService(
    private val repository: B3trUserChallengeRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val thorClient: ThorClient,
    @Value("\${business-event.substitutions.CHALLENGES_CONTRACT}")
    private val challengesContractAddress: String,
) {
    private val logger = LoggerFactory.getLogger(B3trUserChallengesService::class.java)

    private val trackedEventTypes =
        setOf(
            "ChallengeCreated",
            "ChallengeInviteAdded",
            "ChallengeJoined",
            "ChallengeLeft",
            "ChallengeDeclined",
            "ChallengeCompleted",
            "ChallengePayoutClaimed",
            "SplitWinPrizeClaimed",
            "SplitWinCreatorRefunded",
            "ChallengeRefundClaimed",
        )

    private val abiCache: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    open suspend fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<B3trUserChallenge>, List<B3trUserChallenge>> {
        val relevantEvents = events.filter { it.eventType in trackedEventTypes }
        if (relevantEvents.isEmpty()) return emptyList<B3trUserChallenge>() to emptyList()

        val completionChallengeIds =
            relevantEvents
                .filter { it.eventType == "ChallengeCompleted" }
                .map(B3trUserChallengeEventUtils::getChallengeId)
                .toSet()

        val preloaded = mutableMapOf<String, B3trUserChallenge>()
        // Track every (challengeId -> wallets) pair we've seen. Completion fanout iterates this
        // union of preloaded participants plus wallets created earlier in the batch.
        val walletsByChallenge = mutableMapOf<Long, MutableSet<String>>()

        relevantEvents.forEach { event ->
            val challengeId = B3trUserChallengeEventUtils.getChallengeId(event)
            B3trUserChallengeEventUtils.relevantWallets(event).forEach { wallet ->
                val id = B3trUserChallenge.documentId(wallet, challengeId)
                if (id !in preloaded) {
                    repository.findByWalletAndChallengeId(wallet, challengeId)?.let {
                        preloaded[id] = it
                    }
                }
                walletsByChallenge.getOrPut(challengeId) { mutableSetOf() }.add(wallet)
            }
        }
        completionChallengeIds.forEach { challengeId ->
            repository.findAllByChallengeId(challengeId).forEach {
                preloaded.putIfAbsent(it.getDocumentId(), it)
                walletsByChallenge.getOrPut(challengeId) { mutableSetOf() }.add(it.wallet)
            }
        }

        val createdAtByChallengeId =
            preloaded.values
                .groupBy(B3trUserChallenge::challengeId)
                .mapValues { (_, docs) ->
                    docs.minOf(B3trUserChallenge::challengeCreatedAtBlockTimestamp)
                }
                .toMutableMap()
        relevantEvents
            .filter { it.eventType == "ChallengeCreated" }
            .forEach {
                val challengeId = B3trUserChallengeEventUtils.getChallengeId(it)
                createdAtByChallengeId.putIfAbsent(challengeId, it.blockTimestamp)
            }

        val accumulator =
            VersionedDocumentAccumulator<B3trUserChallenge>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) }
            )

        groupByBlock(relevantEvents).forEach { (_, blockEvents) ->
            accumulator.startBlock()
            processBlock(blockEvents, createdAtByChallengeId, walletsByChallenge, accumulator)
        }

        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<B3trUserChallenge>, existing: List<B3trUserChallenge>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
    }

    /**
     * Within a block, group events by (wallet, challengeId) pair and produce exactly one `put` per
     * pair. Folding all events for a pair into a single state transition ensures the accumulator
     * archives only actually-persisted prior versions, not in-batch transients.
     */
    private suspend fun processBlock(
        blockEvents: List<IndexedEvent>,
        createdAtByChallengeId: MutableMap<Long, Long>,
        walletsByChallenge: Map<Long, Set<String>>,
        accumulator: VersionedDocumentAccumulator<B3trUserChallenge>,
    ) {
        val completionsByChallenge =
            blockEvents
                .filter { it.eventType == "ChallengeCompleted" }
                .associateBy { B3trUserChallengeEventUtils.getChallengeId(it) }

        val directEventsByPair = mutableMapOf<Pair<String, Long>, MutableList<IndexedEvent>>()
        blockEvents
            .filterNot { it.eventType == "ChallengeCompleted" }
            .forEach { event ->
                val challengeId = B3trUserChallengeEventUtils.getChallengeId(event)
                B3trUserChallengeEventUtils.relevantWallets(event).forEach { wallet ->
                    directEventsByPair
                        .getOrPut(wallet to challengeId) { mutableListOf() }
                        .add(event)
                }
            }

        val pairsToProcess = linkedSetOf<Pair<String, Long>>()
        pairsToProcess.addAll(directEventsByPair.keys)
        completionsByChallenge.keys.forEach { challengeId ->
            walletsByChallenge[challengeId]?.forEach { wallet ->
                pairsToProcess.add(wallet to challengeId)
            }
        }

        pairsToProcess.forEach { (wallet, challengeId) ->
            processPair(
                wallet = wallet,
                challengeId = challengeId,
                directEvents = directEventsByPair[wallet to challengeId] ?: emptyList(),
                completionEvent = completionsByChallenge[challengeId],
                createdAtByChallengeId = createdAtByChallengeId,
                accumulator = accumulator,
                walletsForCompletion = walletsByChallenge[challengeId].orEmpty(),
            )
        }
    }

    private suspend fun processPair(
        wallet: String,
        challengeId: Long,
        directEvents: List<IndexedEvent>,
        completionEvent: IndexedEvent?,
        createdAtByChallengeId: MutableMap<Long, Long>,
        accumulator: VersionedDocumentAccumulator<B3trUserChallenge>,
        walletsForCompletion: Set<String>,
    ) {
        val recordId = B3trUserChallenge.documentId(wallet, challengeId)
        val (existing, nextVersion) = accumulator.resolve(recordId)

        val fallbackEvent = directEvents.firstOrNull() ?: completionEvent ?: return
        val createdAt =
            existing?.challengeCreatedAtBlockTimestamp
                ?: createdAtByChallengeId[challengeId]
                ?: fallbackEvent.blockTimestamp
        createdAtByChallengeId.putIfAbsent(challengeId, createdAt)

        val createEvent = directEvents.firstOrNull { it.eventType == "ChallengeCreated" }
        val state =
            existing?.toMutableState()
                ?: when {
                    createEvent != null ->
                        B3trUserChallengeEventUtils.createUserChallengeState(
                            createEvent = createEvent,
                            wallet = wallet,
                            challengeCreatedAtBlockTimestamp = createdAt,
                        )
                    directEvents.isNotEmpty() ->
                        B3trUserChallengeEventUtils.createEmptyUserChallengeState(
                            wallet = wallet,
                            challengeId = challengeId,
                            challengeCreatedAtBlockTimestamp = createdAt,
                        )
                    else -> return // completion-only for a wallet with no prior record; nothing to
                // update
                }

        directEvents.forEach { event ->
            // createUserChallengeState already seeded fields from ChallengeCreated; don't re-apply.
            if (event.eventType == "ChallengeCreated" && existing == null) return@forEach
            B3trUserChallengeEventUtils.applyEvent(state, event)
        }

        if (
            completionEvent != null &&
                state.participantStatus == ParticipantStatus.Joined &&
                !state.isWinner &&
                shouldFanoutCompletion(completionEvent, walletsForCompletion.size, challengeId)
        ) {
            val bestScore = parseBestScore(completionEvent)
            val actions = fetchParticipantActions(challengeId, wallet)
            if (actions == bestScore) {
                state.isWinner = true
            }
        }

        if (!businessStateChanged(existing, state)) return

        val latestEvent = completionEvent ?: directEvents.last()
        val updated = state.toDocument(nextVersion, latestEvent)
        accumulator.put(recordId, existing, updated)
    }

    private fun businessStateChanged(
        existing: B3trUserChallenge?,
        state: MutableUserChallengeState,
    ): Boolean {
        if (existing == null) return true
        return existing.participantStatus != state.participantStatus ||
            existing.isCreator != state.isCreator ||
            existing.isWinner != state.isWinner ||
            existing.hasClaimedPrize != state.hasClaimedPrize ||
            existing.hasClaimedRefund != state.hasClaimedRefund ||
            existing.challengeCreatedAtBlockTimestamp != state.challengeCreatedAtBlockTimestamp
    }

    private fun shouldFanoutCompletion(
        completionEvent: IndexedEvent,
        candidateCount: Int,
        challengeId: Long,
    ): Boolean {
        val returnValues = completionEvent.params.getReturnValues()
        val settlementMode =
            SettlementMode.fromOrdinal(
                (returnValues["settlementMode"] as? Number)?.toInt()
                    ?: returnValues["settlementMode"]?.toString()?.toIntOrNull()
                    ?: 0
            )
        val bestCount =
            (returnValues["bestCount"] as? Number)?.toInt()
                ?: returnValues["bestCount"]?.toString()?.toIntOrNull()
                ?: 0
        if (settlementMode != SettlementMode.TopWinners || bestCount == 0) return false

        if (candidateCount > WINNER_FANOUT_WARN_THRESHOLD) {
            logger.warn(
                "Challenge {} completion fanning out {} view calls; consider batching",
                challengeId,
                candidateCount,
            )
        }
        return true
    }

    private fun parseBestScore(event: IndexedEvent): BigInteger =
        when (val raw = event.params.getReturnValues()["bestScore"]) {
            is BigInteger -> raw
            is Number -> BigInteger.valueOf(raw.toLong())
            is String -> raw.toBigInteger()
            else -> BigInteger.ZERO
        }

    private suspend fun fetchParticipantActions(challengeId: Long, wallet: String): BigInteger {
        val abi = loadChallengesAbiFunction("getParticipantActions")
        val clause =
            ContractUtils.createClause(
                challengesContractAddress,
                abi,
                BigInteger.valueOf(challengeId),
                AddressUtils.toBigInt(wallet),
            )
        val response = thorClient.inspectClauses(listOf(clause))
        val decoded = FunctionReturnDecoder.decode(response[0].data, abi.outputs)
        return when (val raw = decoded[""]) {
            is BigInteger -> raw
            is Number -> BigInteger.valueOf(raw.toLong())
            is String -> raw.toBigInteger()
            null -> BigInteger.ZERO
            else -> error("Unexpected getParticipantActions return: $raw")
        }
    }

    private fun loadChallengesAbiFunction(name: String): AbiElement =
        abiCache.computeIfAbsent(name) {
            AbiLoader.loadFunctions("abis/b3tr", listOf(name)).firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("Function '$name' not found in b3tr ABIs")
        }

    private companion object {
        const val WINNER_FANOUT_WARN_THRESHOLD = 200
    }
}
