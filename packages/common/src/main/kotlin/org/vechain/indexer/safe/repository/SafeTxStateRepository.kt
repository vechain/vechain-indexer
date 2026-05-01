package org.vechain.indexer.safe.repository

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.safe.SafeTxState

@Profile("safe") interface SafeTxStateRepository : BaseIndexedRepository<SafeTxState, String>
