package org.vechain.indexer.pageable

/**
 * All pageable parameters.
 */
@PageablePage
@PageableSize
@PageableSortFields
@PageableSortDirection
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PageableAll
