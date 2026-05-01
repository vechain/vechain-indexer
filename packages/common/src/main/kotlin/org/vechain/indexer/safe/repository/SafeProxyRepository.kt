package org.vechain.indexer.safe.repository

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.safe.SafeProxy

@Profile("safe") interface SafeProxyRepository : BaseIndexedRepository<SafeProxy, String>
