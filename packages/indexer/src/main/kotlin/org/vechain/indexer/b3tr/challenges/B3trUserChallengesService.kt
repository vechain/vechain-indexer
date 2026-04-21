package org.vechain.indexer.b3tr.challenges

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.challenges.repository.B3trUserChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "b3tr-challenges")
@Service
open class B3trUserChallengesService(
    private val repository: B3trUserChallengeRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    private val trackedEventTypes =
        setOf("ChallengeCreated", "ChallengeInviteAdded", "ChallengeJoined", "ChallengeDeclined")

    open suspend fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<B3trUserChallenge>, List<B3trUserChallenge>> {
        val relevantEvents = events.filter { it.eventType in trackedEventTypes }
        if (relevantEvents.isEmpty()) return emptyList<B3trUserChallenge>() to emptyList()

        val challengeIds = relevantEvents.map(::getChallengeId).toSet()
        val currentDocs =
            mutableMapOf<String, B3trUserChallenge>().apply {
                challengeIds.forEach { challengeId ->
                    repository.findAllByChallengeId(challengeId).forEach {
                        put(it.getDocumentId(), it)
                    }
                }
            }
        val createdAtByChallengeId =
            currentDocs.values
                .groupBy(B3trUserChallenge::challengeId)
                .mapValues { (_, docs) ->
                    docs.minOf(B3trUserChallenge::challengeCreatedAtBlockTimestamp)
                }
                .toMutableMap()
        val createdDocs = linkedMapOf<String, B3trUserChallenge>()

        relevantEvents.forEach { event ->
            val challengeId = getChallengeId(event)
            if (event.eventType == "ChallengeCreated") {
                createdAtByChallengeId.putIfAbsent(challengeId, event.blockTimestamp)
            }

            relevantWallets(event).forEach { wallet ->
                val documentId = B3trUserChallenge.documentId(wallet, challengeId)
                if (documentId in currentDocs || documentId in createdDocs) {
                    return@forEach
                }

                createdDocs[documentId] =
                    B3trUserChallenge(
                        version = 1,
                        blockId = event.blockId,
                        blockNumber = event.blockNumber,
                        blockTimestamp = event.blockTimestamp,
                        wallet = wallet,
                        challengeId = challengeId,
                        challengeCreatedAtBlockTimestamp =
                            createdAtByChallengeId[challengeId] ?: event.blockTimestamp,
                    )
            }
        }

        return createdDocs.values.toList() to emptyList()
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

    private fun relevantWallets(event: IndexedEvent): Set<String> =
        when (event.eventType) {
            "ChallengeCreated" ->
                setOfNotNull(event.params.getAsString("creator")?.let(HexUtils::normalise))
            "ChallengeInviteAdded" ->
                setOfNotNull(event.params.getAsString("invitee")?.let(HexUtils::normalise))
            "ChallengeJoined",
            "ChallengeDeclined" ->
                setOfNotNull(event.params.getAsString("participant")?.let(HexUtils::normalise))
            else -> emptySet()
        }

    private fun getChallengeId(event: IndexedEvent): Long =
        when (val value = event.params.getReturnValues()["challengeId"]) {
            is Number -> value.toLong()
            else -> value?.toString()?.toLong() ?: error("Expected challengeId value")
        }
}
