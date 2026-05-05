package org.vechain.indexer.b3tr.relayer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.relayer.AutoVotingToggle

@Profile("b3tr", "b3tr-auto-voting-toggles")
@Repository
interface AutoVotingToggleRepository : BaseIndexedRepository<AutoVotingToggle, String>
