package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.CountRepository
import org.vechain.indexer.repository.TransferEventRepo
import org.vechain.indexer.utils.HexUtils

@Profile("transfer-events")
@Service
open class TransferEventService(
    private val transferEventRepo: TransferEventRepo,
    private val countRepository: CountRepository
) {

    fun find(address: String, tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val addressNorm = HexUtils.normalise(address)
        val tokenAddressNorm = HexUtils.normalise(tokenAddress)

        val slice =
            transferEventRepo.findByToOrFromAndTokenAddress(addressNorm, addressNorm, tokenAddressNorm, pageable)
        val count = countRepository.getCount(
            TRANSFERS_COLLECTION,
            listOf(
                MatchOperation(Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)),
                MatchOperation(
                    Criteria.where("").orOperator(Criteria.where(TO).`is`(address), Criteria.where(FROM).`is`(address))
                )
            )
        )

        return PageImpl(slice.content, pageable, count)
    }

    fun findByAddress(address: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val addressNorm = HexUtils.normalise(address)

        val slice = transferEventRepo.findByToOrFrom(addressNorm, addressNorm, pageable)
        val count = countRepository.getCount(
            TRANSFERS_COLLECTION,
            listOf(
                MatchOperation(
                    Criteria.where("").orOperator(Criteria.where(TO).`is`(address), Criteria.where(FROM).`is`(address))
                )
            )
        )

        return PageImpl(slice.content, pageable, count)
    }

    fun findByTokenAddress(tokenAddress: String, pageable: Pageable): Page<IndexedTransferEvent> {
        val tokenAddressNorm = HexUtils.normalise(tokenAddress)

        val slice = transferEventRepo.findByTokenAddress(tokenAddressNorm, pageable)
        val count = countRepository.getCount(
            TRANSFERS_COLLECTION,
            listOf(MatchOperation(Criteria.where(TOKEN_ADDRESS).`is`(tokenAddress)))
        )

        return PageImpl(slice.content, pageable, count)
    }

    fun findByTo(to: String, tokenAddress: String?, pageable: Pageable): Page<IndexedTransferEvent> {
        val toNorm = HexUtils.normalise(to)
        val slice: Slice<IndexedTransferEvent>
        val matchOperations = mutableListOf<MatchOperation>()

        if (tokenAddress != null) {
            val tokenAddressNorm = HexUtils.normalise(tokenAddress)
            slice = transferEventRepo.findByToAndTokenAddress(toNorm, tokenAddressNorm, pageable)
            matchOperations.add(MatchOperation(Criteria.where(TO).`is`(toNorm)))
            matchOperations.add(MatchOperation(Criteria.where(TOKEN_ADDRESS).`is`(tokenAddressNorm)))
        } else {
            slice = transferEventRepo.findByTo(toNorm, pageable)
            matchOperations.add(MatchOperation(Criteria.where(TO).`is`(toNorm)))
        }
        val count = countRepository.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(slice.content, pageable, count)
    }

    fun findByFrom(from: String, tokenAddress: String?, pageable: Pageable): Page<IndexedTransferEvent> {
        val fromNorm = HexUtils.normalise(from)
        val slice: Slice<IndexedTransferEvent>
        val matchOperations = mutableListOf<MatchOperation>()

        if (tokenAddress != null) {
            val tokenAddressNorm = HexUtils.normalise(tokenAddress)
            slice = transferEventRepo.findByFromAndTokenAddress(fromNorm, tokenAddressNorm, pageable)
            matchOperations.add(MatchOperation(Criteria.where(FROM).`is`(fromNorm)))
            matchOperations.add(MatchOperation(Criteria.where(TOKEN_ADDRESS).`is`(tokenAddressNorm)))
        } else {
            slice = transferEventRepo.findByFrom(fromNorm, pageable)
            matchOperations.add(MatchOperation(Criteria.where(FROM).`is`(fromNorm)))
        }
        val count = countRepository.getCount(TRANSFERS_COLLECTION, matchOperations)

        return PageImpl(slice.content, pageable, count)
    }

    companion object {
        val TRANSFERS_COLLECTION = IndexedTransferEvent::class.java
        val TO = IndexedTransferEvent::to.name
        val FROM = IndexedTransferEvent::from.name
        val TOKEN_ADDRESS = IndexedTransferEvent::tokenAddress.name
    }
}
