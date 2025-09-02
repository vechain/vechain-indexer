package org.vechain.indexer.b3tr.voting

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

class VoteEventUtilsTest {
    @Nested
    inner class GetProposalIdTest {
        @Test
        fun `getProposalId gets the proposalId if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("proposalId" to "12345")),
                )
            assertEquals("12345", VoteEventUtils.getProposalId(event))
        }

        @Test
        fun `getProposalId throws an error if proposalId is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notproposalid" to "243423")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getProposalId(event) }
        }

        @Test
        fun `getProposalId proposalId is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("ProposalId" to "67890")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getProposalId(event) }
        }
    }

    @Nested
    inner class GetVoterTest {
        @Test
        fun `getVoter gets the voter if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("from" to "0x123")),
                )
            assertEquals("0x123", VoteEventUtils.getVoter(event))
        }

        @Test
        fun `getVoter throws an error if voter is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notfrom" to "0x456")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getVoter(event) }
        }

        @Test
        fun `getVoter from is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("From" to "0x789")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getVoter(event) }
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
            assertEquals(Support.AGAINST, VoteEventUtils.getSupport(event))
        }

        @Test
        fun `getSupport a value of 1 maps to FOR`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to 1)),
                )
            assertEquals(Support.FOR, VoteEventUtils.getSupport(event))
        }

        @Test
        fun `getSupport a value of 2 maps to ABSTAIN`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to 2)),
                )
            assertEquals(Support.ABSTAIN, VoteEventUtils.getSupport(event))
        }

        @Test
        fun `getSupport throws an error if support is not an integer`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to "notanint")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getSupport(event) }
        }

        @Test
        fun `getSupport throws if value less than 0`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to -1)),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getSupport(event) }
        }

        @Test
        fun `getSupport throws if value greater than 2`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to 3)),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getSupport(event) }
        }

        @Test
        fun `getSupport throws an error if support is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notsupport" to 0)),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getSupport(event) }
        }

        @Test
        fun `getSupport throws an error for invalid support value`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("support" to 3)),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getSupport(event) }
        }

        @Test
        fun `getSupport throws if not available`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params = AbiEventParameters(mapOf("notsupport" to 2)),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getSupport(event) }
        }

        @Test
        fun `getSupport support is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params = AbiEventParameters(returnValues = mapOf("Support" to 2)),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getSupport(event) }
        }
    }

    @Nested
    inner class GetWeightTest {
        @Test
        fun `getWeight gets the weight if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("voteWeight" to "1000")),
                )
            assertEquals(BigInteger("1000"), VoteEventUtils.getWeight(event))
        }

        @Test
        fun `getWeight throws an error if weight is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notweight" to "2000")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getWeight(event) }
        }

        @Test
        fun `getWeight weight is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("VoteWeight" to "3000")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getWeight(event) }
        }

        @Test
        fun `getWeight throws an error if weight is not a valid BigInteger`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params =
                        AbiEventParameters(returnValues = mapOf("voteWeight" to "notabiginteger")),
                )
            assertThrows(NumberFormatException::class.java) { VoteEventUtils.getWeight(event) }
        }
    }

    @Nested
    inner class GetPowerTest {
        @Test
        fun `getPower gets the power if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("votePower" to "5000")),
                )
            assertEquals(BigInteger("5000"), VoteEventUtils.getPower(event))
        }

        @Test
        fun `getPower throws an error if power is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(mapOf("notpower" to "6000")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getPower(event) }
        }

        @Test
        fun `getPower power is case-sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("VotePower" to "7000")),
                )
            assertThrows(IllegalStateException::class.java) { VoteEventUtils.getPower(event) }
        }

        @Test
        fun `getPower throws an error if power is not a valid BigInteger`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params =
                        AbiEventParameters(returnValues = mapOf("votePower" to "notabiginteger")),
                )
            assertThrows(NumberFormatException::class.java) { VoteEventUtils.getPower(event) }
        }
    }

    @Nested
    inner class GetReasonTest {
        @Test
        fun `getReason gets the reason if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("reason" to "test reason")),
                )
            assertEquals("test reason", VoteEventUtils.getReason(event))
        }

        @Test
        fun `getReason can have a blank reason`() {
            val event =
                buildIndexedEvent(
                    id = "event4",
                    params = AbiEventParameters(returnValues = mapOf("reason" to "")),
                )
            assertEquals("", VoteEventUtils.getReason(event))
        }
    }

    @Nested
    inner class GroupByProposalIdTest {
        @Test
        fun `groupByProposalId groups events by proposalId`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("proposalId" to "123")),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(returnValues = mapOf("proposalId" to "123")),
                )
            val event3 =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("proposalId" to "456")),
                )

            val groupedEvents = VoteEventUtils.groupByProposalId(listOf(event1, event2, event3))

            assertEquals(2, groupedEvents.size)
            assertTrue(groupedEvents.containsKey("123"))
            assertTrue(groupedEvents.containsKey("456"))
            assertEquals(2, groupedEvents["123"]!!.size)
            assertEquals(1, groupedEvents["456"]!!.size)
        }

        @Test
        fun `groupByProposalId throws an error if proposalId is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(mapOf("notproposalid" to "value")),
                )
            assertThrows(IllegalStateException::class.java) {
                VoteEventUtils.groupByProposalId(listOf(event))
            }
        }
    }

    @Nested
    inner class GroupBySupportTest {
        @Test
        fun `groupBySupport groups events by support`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("support" to 0)),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(returnValues = mapOf("support" to 1)),
                )
            val event3 =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("support" to 2)),
                )

            val groupedEvents = VoteEventUtils.groupBySupport(listOf(event1, event2, event3))

            assertEquals(3, groupedEvents.size)
            assertTrue(groupedEvents.containsKey(Support.AGAINST))
            assertTrue(groupedEvents.containsKey(Support.FOR))
            assertTrue(groupedEvents.containsKey(Support.ABSTAIN))
            assertEquals(1, groupedEvents[Support.AGAINST]!!.size)
            assertEquals(1, groupedEvents[Support.FOR]!!.size)
            assertEquals(1, groupedEvents[Support.ABSTAIN]!!.size)
        }

        @Test
        fun `groupBySupport throws an error if support is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(mapOf("notsupport" to "value")),
                )
            assertThrows(IllegalStateException::class.java) {
                VoteEventUtils.groupBySupport(listOf(event))
            }
        }
    }
}
