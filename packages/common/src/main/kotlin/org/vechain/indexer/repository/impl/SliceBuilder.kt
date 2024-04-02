package org.vechain.indexer.repository.impl

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl

object SliceBuilder {

    /**
     * Builds a slice of results data with hasNext info about the potential next slice.
     *
     * queryResults is the results performed with a limit of (pageSize + 1) to give an indication
     * whether there are still search results.
     */
    fun <T : Any> buildResultsSlice(queryResults: List<T>, pageable: Pageable): Slice<T> {
        val hasNext: Boolean
        var results: List<T> = emptyList()

        // no results, no next page
        if (queryResults.isEmpty()) hasNext = false
        // more results than page size, there's a next page
        else if (queryResults.size > pageable.pageSize) {
            hasNext = true
            results =
                // in case we overflow the page size, only keep results of the requested page size
                queryResults.toMutableList().slice(0 until pageable.pageSize)
        } else {
            // this is the last results page
            hasNext = false
            results = queryResults
        }

        return SliceImpl(results, pageable, hasNext)
    }
}
