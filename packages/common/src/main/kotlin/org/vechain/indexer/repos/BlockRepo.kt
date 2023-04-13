package org.vechain.indexer.repos

import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Block

@Repository
interface BlockRepo : IndexerRepository, PagingAndSortingRepository<Block, String>, CrudRepository<Block, String>