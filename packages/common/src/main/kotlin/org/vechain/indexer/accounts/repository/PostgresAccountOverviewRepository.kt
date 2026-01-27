package org.vechain.indexer.accounts.repository

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.accounts.AccountOverview
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("accounts", "account-overview")
@Repository
open class PostgresAccountOverviewRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<AccountOverview>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    AccountOverviewRepository {

    override fun tableName(): String = "account_overviews"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        first_seen, last_seen, transactions_sent, clauses_sent,
        vtho_burned, vtho_delegated, gas_used, vet_sent, vet_received
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: AccountOverview): Array<Any?> =
        arrayOf(
            doc.address,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.firstSeen,
            doc.lastSeen,
            doc.transactionsSent,
            doc.clausesSent,
            doc.vthoBurned,
            doc.vthoDelegated,
            doc.gasUsed,
            doc.vetSent,
            doc.vetReceived,
        )

    override fun mapRow(rs: ResultSet): AccountOverview =
        AccountOverview(
            address = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            firstSeen = rs.getLong("first_seen"),
            lastSeen = rs.getLong("last_seen"),
            transactionsSent = rs.getLong("transactions_sent"),
            clausesSent = rs.getLong("clauses_sent"),
            vthoBurned = rs.getBigDecimal("vtho_burned").toBigInteger(),
            vthoDelegated = rs.getBigDecimal("vtho_delegated").toBigInteger(),
            gasUsed = rs.getBigDecimal("gas_used").toBigInteger(),
            vetSent = rs.getBigDecimal("vet_sent").toBigInteger(),
            vetReceived = rs.getBigDecimal("vet_received").toBigInteger(),
        )

    override fun saveAllVersioned(updated: List<AccountOverview>, existing: List<AccountOverview>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findByAddress(address: String): AccountOverview? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE entity_id = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                address,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }
}
