package org.vechain.indexer.b3tr.gm

import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("b3tr", "b3tr-gm-nft")
@Service
open class GmNftService(private val repository: GmNftReadRepository) {

    open fun levelOverviews(): List<GMLevelOverview> = repository.levelCounts()

    @Cacheable(value = ["gmNft_countByLevelAndOwnerNot"], key = "#level")
    open fun levelOverview(level: GmLevelName): GMLevelOverview =
        GMLevelOverview(level, repository.countByLevel(level))
}
