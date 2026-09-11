package org.vechain.indexer.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.config.postgres.PostgresProperties

/** Migrated Postgres 16 whose pool resolves no unqualified name, so statements name a schema. */
class PostgresTestDatabase(private val apiPassword: String = "") : AutoCloseable {

    val container = PostgreSQLContainer("postgres:16")
    lateinit var properties: PostgresProperties
    lateinit var dataSource: HikariDataSource
    lateinit var jdbc: JdbcTemplate

    val jdbcUrl: String
        get() = container.jdbcUrl

    fun start(): PostgresTestDatabase {
        container.start()
        val config = PostgresConfig()
        properties =
            PostgresProperties(
                url = container.jdbcUrl,
                username = container.username,
                password = container.password,
                apiPassword = apiPassword,
            )
        (config.postgresDataSource(properties) as HikariDataSource).use {
            config.postgresFlyway(it).migrate()
        }
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = container.jdbcUrl
                    username = container.username
                    password = container.password
                    connectionInitSql = "SET search_path TO ''"
                    // As PostgresConfig does, so a batch is rewritten here the way production does.
                    addDataSourceProperty("reWriteBatchedInserts", "true")
                }
            )
        jdbc = JdbcTemplate(dataSource)
        return this
    }

    fun count(table: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    fun transactions(): TransactionTemplate =
        TransactionTemplate(PostgresConfig().postgresTransactionManager(dataSource))

    override fun close() {
        dataSource.close()
        container.stop()
    }
}
