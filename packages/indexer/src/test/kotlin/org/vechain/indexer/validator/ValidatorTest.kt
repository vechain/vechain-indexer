package org.vechain.indexer.validator

import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidatorTest {
    @Test
    fun `isEquivalentTo ignores block metadata and version`() {
        val persisted =
            Validator(
                id = "0xVAL1",
                blockId = "0xOLD_BLOCK",
                blockNumber = 19,
                blockTimestamp = 123,
                beneficiary = "0xBEN",
                status = Status.ACTIVE,
                exitingValidatorVetStaked = BigDecimal("5"),
                version = 5,
            )

        val candidate =
            persisted.copy(
                blockId = "0xNEW_BLOCK",
                blockNumber = 20,
                blockTimestamp = 456,
                version = 6,
            )

        assertThat(candidate.isEquivalentTo(persisted)).isTrue()
        assertThat(persisted.isEquivalentTo(candidate)).isTrue()
    }
}
