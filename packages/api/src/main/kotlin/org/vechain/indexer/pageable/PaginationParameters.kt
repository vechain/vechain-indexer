package org.vechain.indexer.pageable

/**
 * All pageable parameters.
 */
@PaginationPage
@PaginationSize
@PaginationSortDirection
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PaginationParameters
