package org.vechain.indexer.stargate.tokenReward

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex

/** The API's read of a token's current reward records. */
@Repository
@ConditionalOnPostgres
open class TokenRewardReadRepository(
    @Qualifier("postgresJdbcTemplate") jdbcTemplate: JdbcTemplate
) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /** `/stargate/token-rewards/{tokenId}`: the token's records in [periods], paged by time. */
    open fun findByTokenIdAndRewardPeriodIn(
        tokenId: String,
        periods: Collection<RewardPeriod>,
        validator: String?,
        direction: Direction,
        offset: Long,
        limit: Int,
    ): List<TokenReward> =
        jdbc.query(
            "SELECT * FROM token_reward.state WHERE superseded_at IS NULL AND token_id = :token" +
                " AND reward_period = ANY(CAST(:periods AS token_reward.period[]))" +
                (if (validator == null) "" else " AND validator = :validator") +
                " ORDER BY block_timestamp ${direction.name}, id ${direction.name} OFFSET :offset LIMIT :limit",
            MapSqlParameterSource("token", BigDecimal(tokenId))
                .addValue("periods", periods.map { it.name }.toTypedArray())
                .addValue("validator", PostgresHex.bytesOrNull(validator))
                .addValue("offset", offset)
                .addValue("limit", limit),
        ) { rs, _ ->
            TokenRewardRowMapping.read(rs)
        }
}
