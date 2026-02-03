package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address

@Profile("transfers")
@Service
open class TransferEventService(
    private val transferEventRepository: TransferEventRepository,
    private val fungibleTokenInteractionsRepository: FungibleTokenInteractionsRepository,
    private val officialTokenService: OfficialTokenService,
    private val mongoTemplate: MongoTemplate,
) {

    fun find(
        to: Address? = null,
        from: Address? = null,
        toOrFrom: Address? = null,
        tokenAddress: Address? = null,
        eventType: TransferEventType? = null,
        after: Long? = null,
        before: Long? = null,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        val criteria =
            buildCriteria(
                to = to?.value,
                from = from?.value,
                toOrFrom = toOrFrom?.value,
                tokenAddress = tokenAddress?.value,
                eventType = eventType,
                after = after,
                before = before,
            )
        return runQuery(criteria, pageable)
    }

    fun findByBlockNumber(
        blockNumber: Long,
        addresses: List<Address>,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return transferEventRepository.findByBlockNumberAndToOrFromIn(
            blockNumber,
            addresses.map { it.value },
            pageable,
        )
    }

    fun findFungibleTokensContractsByAddress(
        address: Address,
        officialTokensOnly: Boolean,
        pageable: Pageable,
    ): Slice<String> {
        val interactions =
            if (officialTokensOnly) {
                fungibleTokenInteractionsRepository.findAllByWalletAddressAndContractAddresses(
                    address.value,
                    officialTokenService.getOfficialTokenAddresses(),
                    pageable,
                )
            } else {
                fungibleTokenInteractionsRepository.findByWalletAddress(address.value, pageable)
            }
        return interactions.map { it.contractAddress }
    }

    private fun buildCriteria(
        to: String? = null,
        from: String? = null,
        toOrFrom: String? = null,
        tokenAddress: String? = null,
        eventType: TransferEventType? = null,
        after: Long? = null,
        before: Long? = null,
    ): Criteria {
        val criteria = Criteria()

        if (toOrFrom != null) {
            criteria.orOperator(
                Criteria.where(IndexedTransferEvent::to.name).`is`(toOrFrom),
                Criteria.where(IndexedTransferEvent::from.name).`is`(toOrFrom),
            )
        } else {
            if (to != null) {
                criteria.and(IndexedTransferEvent::to.name).`is`(to)
            }
            if (from != null) {
                criteria.and(IndexedTransferEvent::from.name).`is`(from)
            }
        }

        if (tokenAddress != null) {
            criteria.and(IndexedTransferEvent::tokenAddress.name).`is`(tokenAddress)
        }

        if (eventType != null) {
            criteria.and(IndexedTransferEvent::eventType.name).`is`(eventType)
        }

        if (before != null && after != null) {
            criteria.and(IndexedTransferEvent::blockTimestamp.name).gte(after).lte(before)
        } else if (before != null) {
            criteria.and(IndexedTransferEvent::blockTimestamp.name).lte(before)
        } else if (after != null) {
            criteria.and(IndexedTransferEvent::blockTimestamp.name).gte(after)
        }

        return criteria
    }

    private fun runQuery(criteria: Criteria, pageable: Pageable): Slice<IndexedTransferEvent> {
        val query = Query(criteria).with(pageable)
        query.limit(pageable.pageSize + 1)
        val raw = mongoTemplate.find(query, IndexedTransferEvent::class.java)

        val hasNext = raw.size > pageable.pageSize
        val content = if (hasNext) raw.dropLast(1) else raw

        return SliceImpl(content, pageable, hasNext)
    }
}
