package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.TransferEvent

@Repository
interface TransferEventRepo : BaseIndexedRepo<TransferEvent>, PagingAndSortingRepository<TransferEvent, String> {
    fun findByToOrFromAndTokenAddress(
        to: String,
        from: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<TransferEvent>

    fun findByToOrFrom(to: String, from: String, pageable: Pageable): Page<TransferEvent>

    fun findByTokenAddress(contractAddress: String, pageable: Pageable): Page<TransferEvent>

}