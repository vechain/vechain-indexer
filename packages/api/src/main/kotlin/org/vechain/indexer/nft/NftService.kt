package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.BigIntegerUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("nfts")
@Service
open class NftService(private val repository: NftReadRepository) {

    open fun findOwnedNfts(
        owner: Address,
        contractAddress: Address?,
        tokenId: String?,
        excludeCollections: List<Address>?,
        pageable: Pageable,
    ): Slice<IndexedNft> {
        val parsedTokenId =
            tokenId
                ?.takeIf { it.isNotEmpty() }
                ?.let { BigIntegerUtils.fromHexOrDecimal(it).toString(10) }
        return offsetSlice(pageable, IndexedNft::blockNumber.name) { offset, limit, direction ->
            if (contractAddress != null) {
                repository.findByOwnerAndContract(
                    owner.value,
                    contractAddress.value,
                    parsedTokenId,
                    offset,
                    limit,
                    direction,
                )
            } else {
                repository.findByOwner(
                    owner.value,
                    excludeCollections.orEmpty().map { it.value },
                    offset,
                    limit,
                    direction,
                )
            }
        }
    }

    open fun findContractsByNftOwner(
        owner: Address,
        excludeCollections: List<Address>?,
        pageable: Pageable,
    ): Slice<String> =
        offsetSlice(pageable, IndexedNft::blockNumber.name) { offset, limit, direction ->
            repository.findContractsByOwner(
                owner.value,
                excludeCollections.orEmpty().map { it.value },
                offset,
                limit,
                direction,
            )
        }
}
