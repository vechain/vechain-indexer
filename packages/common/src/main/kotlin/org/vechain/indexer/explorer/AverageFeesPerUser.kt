package org.vechain.indexer.explorer

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.vechain.indexer.IndexedDocument

/** One day's fee rollup as of [blockNumber]. */
data class AverageFeesPerUser(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val date: String,
    val dayStartTimestamp: Long,
    val totalFeesPaid: BigDecimal,
    val dailyActiveUsers: Long,
    val averageFeesPerUser: BigDecimal,
) : IndexedDocument

/** One origin that has paid a fee on a day; the day's active users are how many there are. */
data class DailyActiveOrigin(
    val dayStartTimestamp: Long,
    val origin: String,
    val blockNumber: Long,
)
