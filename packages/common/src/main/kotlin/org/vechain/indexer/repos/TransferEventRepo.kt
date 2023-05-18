package org.vechain.indexer.repos

import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.TransferEvent

@Repository
interface TransferEventRepo : BaseIndexedRepo<TransferEvent>, PagingAndSortingRepository<TransferEvent, String> {
    fun findByToOrFromAndTokenAddress(
        to: String,
        from: String,
        contractAddress: String,
        pageable: Pageable
    ): List<TransferEvent>

    fun findByToOrFrom(to: String, from: String, pageable: Pageable): List<TransferEvent>

    fun findByTokenAddress(contractAddress: String, pageable: Pageable): List<TransferEvent>

    fun findByToAndTokenAddress(to: String, contractAddress: String, pageable: Pageable): List<TransferEvent>

    fun findByTo(to: String, pageable: Pageable): List<TransferEvent>

    fun findByFrom(from: String, pageable: Pageable): List<TransferEvent>

    fun findByFromAndTokenAddress(from: String, contractAddress: String, pageable: Pageable): List<TransferEvent>

}