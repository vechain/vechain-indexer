package org.vechain.indexer.b3tr.gm.repository

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.gm.GMLevelOverview
import org.vechain.indexer.b3tr.gm.GmLevelName
import org.vechain.indexer.b3tr.gm.GmNft
import org.vechain.indexer.postgres.PostgresVersionedRepository
import org.vechain.indexer.thor.Address

@Profile("b3tr", "b3tr-gm-nft")
@Repository
open class PostgresGmNftRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<GmNft>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    GmNftRepository {

    override fun tableName(): String = "b3tr_gm_nfts"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        level, attached_node_id, b3tr_donated, owner
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: GmNft): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.level.name,
            doc.attachedNodeId,
            doc.b3trDonated,
            doc.owner,
        )

    override fun mapRow(rs: ResultSet): GmNft {
        return GmNft(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            level = GmLevelName.valueOf(rs.getString("level")),
            attachedNodeId = rs.getString("attached_node_id"),
            b3trDonated = rs.getBigDecimal("b3tr_donated").toBigInteger(),
            owner = rs.getString("owner"),
        )
    }

    override fun saveAllVersioned(updated: List<GmNft>, existing: List<GmNft>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(id: String): GmNft? {
        return findCurrentByEntityId(id)
    }

    @Cacheable(value = ["gmNft_countByLevelAndOwnerNot"], key = "#level + '-' + #owner")
    override fun countByLevelAndOwnerNot(level: GmLevelName, owner: String): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE level = ? AND owner != ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            level.name,
            owner,
        ) ?: 0L
    }

    override fun levelCounts(): List<GMLevelOverview> {
        return jdbcTemplate.query(
            """
            SELECT level, COUNT(*) as total_nfts
            FROM ${tableName()}
            WHERE owner != ? AND is_current = true
            GROUP BY level
            ORDER BY total_nfts DESC
            """
                .trimIndent(),
            { rs, _ ->
                GMLevelOverview(
                    level = GmLevelName.valueOf(rs.getString("level")),
                    totalNFTs = rs.getLong("total_nfts"),
                )
            },
            Address.ZERO_ADDRESS,
        )
    }
}
