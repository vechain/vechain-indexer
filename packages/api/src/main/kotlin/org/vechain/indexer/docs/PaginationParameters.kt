package org.vechain.indexer.docs

/** All pageable parameters. */
@PaginationPage
@PaginationSize
@PaginationSortDirection
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class PaginationParameters
