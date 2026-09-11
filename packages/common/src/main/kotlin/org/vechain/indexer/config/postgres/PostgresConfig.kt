package org.vechain.indexer.config.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.JdbcTransactionManager

/**
 * The Postgres store, beside Mongo in the same process. Every bean is named `postgres*` so a
 * `@Transactional` on a Postgres write has to name `PostgresConfig.TRANSACTION_MANAGER`.
 */
@Configuration
@ConditionalOnPostgres
@EnableConfigurationProperties(PostgresProperties::class)
open class PostgresConfig {

    companion object {
        const val MIGRATIONS = "classpath:db/migration"
        const val TRANSACTION_MANAGER = "postgresTransactionManager"
    }

    @Bean
    open fun postgresDataSource(properties: PostgresProperties): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                poolName = "postgres"
                jdbcUrl = properties.url
                username = properties.username
                password = properties.password
                maximumPoolSize = properties.pool.maximumSize
                // Turns a JDBC batch of single-row INSERTs into multi-row statements.
                addDataSourceProperty("reWriteBatchedInserts", "true")
            }
        )

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(prefix = "postgres.flyway", name = ["enabled"], havingValue = "true")
    open fun postgresFlyway(postgresDataSource: DataSource): Flyway =
        Flyway.configure().dataSource(postgresDataSource).locations(MIGRATIONS).load()

    @Bean(TRANSACTION_MANAGER)
    open fun postgresTransactionManager(postgresDataSource: DataSource): JdbcTransactionManager =
        JdbcTransactionManager(postgresDataSource)

    // Resolving the Flyway provider orders the template, and everything built on it, after
    // migrate().
    @Bean
    open fun postgresJdbcTemplate(
        postgresDataSource: DataSource,
        postgresFlyway: ObjectProvider<Flyway>,
    ): JdbcTemplate {
        postgresFlyway.ifAvailable
        return JdbcTemplate(postgresDataSource)
    }

    @Bean
    open fun postgresJdbcClient(postgresJdbcTemplate: JdbcTemplate): JdbcClient =
        JdbcClient.create(postgresJdbcTemplate)
}
