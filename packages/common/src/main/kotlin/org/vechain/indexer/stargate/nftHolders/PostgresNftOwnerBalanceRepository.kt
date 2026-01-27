package org.vechain.indexer.stargate.nftHolders

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.postgres.PostgresVersionedRepository
import org.vechain.indexer.stargate.token.TokenLevel

@Profile("stargate", "nft-holders-by-block")
@Repository
open class PostgresNftOwnerBalanceRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<NftOwnerBalance>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    NftOwnerBalanceRepository {

    override fun tableName(): String = "stargate_nft_owner_balances"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        owner, total, by_level
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: NftOwnerBalance): Array<Any?> {
        // Convert Map<TokenLevel, Long> to Map<String, Long> for JSON serialization
        val byLevelJson = doc.byLevel.mapKeys { it.key.name }
        return arrayOf(
            doc.owner,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.owner,
            doc.total,
            objectMapper.writeValueAsString(byLevelJson),
        )
    }

    override fun mapRow(rs: ResultSet): NftOwnerBalance {
        val byLevelJson = rs.getString("by_level")
        val byLevelMap: Map<String, Long> = objectMapper.readValue(byLevelJson)
        val byLevel = byLevelMap.mapKeys { TokenLevel.valueOf(it.key) }

        return NftOwnerBalance(
            owner = rs.getString("owner"),
            total = rs.getLong("total"),
            byLevel = byLevel,
            blockNumber = rs.getLong("block_number"),
            blockId = rs.getString("block_id"),
            blockTimestamp = rs.getLong("block_timestamp"),
            version = rs.getInt("version"),
        )
    }

    override fun saveAllVersioned(updated: List<NftOwnerBalance>, existing: List<NftOwnerBalance>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findByOwnerIn(owners: Collection<String>): List<NftOwnerBalance> {
        if (owners.isEmpty()) return emptyList()
        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE owner IN (:owners) AND is_current = true
            """
                .trimIndent(),
            mapOf("owners" to owners),
        ) { rs, _ ->
            mapRow(rs)
        }
    }
}
