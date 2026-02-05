package org.vechain.indexer.config

import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.BlockUnexpanded
import org.vechain.indexer.utils.TtlCache

/**
 * Thor client that wraps [DefaultThorClient] and caches "best block" queries.
 *
 * Calls for [BlockRevision.Keyword.BEST] are served from an in-memory cache
 * backed by [TtlCache], reducing repeated best-block requests and therefore
 * lowering load on the Thor API. Cached entries have a time-to-live (TTL)
 * of 10 seconds before they are refreshed from the underlying client.
 */
class CachingThorClient(baseUrl: String, vararg headers: Pair<String, Any>) :
    DefaultThorClient(baseUrl, *headers) {

    private val bestBlockCache = TtlCache<Block>()
    private val bestBlockUnexpandedCache = TtlCache<BlockUnexpanded>()

    override suspend fun getBlock(revision: BlockRevision): Block {
        return if (revision == BlockRevision.Keyword.BEST) {
            bestBlockCache.getOrFetch { super.getBlock(revision) }
        } else {
            super.getBlock(revision)
        }
    }

    override suspend fun getBlockUnexpanded(revision: BlockRevision): BlockUnexpanded {
        return if (revision == BlockRevision.Keyword.BEST) {
            bestBlockUnexpandedCache.getOrFetch { super.getBlockUnexpanded(revision) }
        } else {
            super.getBlockUnexpanded(revision)
        }
    }
}
