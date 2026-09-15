package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trUserChallengesService(
    private val repository: ChallengeWriteRepository,
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

    /** The new row of each (wallet, challenge) pair touched in each block. */
    open suspend fun processEvents(events: List<IndexedEvent>): List<B3trUserChallenge> {
        val relevantEvents = events.filter { it.eventType in trackedEventTypes }
        if (relevantEvents.isEmpty()) return emptyList()

        val completionChallengeIds =
            relevantEvents
                .filter { it.eventType == "ChallengeCompleted" }
                .map(B3trUserChallengeEventUtils::getChallengeId)
                .toSet()

        val keys =
            relevantEvents
                .flatMap { event ->
                    val challengeId = B3trUserChallengeEventUtils.getChallengeId(event)
                    B3trUserChallengeEventUtils.relevantWallets(event).map { it to challengeId }
                }
                .toSet()
        // A completion decides a winner for every wallet already in the challenge, not just the
        // ones this batch names.
        val current =
            (repository.findCurrentUsers(keys.filter { it in keys }.toSet()) +
                    repository.findCurrentUsersOfChallenge(completionChallengeIds))
                .associateBy { it.wallet to it.challengeId }
        val walletsByChallenge =
            (keys + current.keys).groupBy({ it.second }, { it.first }).mapValues {
                it.value.toSet()
            }

        val createdAtByChallengeId =
            current.values
                .groupBy(B3trUserChallenge::challengeId)
                .mapValues { (_, rows) ->
                    rows.minOf(B3trUserChallenge::challengeCreatedAtBlockTimestamp)
                }
                .toMutableMap()
        relevantEvents
            .filter { it.eventType == "ChallengeCreated" }
            .forEach {
                val challengeId = B3trUserChallengeEventUtils.getChallengeId(it)
                createdAtByChallengeId.putIfAbsent(challengeId, it.blockTimestamp)
            }

        val states = current.toMutableMap()
        val rows = linkedMapOf<Triple<Long, String, Long>, B3trUserChallenge>()

        groupByBlock(relevantEvents).forEach { (_, blockEvents) ->
            processBlock(blockEvents, createdAtByChallengeId, walletsByChallenge, states, rows)
        }

        return rows.values.toList()
    }

    /** One row per (wallet, challenge), so a block's transient states never reach the schema. */
    private suspend fun processBlock(
        blockEvents: List<IndexedEvent>,
        createdAtByChallengeId: MutableMap<Long, Long>,
        walletsByChallenge: Map<Long, Set<String>>,
        states: MutableMap<Pair<String, Long>, B3trUserChallenge>,
        rows: MutableMap<Triple<Long, String, Long>, B3trUserChallenge>,
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

        val completionActionsByChallenge =
            completionsByChallenge.mapValues { (challengeId, completionEvent) ->
                fetchParticipantActionsForCompletion(
                    completionEvent = completionEvent,
                    challengeId = challengeId,
                    wallets = walletsByChallenge[challengeId].orEmpty(),
                )
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
                states = states,
                rows = rows,
                walletsForCompletion = walletsByChallenge[challengeId].orEmpty(),
                actionsByWalletForCompletion = completionActionsByChallenge[challengeId].orEmpty(),
            )
        }
    }

    private fun processPair(
        wallet: String,
        challengeId: Long,
        directEvents: List<IndexedEvent>,
        completionEvent: IndexedEvent?,
        createdAtByChallengeId: MutableMap<Long, Long>,
        states: MutableMap<Pair<String, Long>, B3trUserChallenge>,
        rows: MutableMap<Triple<Long, String, Long>, B3trUserChallenge>,
        walletsForCompletion: Set<String>,
        actionsByWalletForCompletion: Map<String, BigInteger>,
    ) {
        val existing = states[wallet to challengeId]

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
                    // Completion-only for a wallet with no prior row; nothing to update.
                    else -> return
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
            val actions = actionsByWalletForCompletion[wallet] ?: BigInteger.ZERO
            if (actions == bestScore) {
                state.isWinner = true
            }
        }

        if (!businessStateChanged(existing, state)) return

        val latestEvent = completionEvent ?: directEvents.last()
        val updated = state.toDocument(latestEvent)
        states[wallet to challengeId] = updated
        rows[Triple(latestEvent.blockNumber, wallet, challengeId)] = updated
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
                "Challenge {} completion requires {} participant action lookups at completion block",
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

    private suspend fun fetchParticipantActionsForCompletion(
        completionEvent: IndexedEvent,
        challengeId: Long,
        wallets: Set<String>,
    ): Map<String, BigInteger> {
        if (!shouldFanoutCompletion(completionEvent, wallets.size, challengeId)) {
            return emptyMap()
        }

        val abi = loadChallengesAbiFunction("getParticipantActions")
        val revision = BlockRevision.Id(completionEvent.blockId)
        val candidateWallets = wallets.sorted()
        val actionsByWallet = linkedMapOf<String, BigInteger>()

        candidateWallets.chunked(PARTICIPANT_ACTIONS_BATCH_SIZE).forEach { batch ->
            val clauses = batch.map { wallet ->
                ContractUtils.createClause(
                    challengesContractAddress,
                    abi,
                    BigInteger.valueOf(challengeId),
                    AddressUtils.toBigInt(wallet),
                )
            }
            val responses = thorClient.inspectClauses(clauses, revision)
            batch.forEachIndexed { index, wallet ->
                val response =
                    responses.getOrNull(index)
                        ?: error(
                            "Missing getParticipantActions response for challengeId=$challengeId wallet=$wallet"
                        )
                actionsByWallet[wallet] =
                    parseParticipantActions(response, abi, challengeId, wallet)
            }
        }

        return actionsByWallet
    }

    private fun parseParticipantActions(
        response: InspectionResult,
        abi: AbiElement,
        challengeId: Long,
        wallet: String,
    ): BigInteger {
        if (response.reverted) {
            error(
                "getParticipantActions reverted for challengeId=$challengeId wallet=$wallet vmError=${response.vmError}"
            )
        }

        val decoded = FunctionReturnDecoder.decode(response.data, abi.outputs)
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
        const val PARTICIPANT_ACTIONS_BATCH_SIZE = 200
    }
}
