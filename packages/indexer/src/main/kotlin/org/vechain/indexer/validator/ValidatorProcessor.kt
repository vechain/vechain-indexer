package org.vechain.indexer.validator

import io.prometheus.metrics.core.metrics.Counter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
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

    private val processerDuration =
        Counter.builder().name("validator-processor-duration-counter").build()

    override fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val start = System.nanoTime()

        val (updated, existing) =
            service.processBlock(
                entry.block,
                entry.events(),
                entry.callResults,
                entry.status == Status.FULLY_SYNCED,
            )

        if (updated.isNotEmpty()) {
            service.save(updated, existing)
        }

        val duration = System.nanoTime() - start
        val durationMS = duration.toDouble() / 1_000_000_000.0
        processerDuration.inc(durationMS)
    }
}
