package org.vechain.indexer.config.postgres

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "postgres")
data class PostgresProperties(
    val url: String,
    val username: String,
    val password: String,
    val flyway: Flyway = Flyway(),
    val pool: Pool = Pool(),
) {
    init {
        require(url.isNotBlank()) {
            "PG_URL is required (postgres.url), e.g. jdbc:postgresql://host:5432/vechain"
        }
    }

    data class Flyway(val enabled: Boolean = true)

    data class Pool(val maximumSize: Int = 10)
}
