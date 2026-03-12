package org.vechain.indexer.explorer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.explorer.AverageFeesPerUser
import org.vechain.indexer.explorer.AverageFeesPerUserRecordType

@Profile("explorer", "average-fees-per-user")
@Repository
interface AverageFeesPerUserRepository : BaseIndexedRepository<AverageFeesPerUser, String> {
    @Query(
        value = "{ 'recordType': ?0, 'dayStartTimestamp': { '\$gte': ?1, '\$lte': ?2 } }",
        sort = "{ 'dayStartTimestamp': 1 }",
    )
    fun findAllByRecordTypeAndDayStartTimestampBetween(
        recordType: AverageFeesPerUserRecordType,
        startDayStartTimestamp: Long,
        endDayStartTimestamp: Long,
    ): List<AverageFeesPerUser>
}
