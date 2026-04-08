package org.vechain.indexer.nft

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
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.BigIntegerUtils

@Profile("nfts")
@Service
open class NftHistoryService(private val mongoTemplate: MongoTemplate) {

    open fun findTokenHistory(
        contractAddress: Address,
        tokenId: String,
        eventNames: List<String>?,
        before: Long?,
        after: Long?,
        pageable: Pageable,
    ): Slice<IndexedHistoryEvent> {
        val normalizedTokenId = BigIntegerUtils.fromHexOrDecimal(tokenId).toString(10)
        val requestedEventNames = eventNames?.takeIf { it.isNotEmpty() } ?: DEFAULT_EVENT_NAMES
        val criteria =
            buildCriteria(
                contractAddress.value,
                normalizedTokenId,
                requestedEventNames,
                before,
                after,
            )
        val query = Query(criteria).with(pageable)
        query.limit(pageable.pageSize + 1)

        val raw = mongoTemplate.find(query, IndexedHistoryEvent::class.java)
        val hasNext = raw.size > pageable.pageSize
        val content = if (hasNext) raw.dropLast(1) else raw

        return SliceImpl(content, pageable, hasNext)
    }

    private fun buildCriteria(
        contractAddress: String,
        tokenId: String,
        eventNames: List<String>,
        before: Long?,
        after: Long?,
    ): Criteria {
        val criteria =
            mutableListOf(
                Criteria.where(IndexedHistoryEvent::contractAddress.name).`is`(contractAddress),
                Criteria.where(IndexedHistoryEvent::tokenId.name).`is`(tokenId),
                Criteria.where(IndexedHistoryEvent::eventName.name).`in`(eventNames),
                Criteria.where(IndexedHistoryEvent::isBlacklisted.name).ne(true),
            )

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

    companion object {
        val ALLOWED_EVENT_NAMES: Set<HistoryEventName> =
            linkedSetOf(HistoryEventName.TRANSFER_NFT, HistoryEventName.NFT_SALE)

        val DEFAULT_EVENT_NAMES: List<String> = ALLOWED_EVENT_NAMES.map { it.name }
    }
}
