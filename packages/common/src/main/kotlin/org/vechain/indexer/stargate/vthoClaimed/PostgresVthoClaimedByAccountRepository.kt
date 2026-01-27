package org.vechain.indexer.stargate.vthoClaimed

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("stargate", "vtho-claimed-by-account")
@Repository
open class PostgresVthoClaimedByAccountRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<VthoClaimedByAccount>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    VthoClaimedByAccountRepository {

    override fun tableName(): String = "stargate_vtho_claimed_by_account"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        account, token_id, total, legacy_rewards, delegation_rewards
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: VthoClaimedByAccount): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.account,
            doc.tokenId,
            doc.total,
            doc.legacyRewards,
            doc.delegationRewards,
        )

    override fun mapRow(rs: ResultSet): VthoClaimedByAccount {
        return VthoClaimedByAccount(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            account = rs.getString("account"),
            tokenId = rs.getString("token_id"),
            total = rs.getBigDecimal("total").toBigInteger(),
            legacyRewards = rs.getBigDecimal("legacy_rewards").toBigInteger(),
            delegationRewards = rs.getBigDecimal("delegation_rewards").toBigInteger(),
        )
    }

    override fun saveAllVersioned(
        updated: List<VthoClaimedByAccount>,
        existing: List<VthoClaimedByAccount>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(id: String): VthoClaimedByAccount? {
        return findCurrentByEntityId(id)
    }

    override fun findAllById(ids: Collection<String>): List<VthoClaimedByAccount> {
        if (ids.isEmpty()) return emptyList()
        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE ${entityIdColumn()} IN (:ids) AND is_current = true
            """
                .trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            mapRow(rs)
        }
    }
}
