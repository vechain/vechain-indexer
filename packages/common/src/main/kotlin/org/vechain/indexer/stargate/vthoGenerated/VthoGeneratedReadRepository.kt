package org.vechain.indexer.stargate.vthoGenerated

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.timeseries.TimeFrameReadRepository

/**
 * The VTHO-generated series behind `/stargate/total-vtho-generated` and `/vtho-generated/{period}`.
 */
@Repository
@ConditionalOnPostgres
open class VthoGeneratedReadRepository(@Qualifier("postgresJdbcTemplate") jdbc: JdbcTemplate) :
    TimeFrameReadRepository<VthoGeneratedByBlock>(jdbc, VthoGeneratedRowMapping)
