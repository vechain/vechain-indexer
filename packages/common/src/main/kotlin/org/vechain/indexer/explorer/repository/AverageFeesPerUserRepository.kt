package org.vechain.indexer.explorer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.explorer.AverageFeesPerUser
import org.vechain.indexer.explorer.AverageFeesPerUserRecordType

@Profile("explorer", "average-fees-per-user")
@Repository
interface AverageFeesPerUserRepository : BaseIndexedRepository<AverageFeesPerUser, String> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'recordType': 'SUMMARY', 'dayStartTimestamp': { '\$gte': ?0, '\$lte': ?1 } } }",
                "{ '\$sort': { 'dayStartTimestamp': 1, 'blockNumber': -1 } }",
                "{ '\$group': { '_id': '\$date', 'doc': { '\$first': '\$\$ROOT' } } }",
                "{ '\$replaceRoot': { 'newRoot': '\$doc' } }",
                "{ '\$sort': { 'dayStartTimestamp': 1 } }",
            ]
    )
    fun findAllInDayRange(
        startDayStartTimestamp: Long,
        endDayStartTimestamp: Long,
    ): List<AverageFeesPerUser>

    fun findFirstByRecordTypeAndDateAndBlockNumberLessThanOrderByBlockNumberDesc(
        recordType: AverageFeesPerUserRecordType,
        date: String,
        blockNumber: Long,
    ): AverageFeesPerUser?
}
