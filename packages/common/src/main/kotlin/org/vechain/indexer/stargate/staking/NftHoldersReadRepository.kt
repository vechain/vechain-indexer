package org.vechain.indexer.stargate.staking

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.timeseries.TimeFrameReadRepository

/** The NFT-holders series behind `/stargate/nft-holders` and `/nft-holders/{period}`. */
@Repository
@ConditionalOnPostgres
open class NftHoldersReadRepository(@Qualifier("postgresJdbcTemplate") jdbc: JdbcTemplate) :
    TimeFrameReadRepository<NftHoldersByBlock>(jdbc, NftHoldersRowMapping)
