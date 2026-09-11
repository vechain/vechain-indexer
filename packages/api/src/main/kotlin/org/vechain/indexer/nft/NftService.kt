package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort.Direction
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.BigIntegerUtils

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
        return page(pageable) { offset, limit, direction ->
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
        page(pageable) { offset, limit, direction ->
            repository.findContractsByOwner(
                owner.value,
                excludeCollections.orEmpty().map { it.value },
                offset,
                limit,
                direction,
            )
        }

    /** Offset paging as the Mongo repository did it: one row past the page decides hasNext. */
    private fun <T> page(
        pageable: Pageable,
        fetch: (offset: Long, limit: Int, direction: Direction) -> List<T>,
    ): Slice<T> {
        val direction =
            pageable.sort.getOrderFor(IndexedNft::blockNumber.name)?.direction ?: Direction.DESC
        val rows = fetch(pageable.offset, pageable.pageSize + 1, direction)
        return SliceImpl(rows.take(pageable.pageSize), pageable, rows.size > pageable.pageSize)
    }
}
