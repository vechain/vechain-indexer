package org.vechain.indexer.b3tr.gm

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.gm.GmNftRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres

/** The level counts behind `/b3tr/gm/level-overview`; a burned token is not held by anyone. */
@Repository
@ConditionalOnPostgres
open class GmNftReadRepository(@Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate) {

    open fun levelCounts(): List<GMLevelOverview> =
        jdbc.query(
            "SELECT level, count(*) AS total FROM $TABLE WHERE $HELD GROUP BY level " +
                "ORDER BY total DESC"
        ) { rs, _ ->
            GMLevelOverview(GmLevelName.valueOf(rs.getString("level")), rs.getLong("total"))
        }

    open fun countByLevel(level: GmLevelName): Long =
        jdbc.queryForObject(
            "SELECT count(*) FROM $TABLE WHERE $HELD AND level = CAST(? AS b3tr_gm.level)",
            Long::class.java,
            level.name,
        )!!

    companion object {
        // Spelled as the partial index's predicate, which a bind parameter would not match.
        private const val HELD =
            "superseded_at IS NULL AND owner <> '\\x0000000000000000000000000000000000000000'::BYTEA"
    }
}
