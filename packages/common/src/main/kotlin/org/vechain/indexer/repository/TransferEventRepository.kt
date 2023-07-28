package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedTransferEvent

@Profile("transfer-events")
@Repository
interface TransferEventRepository : BaseIndexedRepository<IndexedTransferEvent> {
    fun findByToOrFromAndTokenAddress(
        address: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent>

    fun findByToOrFrom(address: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByTokenAddress(contractAddress: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByToAndTokenAddress(
        to: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent>

    fun findByTo(to: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByFrom(from: String, pageable: Pageable): Page<IndexedTransferEvent>

    fun findByFromAndTokenAddress(
        from: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedTransferEvent>

    @Query("{'blockNumber': ?0,'\$or': [ { 'to': {\$in: ?1} }, { 'from': {\$in: ?1} } ] }")
    fun findByBlockNumberAndToOrFromIn(
        blockNumber: Long,
        addresses: List<String>,
        pageable: Pageable
    ): Page<IndexedTransferEvent>

    fun findFungibleTokensContractsByAddress(address: String, pageable: Pageable): Page<String>
}
