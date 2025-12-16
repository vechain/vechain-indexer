package org.vechain.indexer.stargate

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class StargateUtilsTest {
    @Test
    fun `buildIssuanceClause returns correct clause`() {
        val stargateContract = "0x00000000000000000000000000005374616b6572"

        val clauses = StargateUtils.buildIssuanceClause(stargateContract)

        expectThat(clauses).hasSize(1)
        expectThat(clauses[0].to).isEqualTo(stargateContract)
        expectThat(clauses[0].value).isEqualTo("0x0")
        expectThat(clauses[0].data).isEqualTo("0x863623bb")
    }
}
