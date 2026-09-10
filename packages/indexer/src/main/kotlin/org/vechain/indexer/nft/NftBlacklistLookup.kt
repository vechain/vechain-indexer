package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** The subset of some contracts that `nft_blacklist` flags, matched on the normalised address. */
class BlacklistedContracts(private val ids: Set<String>) {
    operator fun contains(contractAddress: String): Boolean =
        NftBlacklist.buildId(contractAddress) in ids

    companion object {
        val NONE = BlacklistedContracts(emptySet())
    }
}

@Profile("nfts", "history")
@Component
open class NftBlacklistLookup(private val repository: NftBlacklistRepository) {
    open fun blacklisted(contractAddresses: Collection<String>): BlacklistedContracts {
        val ids = contractAddresses.mapTo(mutableSetOf(), NftBlacklist::buildId)
        if (ids.isEmpty()) return BlacklistedContracts.NONE
        return BlacklistedContracts(
            repository.findAllById(ids).filter { it.isBlacklisted }.mapTo(mutableSetOf()) { it.id }
        )
    }
}
