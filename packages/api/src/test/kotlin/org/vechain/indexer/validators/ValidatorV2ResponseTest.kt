package org.vechain.indexer.validators

import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class ValidatorV2ResponseTest {

    private val aggregates =
        ValidatorAggregates(
            totalWeight = BigDecimal.ZERO,
            totalNextPeriodWeight = BigDecimal.ZERO,
            totalActiveVetStaked = BigDecimal.ZERO,
            totalActiveNextCycleVetStaked = BigDecimal.ZERO,
            delegationFacetsByValidator = emptyMap(),
        )

    private fun response(status: Status, offlineBlock: Long?) =
        ValidatorV2Response.from(
            Validator(
                id = "0x" + "a".repeat(40),
                blockId = "0x" + "0".repeat(64),
                blockNumber = 1,
                blockTimestamp = 10,
                status = status,
                offlineBlock = offlineBlock,
            ),
            aggregates,
            BigDecimal.ONE,
            BigDecimal.ONE,
        )

    @Test
    fun `a scheduled validator is online until thor marks it offline`() {
        expectThat(response(Status.ACTIVE, null).online).isEqualTo(true)
        expectThat(response(Status.EXITING, null).online).isEqualTo(true)
        val offline = response(Status.ACTIVE, 25_902_556)
        expectThat(offline.online).isEqualTo(false)
        expectThat(offline.offlineBlock).isEqualTo(25_902_556)
    }

    @Test
    fun `a validator outside the leader group is neither online nor offline`() {
        expectThat(response(Status.QUEUED, null).online).isNull()
        expectThat(response(Status.EXITED, 25_902_556).online).isNull()
    }
}
