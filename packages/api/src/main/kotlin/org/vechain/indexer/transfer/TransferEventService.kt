package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.CursorPaginationUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("transfers")
@Service
open class TransferEventService(
    private val repository: TransferReadRepository,
    private val officialTokenService: OfficialTokenService,
) {

    fun find(
        to: Address? = null,
        from: Address? = null,
        toOrFrom: Address? = null,
        tokenAddress: Address? = null,
        eventTypes: List<TransferEventType>? = null,
        after: Long? = null,
        before: Long? = null,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> =
        offsetSlice(pageable, IndexedTransferEvent::blockTimestamp.name) { offset, limit, direction
            ->
            repository.find(
                to?.value,
                from?.value,
                toOrFrom?.value,
                tokenAddress?.value,
                eventTypes,
                after,
                before,
                offset,
                limit,
                direction,
            )
        }

    fun findByBlockNumber(
        blockNumber: Long,
        addresses: List<Address>,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> =
        offsetSlice(pageable, IndexedTransferEvent::blockNumber.name) { offset, limit, direction ->
            repository.findByBlockNumber(
                blockNumber,
                addresses.map { it.value },
                offset,
                limit,
                direction,
            )
        }

    fun findFungibleTokensContractsByAddress(
        address: Address,
        officialTokensOnly: Boolean,
        pageable: Pageable,
    ): Slice<String> =
        offsetSlice(pageable, FungibleTokenInteraction::blockNumber.name) { offset, limit, direction
            ->
            val contracts =
                if (officialTokensOnly) officialTokenService.getOfficialTokenAddresses() else null
            repository.findInteractedContracts(address.value, contracts, offset, limit, direction)
        }

    /** Cursor is `blockNumber|transferIndex` of the last row served, as before. */
    fun findLatestByType(
        eventTypes: Collection<TransferEventType>,
        size: Int?,
        cursor: String? = null,
    ): PaginatedResponse<IndexedTransferEvent> {
        require(eventTypes.isNotEmpty()) { "eventTypes must not be empty" }
        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val after =
            CursorPaginationUtils.parseCursor(cursor)?.let {
                LatestTransferCursor(
                    it.sortValue.toLongOrNull() ?: throw BadRequestException("Invalid cursor"),
                    it.cursorValue.toLongOrNull() ?: throw BadRequestException("Invalid cursor"),
                )
            }
        val results = repository.findLatest(eventTypes, after, pageSize + 1)
        return paginatedResponse(
            data = results.take(pageSize),
            hasNext = results.size > pageSize,
            cursor =
                CursorPaginationUtils.calculateNextCursor(
                    results = results,
                    pageSize = pageSize,
                    sortByField = IndexedTransferEvent::blockNumber.name,
                    cursorField = IndexedTransferEvent::transferIndex.name,
                ),
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
    }
}
