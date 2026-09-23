package org.vechain.indexer

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.fixtures.BusinessEventParamFixtures.BUSINESS_EVENT_PARAMS
import org.vechain.indexer.history.HistoryConfig
import org.vechain.indexer.stargate.rewards.TokenRewardConfig
import org.vechain.indexer.validator.DelegationConfig

/** The three as-of readers never pull their parent back to them at startup. */
class DependencyEdgesTest {

    private val validator = parent("validator")
    private val delegation = parent("delegation")

    private fun parent(name: String): Indexer =
        mockk(relaxed = true) {
            every { this@mockk.name } returns name
            every { startBlock } returns 0L
        }

    @Test
    fun `delegation, history and token_reward depend on their parent without aligning`() {
        val edges =
            listOf(
                DelegationConfig()
                    .delegationIndexer(
                        mockk(relaxed = true),
                        mockk(relaxed = true),
                        validator,
                        0L,
                        1L,
                        "0x00000000000000000000000000005374616b6572",
                        BUSINESS_EVENT_PARAMS.getValue("STARGATE_CONTRACT"),
                        BUSINESS_EVENT_PARAMS.getValue("STARGATE_NFT_CONTRACT"),
                    ),
                HistoryConfig()
                    .historyIndexer(
                        mockk(relaxed = true),
                        mockk(relaxed = true),
                        validator,
                        0L,
                        1L,
                        mockk<BusinessEventProperties> {
                            every { substitutions } returns BUSINESS_EVENT_PARAMS
                        },
                    ),
                TokenRewardConfig()
                    .tokenRewardIndexer(
                        mockk(relaxed = true),
                        mockk(relaxed = true),
                        delegation,
                        0L,
                        1L,
                    ),
            )

        assertEquals(
            listOf("validator" to false, "validator" to false, "delegation" to false),
            edges.map { (it as BlockIndexer).dependsOn!!.name to it.alignWithParent },
        )
    }
}
