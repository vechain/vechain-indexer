package org.vechain.indexer.postgres

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresProperties

/**
 * Keeps the read-only `api` role in step with this colour's `PG_API_PASSWORD` on every start, so a
 * snapshot restored from the other colour serves the target colour's API at once. Runs after
 * Flyway, on the migrating side only.
 */
@Component
@ConditionalOnPostgres
@ConditionalOnProperty(prefix = "postgres.flyway", name = ["enabled"], havingValue = "true")
open class PostgresApiRole(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate,
    private val properties: PostgresProperties,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    open fun sync() {
        if (properties.apiPassword.isBlank()) {
            logger.info("PG_API_PASSWORD is not set; leaving the api role unchanged")
            return
        }
        jdbc.execute(
            "DO \$\$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ROLE') " +
                "THEN CREATE ROLE $ROLE LOGIN; END IF; END \$\$"
        )
        // format(%L) quotes the password server-side; ALTER ROLE cannot take a bind parameter.
        val alter =
            jdbc.queryForObject(
                "SELECT format('ALTER ROLE $ROLE WITH LOGIN PASSWORD %L', ?)",
                String::class.java,
                properties.apiPassword,
            )
        jdbc.execute(alter!!)
        jdbc.execute("GRANT USAGE ON SCHEMA public TO $ROLE")
        jdbc.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO $ROLE")
        jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO $ROLE")
        logger.info("Synced the {} role's password and grants", ROLE)
    }

    companion object {
        const val ROLE = "api"
    }
}
