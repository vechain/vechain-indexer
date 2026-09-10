package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BigIntegerUtils

@Profile("stargate")
@Service
class StargateTokenHistoryService(
    private val mongoTemplate: MongoTemplate,
    @Value(
        "\${business-event.substitutions.STARGATE_NFT_CONTRACT:\${STARGATE_NFT_CONTRACT:0x1856c533ac2d94340aaa8544d35a5c1d4a21dee7}}"
    )
    stargateNftContract: String,
) {
    private val stargateNftContract = HexUtils.normalise(stargateNftContract)
    private val protocolEventNames = PROTOCOL_EVENT_NAMES.map { it.name }
    private val nftScopedEventNames = NFT_SCOPED_EVENT_NAMES.map { it.name }

    fun findTokenHistory(
        tokenId: String,
        eventNames: List<String>?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val normalizedTokenId = BigIntegerUtils.fromHexOrDecimal(tokenId).toString(10)
        val criteria = buildCriteria(normalizedTokenId, eventNames, before, after)
        val query = Query(criteria).with(pageable)
        query.limit(pageable.pageSize + 1)

        val raw = mongoTemplate.find(query, IndexedHistoryEvent::class.java)
        val hasNext = raw.size > pageable.pageSize
        val content = if (hasNext) raw.dropLast(1) else raw

        return SliceImpl(content, pageable, hasNext)
    }

    private fun buildCriteria(
        tokenId: String,
        eventNames: List<String>?,
        before: Long?,
        after: Long?,
    ): Criteria {
        val criteria =
            mutableListOf(
                Criteria.where(IndexedHistoryEvent::tokenId.name).`is`(tokenId),
                Criteria.where(IndexedHistoryEvent::isBlacklisted.name).ne(true),
                buildStargateTokenScopeCriteria(),
            )

        if (!eventNames.isNullOrEmpty()) {
            criteria += Criteria.where(IndexedHistoryEvent::eventName.name).`in`(eventNames)
        }

        if (before != null && after != null) {
            criteria +=
                Criteria.where(IndexedHistoryEvent::blockTimestamp.name).gte(after).lte(before)
        } else if (before != null) {
            criteria += Criteria.where(IndexedHistoryEvent::blockTimestamp.name).lte(before)
        } else if (after != null) {
            criteria += Criteria.where(IndexedHistoryEvent::blockTimestamp.name).gte(after)
        }

        return Criteria().andOperator(*criteria.toTypedArray())
    }

    private fun buildStargateTokenScopeCriteria(): Criteria =
        Criteria()
            .orOperator(
                Criteria.where(IndexedHistoryEvent::eventName.name).`in`(protocolEventNames),
                Criteria()
                    .andOperator(
                        Criteria.where(IndexedHistoryEvent::eventName.name)
                            .`in`(nftScopedEventNames),
                        Criteria.where(IndexedHistoryEvent::contractAddress.name)
                            .`is`(stargateNftContract),
                    ),
            )

    companion object {
        val PROTOCOL_EVENT_NAMES: Set<HistoryEventName> =
            HistoryEventName.entries.filterTo(linkedSetOf()) { it.name.startsWith("STARGATE_") }

        val NFT_SCOPED_EVENT_NAMES: Set<HistoryEventName> =
            linkedSetOf(
                HistoryEventName.TRANSFER_NFT,
                HistoryEventName.NFT_SALE,
                HistoryEventName.VEVOTE_VOTE_CAST,
            )

        val ALLOWED_EVENT_NAMES: Set<HistoryEventName> =
            PROTOCOL_EVENT_NAMES + NFT_SCOPED_EVENT_NAMES
    }
}
