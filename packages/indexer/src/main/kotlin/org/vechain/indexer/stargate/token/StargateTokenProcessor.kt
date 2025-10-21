package org.vechain.indexer.stargate.token

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.stargate.StargateToken
import org.vechain.indexer.stargate.StargateTokenArchive
import org.vechain.indexer.stargate.StargateTokenRepository
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
    override fun process(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val updates = service.processBlock(entry.block, entry.callResults, entry.events())

        service.save(updates.first, updates.second)
    }
}
