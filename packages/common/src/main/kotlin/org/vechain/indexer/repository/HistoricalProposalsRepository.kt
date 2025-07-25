package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.HistoricalProposals

@Profile("historical-proposals")
@Repository
interface HistoricalProposalsRepository : BaseIndexedRepository<HistoricalProposals, String> {}
