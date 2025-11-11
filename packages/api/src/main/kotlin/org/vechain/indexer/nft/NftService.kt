package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.BigIntegerUtils

@Profile("nfts")
@Service
open class NftService(private val nftRepository: NftRepository) {

    open fun findOwnedNfts(
        owner: Address,
        contractAddress: Address?,
        tokenId: String?,
        excludeCollections: List<Address>?,
        pageable: Pageable,
    ): Slice<IndexedNft> {
        val excludeCollectionsList = excludeCollections?.map { it.value } ?: emptyList()
        val parsedTokenId = tokenId?.let { BigIntegerUtils.fromHexOrDecimal(it).toString(10) }
        return if (contractAddress != null) {
            return if (!parsedTokenId.isNullOrEmpty()) {
                nftRepository.findByOwnerAndContractAddressAndTokenId(
                    owner.value,
                    contractAddress.value,
                    parsedTokenId,
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
            nftRepository.findByOwner(owner.value, excludeCollectionsList, pageable)
        }
    }

    open fun findContractsByNftOwner(
        owner: Address,
        excludeCollections: List<Address>?,
        pageable: Pageable,
    ): Slice<String> {
        val excludeCollectionsList = excludeCollections?.map { it.value } ?: emptyList()
        return nftRepository.findContractsByNftOwner(owner.value, excludeCollectionsList, pageable)
    }
}
