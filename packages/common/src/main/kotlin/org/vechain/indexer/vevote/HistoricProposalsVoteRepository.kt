package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("vevote", "vevote-historic-proposals")
@Repository
interface HistoricProposalsVoteRepository : BaseIndexedRepository<HistoricProposalsVote, String>
