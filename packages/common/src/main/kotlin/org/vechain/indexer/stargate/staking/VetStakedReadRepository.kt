package org.vechain.indexer.stargate.staking

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.timeseries.TimeFrameReadRepository

/** The VET-staked series behind `/stargate/total-vet-staked` and `/vet-staked/{period}`. */
@Repository
@ConditionalOnPostgres
open class VetStakedReadRepository(@Qualifier("postgresJdbcTemplate") jdbc: JdbcTemplate) :
    TimeFrameReadRepository<VetStakedByBlock>(jdbc, VetStakedRowMapping)
