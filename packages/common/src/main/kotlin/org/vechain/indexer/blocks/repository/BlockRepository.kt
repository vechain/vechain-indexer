package org.vechain.indexer.blocks.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.blocks.IndexedBlock

// Range on the numeric blockNumber, never on _id: _id is a string, so "100" sorts below "99".
@Repository
interface BlockRepository : BaseIndexedRepository<IndexedBlock, String> {

    @Query(value = "{ 'blockNumber': { \$exists: true } }", sort = "{ 'blockNumber': -1 }")
    fun findLatest(pageable: Pageable): Slice<IndexedBlock>

    @Query(value = "{ 'blockNumber': { \$lte: ?0 } }", sort = "{ 'blockNumber': -1 }")
    fun findAtOrBelow(blockNumber: Long, pageable: Pageable): Slice<IndexedBlock>
}
