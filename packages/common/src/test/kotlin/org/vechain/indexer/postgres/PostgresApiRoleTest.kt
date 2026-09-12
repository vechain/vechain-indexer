package org.vechain.indexer.postgres

import java.sql.DriverManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource

/** The grant loop as RDS runs it: a non-superuser migrator beside a schema it does not own. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresApiRoleTest {

    private val database = PostgresTestDatabase(apiPassword = "api-secret")

    @BeforeAll
    fun start() {
        database.start()
    }

    @AfterAll fun stop() = database.close()

    @Test
    fun `sync grants every migrated schema and skips one it does not own`() {
        database.jdbc.execute("CREATE ROLE aws NOLOGIN")
        database.jdbc.execute("CREATE SCHEMA rdsadmin AUTHORIZATION aws")
        database.jdbc.execute("CREATE ROLE migrator LOGIN PASSWORD 'migrator' CREATEROLE")
        handMigratorWhatRdsHandsItsMaster()

        PostgresApiRole(migrating(), database.properties).sync()

        DriverManager.getConnection(database.jdbcUrl, PostgresApiRole.ROLE, "api-secret").use { api
            ->
            // public is owned by pg_database_owner, so it survives the filter on the owned
            // database.
            api.createStatement().executeQuery("SELECT count(*) FROM public.indexer_state").use {
                assertEquals(true, it.next())
            }
            api.createStatement().executeQuery("SELECT count(*) FROM blocks.block").use { rs ->
                rs.next()
                assertEquals(0, rs.getInt(1))
            }
        }
    }

    /** The database, the migrated schemas and their tables; the foreign schema stays with aws. */
    private fun handMigratorWhatRdsHandsItsMaster() {
        database.jdbc.execute("ALTER DATABASE ${database.container.databaseName} OWNER TO migrator")
        database.jdbc.execute(
            "DO \$\$ DECLARE r record; BEGIN " +
                "FOR r IN SELECT nspname FROM pg_namespace WHERE nspname = 'blocks' LOOP " +
                "EXECUTE format('ALTER SCHEMA %I OWNER TO migrator', r.nspname); END LOOP; " +
                "FOR r IN SELECT schemaname, tablename FROM pg_tables " +
                "WHERE schemaname IN ('public', 'blocks') LOOP " +
                "EXECUTE format('ALTER TABLE %I.%I OWNER TO migrator', r.schemaname, r.tablename); " +
                "END LOOP; END \$\$"
        )
    }

    private fun migrating(): JdbcTemplate =
        JdbcTemplate(SingleConnectionDataSource(database.jdbcUrl, "migrator", "migrator", true))
}
