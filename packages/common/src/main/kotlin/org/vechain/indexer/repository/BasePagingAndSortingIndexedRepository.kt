package org.vechain.indexer.repository

import org.springframework.data.repository.PagingAndSortingRepository
import org.vechain.indexer.model.IndexedDocument

interface BasePagingAndSortingIndexedRepository<T : IndexedDocument, ID> :
    BaseIndexedRepository<T, ID>, PagingAndSortingRepository<T, ID>
