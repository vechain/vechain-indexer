package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedTransferEvent

@Profile("transfer-events")
@Repository
interface TransferEventRepo : BaseIndexedRepo<IndexedTransferEvent>,
    PagingAndSortingRepository<IndexedTransferEvent, String> {
    fun findByToOrFromAndTokenAddress(
        to: String,
        from: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent>

    fun findByToOrFrom(to: String, from: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByTokenAddress(contractAddress: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByToAndTokenAddress(to: String, contractAddress: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByTo(to: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByFrom(from: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByFromAndTokenAddress(from: String, contractAddress: String, pageable: Pageable): Page<IndexedTransferEvent>

}
