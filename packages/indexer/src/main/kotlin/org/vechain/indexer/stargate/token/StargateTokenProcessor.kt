package org.vechain.indexer.stargate.token

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "stargate-token")
@Component
open class StargateTokenProcessor(
    private val service: StargateTokenService,
    stargateTokenRepository: StargateTokenRepository,
    archiveService: ArchiveService<StargateToken, StargateTokenArchive>,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = stargateTokenRepository,
        archiveService = archiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.STARGATE_TOKEN,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val updates = service.processBlock(entry.block, entry.callResults, entry.events())

        withContext(Dispatchers.IO) { service.save(updates.first, updates.second) }
    }
}
