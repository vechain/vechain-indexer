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
 * Flyway, on the migrating side only, and grants every schema Flyway has created.
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
        for (schema in schemas()) {
            jdbc.execute("GRANT USAGE ON SCHEMA $schema TO $ROLE")
            jdbc.execute("GRANT SELECT ON ALL TABLES IN SCHEMA $schema TO $ROLE")
            jdbc.execute(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA $schema GRANT SELECT ON TABLES TO $ROLE"
            )
        }
        logger.info("Synced the {} role's password and grants", ROLE)
    }

    /** Every schema Flyway has created: public and one per indexer. */
    private fun schemas(): List<String> =
        jdbc.queryForList(
            // pg_has_role skips a schema owned by someone else, whose GRANT would throw in here.
            "SELECT nspname FROM pg_namespace WHERE nspname NOT LIKE 'pg\\_%' " +
                "AND nspname <> 'information_schema' " +
                "AND pg_has_role(current_user, nspowner, 'USAGE') ORDER BY nspname",
            String::class.java,
        )

    companion object {
        const val ROLE = "api"
    }
}
