package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
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
    ): List<IndexedTransferEvent>

    fun findByToOrFrom(to: String, from: String, pageable: Pageable): List<IndexedTransferEvent>

    fun findByTokenAddress(contractAddress: String, pageable: Pageable): List<IndexedTransferEvent>

    fun findByToAndTokenAddress(to: String, contractAddress: String, pageable: Pageable): List<IndexedTransferEvent>

    fun findByTo(to: String, pageable: Pageable): List<IndexedTransferEvent>

    fun findByFrom(from: String, pageable: Pageable): List<IndexedTransferEvent>

    fun findByFromAndTokenAddress(from: String, contractAddress: String, pageable: Pageable): List<IndexedTransferEvent>

}
