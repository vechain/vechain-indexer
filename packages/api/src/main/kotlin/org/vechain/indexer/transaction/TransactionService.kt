package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort.Direction
import org.springframework.stereotype.Service
import org.vechain.indexer.blocks.BlocksReadRepository
import org.vechain.indexer.blocks.BlocksReadRepository.LatestCursor
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.CursorPaginationUtils

@Profile("blocks")
@Service
open class TransactionService(private val repository: BlocksReadRepository) {

    open fun findById(id: String): IndexedTransaction? = repository.findById(id)

    open fun findByOriginOrDelegator(
        address: Address,
        includeDelegated: Boolean,
        pageable: Pageable,
        expanded: Boolean,
    ): Slice<IndexedTransaction> =
        page(pageable) { offset, limit, direction ->
            repository.findByOrigin(
                address.value,
                includeDelegated,
                offset,
                limit,
                direction,
                expanded,
            )
        }

    open fun findAllDelegated(
        delegator: Address,
        pageable: Pageable,
        expanded: Boolean,
    ): Slice<IndexedTransaction> =
        page(pageable) { offset, limit, direction ->
            repository.findDelegated(delegator.value, offset, limit, direction, expanded)
        }

    open fun findByContractAddress(
        contractAddress: Address,
        pageable: Pageable,
        expanded: Boolean,
    ): Slice<IndexedTransaction> =
        page(pageable) { offset, limit, direction ->
            repository.findByContract(contractAddress.value, offset, limit, direction, expanded)
        }

    /** Cursor is `blockNumber|transactionIndex` of the last row served, as before. */
    open fun findLatest(size: Int?, cursor: String? = null): PaginatedResponse<IndexedTransaction> {
        val pageSize = size ?: DEFAULT_PAGE_SIZE
        val after =
            CursorPaginationUtils.parseCursor(cursor)?.let {
                LatestCursor(it.sortValue.toLong(), it.cursorValue.toInt())
            }
        val results = repository.findLatest(after, pageSize + 1)

        return paginatedResponse(
            data = results.take(pageSize),
            hasNext = results.size > pageSize,
            cursor =
                CursorPaginationUtils.calculateNextCursor(
                    results = results,
                    pageSize = pageSize,
                    sortByField = IndexedTransaction::blockNumber.name,
                    cursorField = IndexedTransaction::transactionIndex.name,
                ),
        )
    }

    /** Offset paging as the Mongo repositories did it: one row past the page decides hasNext. */
    private fun page(
        pageable: Pageable,
        fetch: (offset: Long, limit: Int, direction: Direction) -> List<IndexedTransaction>,
    ): Slice<IndexedTransaction> {
        val direction =
            pageable.sort.getOrderFor(IndexedTransaction::blockNumber.name)?.direction
                ?: Direction.DESC
        val rows = fetch(pageable.offset, pageable.pageSize + 1, direction)
        return SliceImpl(rows.take(pageable.pageSize), pageable, rows.size > pageable.pageSize)
    }
}
