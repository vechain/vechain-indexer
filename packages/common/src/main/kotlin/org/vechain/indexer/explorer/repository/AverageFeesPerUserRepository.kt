package org.vechain.indexer.explorer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.explorer.AverageFeesPerUser

@Profile("explorer", "average-fees-per-user")
@Repository
interface AverageFeesPerUserRepository : BaseIndexedRepository<AverageFeesPerUser, String> {
    @Query(
        value = "{ 'dayStartTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'dayStartTimestamp': 1 }",
    )
    fun findAllInDayRange(
        startDayStartTimestamp: Long,
        endDayStartTimestamp: Long,
    ): List<AverageFeesPerUser>
}
