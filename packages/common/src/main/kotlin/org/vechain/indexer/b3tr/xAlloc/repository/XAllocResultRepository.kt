package org.vechain.indexer.b3tr.xAlloc.repository

import org.vechain.indexer.b3tr.xAlloc.XAllocResult
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface XAllocResultRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(updated: List<XAllocResult>, existing: List<XAllocResult>)

    // Query operations
    fun findByRoundId(roundId: Int): List<XAllocResult>

    fun findByAppId(appId: String): List<XAllocResult>

    fun findByAppIdAndRoundId(appId: String, roundId: Int): XAllocResult?

    fun findById(id: String): XAllocResult?

    fun getLatestRecord(): XAllocResult?
}
