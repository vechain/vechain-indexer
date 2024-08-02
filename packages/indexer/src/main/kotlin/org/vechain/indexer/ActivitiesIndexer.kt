package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedActivity
import org.vechain.indexer.repository.ActivityRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils

@Profile("activities")
@Component
open class ActivitiesIndexer(
    private val activityRepository: ActivityRepository,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    @Value("\${indexer.startBlock.activities}") private val startBlock: Long,
    @Value("\${indexer.syncLoggerInterval.activities}") private val syncLoggerInterval: Long,
) :
    VeWorldIndexer(
        repository = activityRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLoggerInterval = syncLoggerInterval
    ) {

    override fun rollback(blockNumber: Long) {
        activityRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }

    override fun processBlock(block: Block) {
        if (block.transactions.isEmpty()) return

        val activities = BlockUtils.getActivities(block)

        if (activities.isNotEmpty()) mongoTemplate.insert(activities, IndexedActivity::class.java)
    }
}
