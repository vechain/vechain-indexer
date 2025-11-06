package org.vechain.indexer.docs

@PaginationSize
@PaginationSortDirection
@Cursor
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class CursorPaginationParameters()
