package org.vechain.indexer.config.postgres

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/** Off only in test contexts that boot without a database (`postgres.enabled=false`). */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "postgres",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
annotation class ConditionalOnPostgres
