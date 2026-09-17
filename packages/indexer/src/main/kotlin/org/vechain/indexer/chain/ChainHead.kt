package org.vechain.indexer.chain

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision

/** The best block Thor knows, polled for every reader; null until a poll lands or one fails. */
@Component
class ChainHead(private val thorClient: ThorClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Volatile private var best: Long? = null

    fun bestBlockNumber(): Long? = best

    @Scheduled(fixedDelayString = "\${indexer.healthcheck.report-interval-ms:10000}")
    fun refresh() {
        best =
            try {
                runBlocking { thorClient.getBlockUnexpanded(BlockRevision.Keyword.BEST).number }
            } catch (e: Exception) {
                logger.warn(
                    "Failed to fetch the best block for revision {}",
                    BlockRevision.Keyword.BEST,
                    e,
                )
                null
            }
    }
}
