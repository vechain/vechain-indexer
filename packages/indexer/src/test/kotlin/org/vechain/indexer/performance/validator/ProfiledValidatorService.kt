package org.vechain.indexer.performance.validator

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorRepository
import org.vechain.indexer.validator.ValidatorService

/** Extended ValidatorService that profiles the main processing and save steps. */
class ProfiledValidatorService(
    repository: ValidatorRepository,
    delegationRepository: DelegationRepository,
    mongoTemplate: MongoTemplate,
    inlineVersioningProperties: InlineVersioningProperties,
    private val profiler: DetailedProfiler,
) : ValidatorService(repository, delegationRepository, mongoTemplate, inlineVersioningProperties) {

    override fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Validator>, List<Validator>> {
        return profiler.time("      ValidatorService.processBlock") {
            super.processBlock(block, matchedEvents, callResponses)
        }
    }

    override fun save(updates: List<Validator>, archive: List<Validator>) {
        profiler.time("      ValidatorService.save (MongoDB)") { super.save(updates, archive) }
    }
}
