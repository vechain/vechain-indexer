package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("validator")
@Component
open class ValidatorProcessor(
    repository: ValidatorRepository,
    archiveService: ArchiveService<Validator, ValidatorArchive>,
    private val service: ValidatorService,
) : BaseStatefulProcessor(repository = repository, archiveService = archiveService) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        val (updated, existing, deleted) = service.processBlock(block!!, matchedEvents)

        if (updated.isNotEmpty() || existing.isNotEmpty() || deleted.isNotEmpty()) {
            service.saveAndDelete(updated, existing, deleted)
        }
    }
}
