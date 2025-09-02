package org.vechain.indexer.b3tr.xAlloc

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

class XAllocEventsUtilsTest {

    @Nested
    inner class GroupByRoundIdTest {
        @Test
        fun `groups events by roundId`() {
            val e1 =
                buildIndexedEvent(
                    id = "e1",
                    params = AbiEventParameters(returnValues = mapOf("roundId" to 1)),
                )
            val e2 =
                buildIndexedEvent(
                    id = "e2",
                    params = AbiEventParameters(returnValues = mapOf("roundId" to 1)),
                )
            val e3 =
                buildIndexedEvent(
                    id = "e3",
                    params = AbiEventParameters(returnValues = mapOf("roundId" to 2)),
                )

            val grouped = XAllocEventUtils.groupByRoundId(listOf(e1, e2, e3))

            assertEquals(2, grouped.size)
            assertTrue(grouped.containsKey(1))
            assertTrue(grouped.containsKey(2))
            assertEquals(2, grouped[1]!!.size)
            assertEquals(1, grouped[2]!!.size)
        }

        @Test
        fun `throws if roundId missing`() {
            val e =
                buildIndexedEvent(
                    id = "e-missing",
                    params = AbiEventParameters(returnValues = mapOf("notRoundId" to 9)),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.groupByRoundId(listOf(e))
            }
        }
    }

    @Nested
    inner class GetAppIdsTest {

        @Test
        fun `returns list of strings from appsIds`() {
            val e =
                buildIndexedEvent(
                    id = "appsids-list",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("appsIds" to listOf("app1", "app2"))
                        ),
                )
            val result = XAllocEventUtils.getAppIds(e)
            assertEquals(listOf("app1", "app2"), result)
        }

        @Test
        fun `trims entries and drops blanks`() {
            val e =
                buildIndexedEvent(
                    id = "appsids-trim",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("appsIds" to listOf(" app1 ", " ", "app2"))
                        ),
                )
            val result = XAllocEventUtils.getAppIds(e)
            assertEquals(listOf("app1", "app2"), result)
        }

        @Test
        fun `accepts non-string elements by toString`() {
            val e =
                buildIndexedEvent(
                    id = "appsids-mixed",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("appsIds" to listOf("app1", 123, true))
                        ),
                )
            val result = XAllocEventUtils.getAppIds(e)
            assertEquals(listOf("app1", "123", "true"), result)
        }

        @Test
        fun `empty list yields empty result`() {
            val e =
                buildIndexedEvent(
                    id = "appsids-empty",
                    params =
                        AbiEventParameters(returnValues = mapOf("appsIds" to emptyList<String>())),
                )
            val result = XAllocEventUtils.getAppIds(e)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `throws when appsIds is missing or not a list`() {
            val missing =
                buildIndexedEvent(
                    id = "appsids-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { XAllocEventUtils.getAppIds(missing) }

            val wrongType =
                buildIndexedEvent(
                    id = "appsids-wrongtype",
                    params = AbiEventParameters(returnValues = mapOf("appsIds" to "not-a-list")),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getAppIds(wrongType)
            }
        }
    }

    @Nested
    inner class GetWeightsTest {

        @Test
        fun `supports BigInteger Number and String elements`() {
            val e =
                buildIndexedEvent(
                    id = "weights-mixed",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("voteWeights" to listOf(BigInteger("10"), 20, "30"))
                        ),
                )
            val result = XAllocEventUtils.getWeights(e)
            assertEquals(listOf(BigInteger("10"), BigInteger("20"), BigInteger("30")), result)
        }

        @Test
        fun `ignores unparsable strings`() {
            val e =
                buildIndexedEvent(
                    id = "weights-badstr",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("voteWeights" to listOf("10", "oops", "30"))
                        ),
                )
            val result = XAllocEventUtils.getWeights(e)
            assertEquals(listOf(BigInteger("10"), BigInteger("30")), result)
        }

        @Test
        fun `empty list yields empty result`() {
            val e =
                buildIndexedEvent(
                    id = "weights-empty",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("voteWeights" to emptyList<BigInteger>())
                        ),
                )
            val result = XAllocEventUtils.getWeights(e)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `throws when voteWeights is missing or not a list`() {
            val missing =
                buildIndexedEvent(
                    id = "weights-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { XAllocEventUtils.getWeights(missing) }

            val wrongType =
                buildIndexedEvent(
                    id = "weights-wrongtype",
                    params = AbiEventParameters(returnValues = mapOf("voteWeights" to "not-a-list")),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getWeights(wrongType)
            }
        }
    }

    @Nested
    inner class ParseVotesSingleEventTest {
        @Test
        fun `maps appIds to weights for one event`() {
            val e =
                buildIndexedEvent(
                    id = "single-ok",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "appsIds" to listOf("app1", "app2"),
                                    "voteWeights" to listOf(100, 200),
                                )
                        ),
                )

            val result = XAllocEventUtils.parseVotes(e)

            assertEquals(mapOf("app1" to BigInteger("100"), "app2" to BigInteger("200")), result)
        }

        @Test
        fun `throws when appIds and weights lengths differ`() {
            val e =
                buildIndexedEvent(
                    id = "single-mismatch",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "appsIds" to listOf("app1", "app2"),
                                    "voteWeights" to listOf("100"),
                                )
                        ),
                )
            assertThrows(IllegalStateException::class.java) { XAllocEventUtils.parseVotes(e) }
        }

        @Test
        fun `throws when duplicate appIds`() {
            val e =
                buildIndexedEvent(
                    id = "single-dup",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "appsIds" to listOf("app1", "app1"),
                                    "voteWeights" to listOf(100, 200),
                                )
                        ),
                )
            assertThrows(IllegalStateException::class.java) { XAllocEventUtils.parseVotes(e) }
        }
    }

    @Nested
    inner class ParseVotesAggregatedTest {
        @Test
        fun `aggregates weights and voter counts across multiple events`() {
            val e1 =
                buildIndexedEvent(
                    id = "agg-1",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "appsIds" to listOf("A", "B"),
                                    "voteWeights" to listOf(10, 20),
                                )
                        ),
                )
            val e2 =
                buildIndexedEvent(
                    id = "agg-2",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "appsIds" to listOf("B", "C"),
                                    "voteWeights" to listOf(30, 40),
                                )
                        ),
                )

            val aggregated = XAllocEventUtils.parseVotes(listOf(e1, e2))

            assertEquals(3, aggregated.size)

            val a = aggregated["A"]!!
            val b = aggregated["B"]!!
            val c = aggregated["C"]!!

            assertEquals(BigInteger("10"), a.weight)
            assertEquals(1L, a.voters)

            assertEquals(BigInteger("50"), b.weight) // 20 + 30
            assertEquals(2L, b.voters)

            assertEquals(BigInteger("40"), c.weight)
            assertEquals(1L, c.voters)
        }

        @Test
        fun `returns empty map for empty input`() {
            val aggregated = XAllocEventUtils.parseVotes(emptyList())
            assertTrue(aggregated.isEmpty())
        }
    }
}
