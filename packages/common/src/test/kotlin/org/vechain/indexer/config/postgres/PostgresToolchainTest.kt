package org.vechain.indexer.config.postgres

import com.zaxxer.hikari.HikariDataSource
import java.sql.SQLException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/** Smoke test for the toolchain: driver, pool, Flyway and JdbcClient against a real Postgres. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresToolchainTest {

    private val postgres = PostgreSQLContainer("postgres:16")
    private val config = PostgresConfig()
    private lateinit var dataSource: HikariDataSource

    @BeforeAll
    fun start() {
        postgres.start()
        dataSource =
            config.postgresDataSource(
                PostgresProperties(
                    url = postgres.jdbcUrl,
                    username = postgres.username,
                    password = postgres.password,
                )
            ) as HikariDataSource
    }

    @AfterAll
    fun stop() {
        dataSource.close()
        postgres.stop()
    }

    private fun jdbcClient() =
        config.postgresJdbcClient(config.postgresJdbcTemplate(dataSource, flywayProvider()))

    private fun flywayProvider(): ObjectProvider<org.flywaydb.core.Flyway> {
        val flyway = config.postgresFlyway(dataSource)
        flyway.migrate()
        return object : ObjectProvider<org.flywaydb.core.Flyway> {
            override fun getObject(vararg args: Any?) = flyway

            override fun getIfAvailable() = flyway

            override fun getIfUnique() = flyway

            override fun getObject() = flyway
        }
    }

    @Test
    fun `the pool rewrites batched inserts and speaks to Postgres 16`() {
        assertEquals("true", dataSource.dataSourceProperties["reWriteBatchedInserts"])
        assertEquals("true", dataSource.dataSourceProperties["tcpKeepAlive"])
        assertEquals(60_000, dataSource.keepaliveTime)
        val version = jdbcClient().sql("SHOW server_version_num").query(Int::class.java).single()
        assertTrue(version >= 160000, "server_version_num=$version")
    }

    @Test
    fun `a read outliving the socket timeout fails instead of blocking forever`() {
        val impatient =
            config.postgresDataSource(
                PostgresProperties(
                    url = postgres.jdbcUrl,
                    username = postgres.username,
                    password = postgres.password,
                    pool = PostgresProperties.Pool(socketTimeoutSeconds = 1),
                )
            ) as HikariDataSource
        impatient.use { source ->
            source.connection.use { connection ->
                assertThrows<SQLException> {
                    connection.createStatement().execute("SELECT pg_sleep(10)")
                }
            }
        }
    }

    @Test
    fun `flyway owns the public schema`() {
        val client = jdbcClient()
        val schema =
            client
                .sql(
                    "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'public'"
                )
                .query(String::class.java)
                .single()
        assertEquals("public", schema)
        val history =
            client
                .sql(
                    "SELECT count(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'"
                )
                .query(Int::class.java)
                .single()
        assertEquals(1, history)
        val blocks =
            client
                .sql(
                    "SELECT table_schema FROM information_schema.tables WHERE table_name = 'block'"
                )
                .query(String::class.java)
                .single()
        assertEquals("blocks", blocks)
    }

    @Test
    fun `the transaction manager binds the postgres data source`() {
        assertEquals(dataSource, config.postgresTransactionManager(dataSource).dataSource)
    }

    /**
     * V3 first shipped as a table rewrite; where it already ran, repair keeps the edited file
     * bootable.
     */
    @Test
    fun `an edited migration still boots where its first version already ran`() {
        dataSource.connection.use { it.createStatement().execute("CREATE DATABASE drift") }
        val url = postgres.jdbcUrl.replace("/${postgres.databaseName}", "/drift")
        val drift =
            config.postgresDataSource(
                PostgresProperties(
                    url = url,
                    username = postgres.username,
                    password = postgres.password,
                )
            ) as HikariDataSource
        val firstVersions = createTempDirectory("flyway")
        for (name in listOf("V1__blocks_and_transactions.sql", "V2__indexer_schemas.sql")) {
            val sql = javaClass.getResource("/db/migration/$name")!!.readText()
            firstVersions.resolve(name).writeText(sql)
        }
        firstVersions
            .resolve("V3__event_params_json.sql")
            .writeText(
                "ALTER TABLE blocks.event ALTER COLUMN params TYPE json USING params::text::json;"
            )

        drift.use { source ->
            Flyway.configure()
                .dataSource(source)
                .locations("filesystem:$firstVersions")
                .load()
                .migrate()
            assertThrows<FlywayException> {
                Flyway.configure()
                    .dataSource(source)
                    .locations(PostgresConfig.MIGRATIONS)
                    .load()
                    .migrate()
            }
            config.postgresFlyway(source).migrate()
            val type =
                JdbcTemplate(source)
                    .queryForObject(
                        "SELECT data_type FROM information_schema.columns WHERE table_schema = 'blocks' " +
                            "AND table_name = 'event' AND column_name = 'params'",
                        String::class.java,
                    )
            assertEquals("json", type)
        }
    }
}
