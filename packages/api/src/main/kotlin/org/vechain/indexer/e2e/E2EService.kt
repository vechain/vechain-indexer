package org.vechain.indexer.e2e

import org.jetbrains.annotations.TestOnly
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.nft.NftReadRepository
import org.vechain.indexer.transfer.IndexedTransferEvent
import org.vechain.indexer.transfer.TransferEventType
import org.vechain.indexer.transfer.TransferReadRepository

@Profile("e2e")
@Service
open class E2EService(
    private val transferRepository: TransferReadRepository,
    private val nftRepository: NftReadRepository,
) {

    @TestOnly
    open fun getNftTransfers(): List<IndexedTransferEvent> =
        transferRepository.findAllByEventType(TransferEventType.NFT)

    @TestOnly open fun getNfts(): List<IndexedNft> = nftRepository.findAll()
}
