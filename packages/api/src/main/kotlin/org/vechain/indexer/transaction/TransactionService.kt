package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.CursorPaginationUtils

@Profile("transactions", "transaction")
@Service
open class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val mongoTemplate: MongoTemplate,
) {

    open fun findById(id: String): IndexedTransaction? {
        return transactionRepository.findByIdOrNull(HexUtils.normalise(id))
    }

    open fun findByOriginOrDelegator(
        address: Address,
        includeDelegated: Boolean,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        return if (includeDelegated) {
            transactionRepository.findByOriginOrGasPayer(address.value, address.value, pageable)
        } else {
            transactionRepository.findByOrigin(address.value, pageable)
        }
    }

    open fun findAllDelegated(delegator: Address, pageable: Pageable): Slice<IndexedTransaction> {
        return transactionRepository.findByGasPayerAndOriginNot(
            delegator.value,
            delegator.value,
            pageable,
        )
    }

    open fun findByContractAddress(
        contractAddress: Address,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        return transactionRepository.findByContractAddress(
            HexUtils.normalise(contractAddress.value),
            pageable,
        )
    }

    open fun findByContractAddresses(
        contractAddresses: List<Address>,
        pageable: Pageable,
    ): Slice<IndexedTransaction> {
        val normalizedAddresses = contractAddresses.map { HexUtils.normalise(it.value) }
        return transactionRepository.findByContractAddresses(normalizedAddresses, pageable)
    }

    open fun findLatest(size: Int?, cursor: String? = null): PaginatedResponse<IndexedTransaction> {
        val (pageSize, query) =
            CursorPaginationUtils.buildCursorQuery(
                baseCriteria = Criteria.where(IndexedTransaction::blockNumber.name).exists(true),
                size = size,
                direction = "DESC",
                sortByField = IndexedTransaction::blockNumber.name,
                cursor = cursor,
                cursorField = IndexedTransaction::transactionIndex.name,
                parseCursorFieldValue = true,
            )

        val results = mongoTemplate.find(query, IndexedTransaction::class.java)
        val page = results.take(pageSize)
        val nextCursor =
            CursorPaginationUtils.calculateNextCursor(
                results = results,
                pageSize = pageSize,
                sortByField = IndexedTransaction::blockNumber.name,
                cursorField = IndexedTransaction::transactionIndex.name,
            )

        return paginatedResponse(
            data = page,
            hasNext = results.size > pageSize,
            cursor = nextCursor,
        )
    }
}
