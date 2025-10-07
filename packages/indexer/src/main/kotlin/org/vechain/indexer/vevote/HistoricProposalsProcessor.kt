package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult

@Profile("vevote-historic-proposals")
@Component
open class HistoricProposalsProcessor(
    private val repository: HistoricProposalsRepository,
    private val historicProposalsService: HistoricProposalsService,
) : BaseProcessor(repository = repository) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            historicProposalsService.processNewProposals(emptyList(), entry.latestBlockNumber())
            return
        }
        val proposals: List<HistoricProposals> =
            historicProposalsService.processNewProposals(entry.events(), entry.latestBlockNumber())

        if (proposals.isNotEmpty()) {
            repository.saveAll(proposals)
        }
    }

    override fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
