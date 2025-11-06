package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("validator", "validator-stats")
@Component
open class ValidatorProcessor(
    repository: ValidatorRepository,
    archiveService: ArchiveService<Validator, ValidatorArchive>,
    private val service: ValidatorService,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerVersionService = indexerVersionService,
        IndexerNames.VALIDATOR,
    ) {
    override fun process(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val (updated, existing) =
            service.processBlock(entry.block, entry.events(), entry.callResults)

        if (updated.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
