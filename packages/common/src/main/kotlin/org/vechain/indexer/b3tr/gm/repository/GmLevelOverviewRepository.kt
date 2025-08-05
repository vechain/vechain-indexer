package org.vechain.indexer.b3tr.gm.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.gm.GmLevelOverview

@Profile("b3tr", "galaxy-member")
@Repository
interface GmLevelOverviewRepository :
    BasePagingAndSortingIndexedRepository<GmLevelOverview, String>
