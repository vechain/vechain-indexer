package org.vechain.indexer.transfer

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface TransferEventRepository : PostgresIndexedRepository {

    fun saveAll(events: List<IndexedTransferEvent>)

    fun findByToOrFromAndTokenAddress(
        address: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    fun findByToOrFrom(to: String, from: String, pageable: Pageable): Slice<IndexedTransferEvent>

    fun findByTokenAddress(contractAddress: String, pageable: Pageable): Slice<IndexedTransferEvent>

    fun findByToAndTokenAddress(
        to: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    fun findByTo(to: String, pageable: Pageable): Slice<IndexedTransferEvent>

    fun findByFrom(from: String, pageable: Pageable): Slice<IndexedTransferEvent>

    fun findByFromAndTokenAddress(
        from: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    fun findByBlockNumberAndToOrFromIn(
        blockNumber: Long,
        addresses: List<String>,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>
}
