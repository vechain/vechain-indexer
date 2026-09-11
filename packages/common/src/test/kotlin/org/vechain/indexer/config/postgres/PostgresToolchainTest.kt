package org.vechain.indexer.config.postgres

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.ObjectProvider
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
        val version = jdbcClient().sql("SHOW server_version_num").query(Int::class.java).single()
        assertTrue(version >= 160000, "server_version_num=$version")
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
    }

    @Test
    fun `the transaction manager binds the postgres data source`() {
        assertEquals(dataSource, config.postgresTransactionManager(dataSource).dataSource)
    }
}
