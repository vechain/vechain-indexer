package org.vechain.indexer.stargate.token

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "stargate-token")
@Component
open class StargateTokenProcessor(
    private val service: StargateTokenService,
    stargateTokenRepository: StargateTokenRepository,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = stargateTokenRepository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.STARGATE_TOKEN,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val (updated, existing) =
            service.processBlock(entry.block, entry.callResults, entry.events())

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
