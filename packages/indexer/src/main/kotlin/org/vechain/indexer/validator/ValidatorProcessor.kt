package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block

@Profile("validator")
@Component
open class ValidatorProcessor(
    repository: ValidatorRepository,
    archiveService: ArchiveService<Validator, ValidatorArchive>,
    private val service: ValidatorService,
    private val thorService: ThorService,
) : BaseStatefulProcessor(repository = repository, archiveService = archiveService) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        val bestBlock = thorService.getBestBlock()
        if (bestBlock.number - (block?.number ?: 0) <= 25) {
            service.refreshValidators(block!!.id, block.number, block.timestamp)
        }

        if (matchedEvents.isEmpty()) return
        service.handleValidatorEvents(matchedEvents)
    }
}
