package org.vechain.indexer.utils

object SortUtils {
    fun <T> List<T>.isSortedWith(comparator: (T, T) -> Boolean): Boolean =
        this.zipWithNext().all { (a, b) -> comparator(a, b) }
}
