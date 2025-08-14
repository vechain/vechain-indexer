package org.vechain.indexer.historical.vote_tally

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("historical-proposals")
@Repository
interface VoteTallyRepository : BaseIndexedRepository<VoteTally, String> {}
