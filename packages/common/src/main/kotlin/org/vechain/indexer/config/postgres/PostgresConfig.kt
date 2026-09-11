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
 * The `chain` Postgres store, beside Mongo in the same process. Every bean is named `chain*` so a
 * `@Transactional` on a Postgres write has to say `transactionManager = "chainTransactionManager"`.
 * `postgres.enabled=false` is for tests that boot a context without a database.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "postgres",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(PostgresProperties::class)
open class PostgresConfig {

    companion object {
        const val SCHEMA = "chain"
        const val MIGRATIONS = "classpath:db/migration/chain"
        const val TRANSACTION_MANAGER = "chainTransactionManager"
    }

    @Bean
    open fun chainDataSource(properties: PostgresProperties): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                poolName = SCHEMA
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
    open fun chainFlyway(chainDataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(chainDataSource)
            .schemas(SCHEMA)
            .defaultSchema(SCHEMA)
            .locations(MIGRATIONS)
            .load()

    @Bean(TRANSACTION_MANAGER)
    open fun chainTransactionManager(chainDataSource: DataSource): JdbcTransactionManager =
        JdbcTransactionManager(chainDataSource)

    // Resolving the Flyway provider orders the template, and everything built on it, after
    // migrate().
    @Bean
    open fun chainJdbcTemplate(
        chainDataSource: DataSource,
        chainFlyway: ObjectProvider<Flyway>,
    ): JdbcTemplate {
        chainFlyway.ifAvailable
        return JdbcTemplate(chainDataSource)
    }

    @Bean
    open fun chainJdbcClient(chainJdbcTemplate: JdbcTemplate): JdbcClient =
        JdbcClient.create(chainJdbcTemplate)
}
