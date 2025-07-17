package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedNft
import org.vechain.indexer.repository.NFTRepository

@Profile("nfts")
@Service
open class NFTService(private val nftRepository: NFTRepository) {

    open fun findOwnedNFTs(
        owner: Address,
        contractAddress: Address?,
        tokenId: String?,
        pageable: Pageable,
    ): Slice<IndexedNft> {
        return if (contractAddress != null) {
            return if (!tokenId.isNullOrEmpty()) {
                nftRepository.findByOwnerAndContractAddressAndTokenId(
                    owner.value,
                    contractAddress.value,
                    tokenId,
                    pageable,
                )
            } else {
                nftRepository.findByOwnerAndContractAddress(
                    owner.value,
                    contractAddress.value,
                    pageable,
                )
            }
        } else {
            nftRepository.findByOwner(owner.value, pageable)
        }
    }

    open fun findContractsByNFTOwner(owner: Address, pageable: Pageable): Slice<String> {
        return nftRepository.findContractsByNFTOwner(owner.value, pageable)
    }
}
