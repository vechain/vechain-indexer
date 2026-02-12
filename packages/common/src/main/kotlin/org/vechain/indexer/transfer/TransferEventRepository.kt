package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("transfers")
@Repository
interface TransferEventRepository : BaseIndexedRepository<IndexedTransferEvent, String> {
    @Query("{'\$and': [ {'tokenAddress': ?1}, {'\$or': [{'to': ?0}, {'from': ?0}]} ] }")
    fun findByToOrFromAndTokenAddress(
        address: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query(
        "{'\$and': [ {'tokenAddress': ?1}, {'eventType': ?2}, {'\$or': [{'to': ?0}, {'from': ?0}]} ] }"
    )
    fun findByToOrFromAndTokenAddressAndEventType(
        address: String,
        contractAddress: String,
        eventType: TransferEventType,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ '\$or': [{ 'to': ?0 }, { 'from': ?1 }] }")
    fun findByToOrFrom(to: String, from: String, pageable: Pageable): Slice<IndexedTransferEvent>

    @Query("{'\$and': [ {'eventType': ?1}, {'\$or': [{'to': ?0}, {'from': ?0}]} ] }")
    fun findByToOrFromAndEventType(
        address: String,
        eventType: TransferEventType,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ 'tokenAddress': ?0 }")
    fun findByTokenAddress(contractAddress: String, pageable: Pageable): Slice<IndexedTransferEvent>

    @Query("{ 'tokenAddress': ?0, 'eventType': ?1 }")
    fun findByTokenAddressAndEventType(
        contractAddress: String,
        eventType: TransferEventType,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ 'to': ?0, 'tokenAddress': ?1 }")
    fun findByToAndTokenAddress(
        to: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ 'to': ?0, 'tokenAddress': ?1, 'eventType': ?2 }")
    fun findByToAndTokenAddressAndEventType(
        to: String,
        contractAddress: String,
        eventType: TransferEventType,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ 'to': ?0 }") fun findByTo(to: String, pageable: Pageable): Slice<IndexedTransferEvent>

    @Query("{ 'to': ?0, 'eventType': ?1 }")
    fun findByToAndEventType(
        to: String,
        eventType: TransferEventType,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ 'from': ?0 }")
    fun findByFrom(from: String, pageable: Pageable): Slice<IndexedTransferEvent>

    @Query("{ 'from': ?0, 'eventType': ?1 }")
    fun findByFromAndEventType(
        from: String,
        eventType: TransferEventType,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ 'from': ?0, 'tokenAddress': ?1 }")
    fun findByFromAndTokenAddress(
        from: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{ 'from': ?0, 'tokenAddress': ?1, 'eventType': ?2 }")
    fun findByFromAndTokenAddressAndEventType(
        from: String,
        contractAddress: String,
        eventType: TransferEventType,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>

    @Query("{'blockNumber': ?0,'\$or': [ { 'to': {\$in: ?1} }, { 'from': {\$in: ?1} } ] }")
    fun findByBlockNumberAndToOrFromIn(
        blockNumber: Long,
        addresses: List<String>,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent>
}
