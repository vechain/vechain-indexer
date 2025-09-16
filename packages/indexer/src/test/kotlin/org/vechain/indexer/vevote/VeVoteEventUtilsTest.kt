package org.vechain.indexer.vevote

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.vevote.VeVoteEventUtils.getProposalId
import org.vechain.indexer.vevote.VeVoteEventUtils.getSupport
import org.vechain.indexer.vevote.VeVoteEventUtils.getWeight

class VeVoteEventUtilsTest {
    @Nested
    inner class GetProposalIdTest {
        @Test
        fun `getProposalId gets the proposalId if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("proposalId" to "12345")),
                )
            assertEquals("12345", getProposalId(event))
        }

        @Test
        fun `getProposalId throws an error if proposalId is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notproposalid" to "243423")),
                )
            assertThrows(IllegalStateException::class.java) { getProposalId(event) }
        }

        @Test
        fun `getProposalId proposalId is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("ProposalId" to "67890")),
                )
            assertThrows(IllegalStateException::class.java) { getProposalId(event) }
        }
    }

    @Nested
    inner class GetSupportTest {

        @Test
        fun `getSupport a value of 0 maps to AGAINST`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to 0)),
                )
            assertEquals(Support.AGAINST, getSupport(event))
        }

        @Test
        fun `getSupport a value of 1 maps to FOR`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to 1)),
                )
            assertEquals(Support.FOR, getSupport(event))
        }

        @Test
        fun `getSupport a value of 2 maps to ABSTAIN`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to 2)),
                )
            assertEquals(Support.ABSTAIN, getSupport(event))
        }

        @Test
        fun `getSupport throws an error if support is not an integer`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to "notanint")),
                )
            assertThrows(IllegalStateException::class.java) { getSupport(event) }
        }

        @Test
        fun `getSupport throws an error if support is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notsupport" to 0)),
                )
            assertThrows(IllegalStateException::class.java) { getSupport(event) }
        }

        @Test
        fun `getSupport returns Abstain for any other numeric value`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("support" to 3)),
                )
            val result = getSupport(event)

            assertEquals(result, Support.ABSTAIN)
        }

        @Test
        fun `getSupport throws if not available`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params = AbiEventParameters(mapOf("notsupport" to 2)),
                )
            assertThrows(IllegalStateException::class.java) { getSupport(event) }
        }

        @Test
        fun `getSupport support is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params = AbiEventParameters(returnValues = mapOf("Support" to 2)),
                )
            assertThrows(IllegalStateException::class.java) { getSupport(event) }
        }
    }

    @Nested
    inner class GetWeightTest {
        @Test
        fun `getWeight gets the weight if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("weight" to "1000")),
                )
            assertEquals(BigDecimal("1000"), getWeight(event))
        }

        @Test
        fun `getWeight throws an error if weight is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notweight" to "2000")),
                )
            assertThrows(IllegalStateException::class.java) { getWeight(event) }
        }

        @Test
        fun `getWeight weight is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("Weight" to "3000")),
                )
            assertThrows(IllegalStateException::class.java) { getWeight(event) }
        }

        @Test
        fun `getWeight throws an error if weight is not a valid BigInteger`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params = AbiEventParameters(returnValues = mapOf("weight" to "notabigdecimal")),
                )
            assertThrows(IllegalStateException::class.java) { getWeight(event) }
        }
    }
}
