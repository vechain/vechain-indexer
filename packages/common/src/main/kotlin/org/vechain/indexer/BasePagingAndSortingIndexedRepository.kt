package org.vechain.indexer

import org.springframework.data.repository.PagingAndSortingRepository

interface BasePagingAndSortingIndexedRepository<T : IndexedDocument, ID> :
    BaseIndexedRepository<T, ID>, PagingAndSortingRepository<T, ID>
