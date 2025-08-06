package org.vechain.indexer.b3tr.gm.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.gm.GmNft

@Profile("b3tr", "gm-nft")
@Repository
interface GmNftRepository : BasePagingAndSortingIndexedRepository<GmNft, String> {
    fun findAllByAttachedNodeIdNotNull(): List<GmNft>
}
