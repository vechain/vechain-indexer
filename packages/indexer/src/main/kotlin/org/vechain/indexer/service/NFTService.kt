package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.IdUtils
import org.web3j.utils.Numeric

@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepository: NFTRepository,
    private val nftArchiveService: ArchiveService<IndexedNFT, NFTArchive>
) {

    @Transactional(rollbackFor = [Exception::class])
    open fun update(updated: List<IndexedNFT>, existing: List<IndexedNFT>) {
        if (updated.isNotEmpty()) {
            nftRepository.saveAll(updated)
        }

        if (existing.isNotEmpty()) {
            nftArchiveService.saveAll(existing)
        }
    }

    open fun parseRecords(
        block: Block,
        data: List<IndexedTransferEvent>,
        existing: List<IndexedNFT>
    ): List<IndexedNFT> {
        return data.map {
            val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])
            val nftId = IdUtils.buildNftId(it)
            val version = existing.find { nft -> nft.id == nftId }?.version?.plus(1) ?: 1

            IndexedNFT(
                id = nftId,
                version = version,
                owner = it.to,
                contractAddress = it.tokenAddress!!,
                tokenId = tokenId.toString(10),
                txId = it.txId,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )
        }
    }

    open fun getExisting(nftTransfers: List<IndexedTransferEvent>): List<IndexedNFT> {
        return nftRepository.findAllById(nftTransfers.map { IdUtils.buildNftId(it) }).toList()
    }
}
