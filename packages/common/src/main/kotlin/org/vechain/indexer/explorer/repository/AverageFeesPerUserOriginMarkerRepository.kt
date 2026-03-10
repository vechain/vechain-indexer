package org.vechain.indexer.explorer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.explorer.AverageFeesPerUserOriginMarker

@Profile("explorer", "average-fees-per-user")
@Repository
interface AverageFeesPerUserOriginMarkerRepository :
    BaseIndexedRepository<AverageFeesPerUserOriginMarker, String>
