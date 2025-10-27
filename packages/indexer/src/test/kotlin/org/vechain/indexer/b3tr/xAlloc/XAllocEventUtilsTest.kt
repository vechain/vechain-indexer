package org.vechain.indexer.b3tr.xAlloc

import java.math.BigDecimal
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
        fun `unparsable weight value should throw an error`() {
            val e =
                buildIndexedEvent(
                    id = "weights-badstr",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("voteWeights" to listOf("10", "oops", "30"))
                        ),
                )

            assertThrows(IllegalStateException::class.java) { XAllocEventUtils.getWeights(e) }
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
        fun `duplicate appIds should be merged`() {
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
            val result = XAllocEventUtils.parseVotes(e)
            assertEquals(mapOf("app1" to BigInteger("300")), result)
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

    @Nested
    inner class GetTotalAmountTest {
        @Test
        fun `returns BigInteger directly`() {
            val amount = BigInteger("1000000000000000000")
            val e =
                buildIndexedEvent(
                    id = "total-big",
                    params = AbiEventParameters(returnValues = mapOf("totalAmount" to amount)),
                )
            assertEquals(amount, XAllocEventUtils.getTotalAmount(e))
        }

        @Test
        fun `converts Number to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "total-number",
                    params = AbiEventParameters(returnValues = mapOf("totalAmount" to 5000L)),
                )
            assertEquals(BigInteger("5000"), XAllocEventUtils.getTotalAmount(e))
        }

        @Test
        fun `parses String to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "total-string",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("totalAmount" to "2500000000000000000")
                        ),
                )
            assertEquals(BigInteger("2500000000000000000"), XAllocEventUtils.getTotalAmount(e))
        }

        @Test
        fun `handles leading and trailing whitespace in String`() {
            val e =
                buildIndexedEvent(
                    id = "total-whitespace",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("totalAmount" to "  1000000000000000000  ")
                        ),
                )
            assertEquals(BigInteger("1000000000000000000"), XAllocEventUtils.getTotalAmount(e))
        }

        @Test
        fun `throws on invalid String format`() {
            val e =
                buildIndexedEvent(
                    id = "total-invalid",
                    params =
                        AbiEventParameters(returnValues = mapOf("totalAmount" to "not-a-number")),
                )
            assertThrows(IllegalStateException::class.java) { XAllocEventUtils.getTotalAmount(e) }
        }

        @Test
        fun `throws when totalAmount is missing`() {
            val e =
                buildIndexedEvent(
                    id = "total-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { XAllocEventUtils.getTotalAmount(e) }
        }
    }

    @Nested
    inner class GetUnallocatedAmountTest {
        @Test
        fun `returns BigInteger directly`() {
            val amount = BigInteger("500000000000000000")
            val e =
                buildIndexedEvent(
                    id = "unalloc-big",
                    params = AbiEventParameters(returnValues = mapOf("unallocatedAmount" to amount)),
                )
            assertEquals(amount, XAllocEventUtils.getUnallocatedAmount(e))
        }

        @Test
        fun `converts Number to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "unalloc-number",
                    params = AbiEventParameters(returnValues = mapOf("unallocatedAmount" to 2500L)),
                )
            assertEquals(BigInteger("2500"), XAllocEventUtils.getUnallocatedAmount(e))
        }

        @Test
        fun `parses String to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "unalloc-string",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("unallocatedAmount" to "1000000000000000000")
                        ),
                )
            assertEquals(
                BigInteger("1000000000000000000"),
                XAllocEventUtils.getUnallocatedAmount(e),
            )
        }

        @Test
        fun `throws on invalid String format`() {
            val e =
                buildIndexedEvent(
                    id = "unalloc-invalid",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("unallocatedAmount" to "invalid-amount")
                        ),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getUnallocatedAmount(e)
            }
        }

        @Test
        fun `throws when unallocatedAmount is missing`() {
            val e =
                buildIndexedEvent(
                    id = "unalloc-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getUnallocatedAmount(e)
            }
        }
    }

    @Nested
    inner class GetTeamAllocationAmountTest {
        @Test
        fun `returns BigInteger directly`() {
            val amount = BigInteger("300000000000000000")
            val e =
                buildIndexedEvent(
                    id = "team-big",
                    params =
                        AbiEventParameters(returnValues = mapOf("teamAllocationAmount" to amount)),
                )
            assertEquals(amount, XAllocEventUtils.getTeamAllocationAmount(e))
        }

        @Test
        fun `converts Number to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "team-number",
                    params =
                        AbiEventParameters(returnValues = mapOf("teamAllocationAmount" to 1500L)),
                )
            assertEquals(BigInteger("1500"), XAllocEventUtils.getTeamAllocationAmount(e))
        }

        @Test
        fun `parses String to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "team-string",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("teamAllocationAmount" to "750000000000000000")
                        ),
                )
            assertEquals(
                BigInteger("750000000000000000"),
                XAllocEventUtils.getTeamAllocationAmount(e),
            )
        }

        @Test
        fun `throws on invalid String format`() {
            val e =
                buildIndexedEvent(
                    id = "team-invalid",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("teamAllocationAmount" to "bad-amount")
                        ),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getTeamAllocationAmount(e)
            }
        }

        @Test
        fun `throws when teamAllocationAmount is missing`() {
            val e =
                buildIndexedEvent(
                    id = "team-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getTeamAllocationAmount(e)
            }
        }
    }

    @Nested
    inner class GetRewardsAllocationAmountTest {
        @Test
        fun `returns BigInteger directly`() {
            val amount = BigInteger("200000000000000000")
            val e =
                buildIndexedEvent(
                    id = "rewards-big",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("rewardsAllocationAmount" to amount)
                        ),
                )
            assertEquals(amount, XAllocEventUtils.getRewardsAllocationAmount(e))
        }

        @Test
        fun `converts Number to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "rewards-number",
                    params =
                        AbiEventParameters(returnValues = mapOf("rewardsAllocationAmount" to 1000L)),
                )
            assertEquals(BigInteger("1000"), XAllocEventUtils.getRewardsAllocationAmount(e))
        }

        @Test
        fun `parses String to BigInteger`() {
            val e =
                buildIndexedEvent(
                    id = "rewards-string",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("rewardsAllocationAmount" to "500000000000000000")
                        ),
                )
            assertEquals(
                BigInteger("500000000000000000"),
                XAllocEventUtils.getRewardsAllocationAmount(e),
            )
        }

        @Test
        fun `throws on invalid String format`() {
            val e =
                buildIndexedEvent(
                    id = "rewards-invalid",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("rewardsAllocationAmount" to "oops")
                        ),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getRewardsAllocationAmount(e)
            }
        }

        @Test
        fun `throws when rewardsAllocationAmount is missing`() {
            val e =
                buildIndexedEvent(
                    id = "rewards-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getRewardsAllocationAmount(e)
            }
        }
    }

    @Nested
    inner class GetTotalAmountAsDecimalTest {
        @Test
        fun `scales down 10^18 to decimal`() {
            val amount = BigInteger("1000000000000000000") // 1 with 18 zeros
            val e =
                buildIndexedEvent(
                    id = "total-decimal",
                    params = AbiEventParameters(returnValues = mapOf("totalAmount" to amount)),
                )
            assertEquals(
                BigDecimal("1.000000000000000000"),
                XAllocEventUtils.getTotalAmountAsDecimal(e),
            )
        }

        @Test
        fun `scales down partial amounts correctly`() {
            val amount = BigInteger("5500000000000000000") // 5.5 with 18 zeros
            val e =
                buildIndexedEvent(
                    id = "total-partial",
                    params = AbiEventParameters(returnValues = mapOf("totalAmount" to amount)),
                )
            assertEquals(
                BigDecimal("5.500000000000000000"),
                XAllocEventUtils.getTotalAmountAsDecimal(e),
            )
        }

        @Test
        fun `handles small amounts below 1`() {
            val amount = BigInteger("123456789000000000") // 0.123456789
            val e =
                buildIndexedEvent(
                    id = "total-small",
                    params = AbiEventParameters(returnValues = mapOf("totalAmount" to amount)),
                )
            assertEquals(
                BigDecimal("0.123456789000000000"),
                XAllocEventUtils.getTotalAmountAsDecimal(e),
            )
        }

        @Test
        fun `handles zero amount`() {
            val amount = BigInteger("0")
            val e =
                buildIndexedEvent(
                    id = "total-zero",
                    params = AbiEventParameters(returnValues = mapOf("totalAmount" to amount)),
                )
            assertEquals(
                BigDecimal("0.000000000000000000"),
                XAllocEventUtils.getTotalAmountAsDecimal(e),
            )
        }

        @Test
        fun `throws on missing totalAmount`() {
            val e =
                buildIndexedEvent(
                    id = "total-decimal-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getTotalAmountAsDecimal(e)
            }
        }
    }

    @Nested
    inner class GetUnallocatedAmountAsDecimalTest {
        @Test
        fun `scales down 10^18 to decimal`() {
            val amount = BigInteger("500000000000000000")
            val e =
                buildIndexedEvent(
                    id = "unalloc-decimal",
                    params = AbiEventParameters(returnValues = mapOf("unallocatedAmount" to amount)),
                )
            assertEquals(
                BigDecimal("0.500000000000000000"),
                XAllocEventUtils.getUnallocatedAmountAsDecimal(e),
            )
        }

        @Test
        fun `scales down correctly with Number input`() {
            val e =
                buildIndexedEvent(
                    id = "unalloc-decimal-number",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("unallocatedAmount" to 2500000000000000000L)
                        ),
                )
            assertEquals(
                BigDecimal("2.500000000000000000"),
                XAllocEventUtils.getUnallocatedAmountAsDecimal(e),
            )
        }

        @Test
        fun `throws on missing unallocatedAmount`() {
            val e =
                buildIndexedEvent(
                    id = "unalloc-decimal-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getUnallocatedAmountAsDecimal(e)
            }
        }
    }

    @Nested
    inner class GetTeamAllocationAmountAsDecimalTest {
        @Test
        fun `scales down 10^18 to decimal`() {
            val amount = BigInteger("300000000000000000")
            val e =
                buildIndexedEvent(
                    id = "team-decimal",
                    params =
                        AbiEventParameters(returnValues = mapOf("teamAllocationAmount" to amount)),
                )
            assertEquals(
                BigDecimal("0.300000000000000000"),
                XAllocEventUtils.getTeamAllocationAmountAsDecimal(e),
            )
        }

        @Test
        fun `scales down correctly with String input`() {
            val e =
                buildIndexedEvent(
                    id = "team-decimal-string",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("teamAllocationAmount" to "750000000000000000")
                        ),
                )
            assertEquals(
                BigDecimal("0.750000000000000000"),
                XAllocEventUtils.getTeamAllocationAmountAsDecimal(e),
            )
        }

        @Test
        fun `throws on missing teamAllocationAmount`() {
            val e =
                buildIndexedEvent(
                    id = "team-decimal-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getTeamAllocationAmountAsDecimal(e)
            }
        }
    }

    @Nested
    inner class GetRewardsAllocationAmountAsDecimalTest {
        @Test
        fun `scales down 10^18 to decimal`() {
            val amount = BigInteger("200000000000000000")
            val e =
                buildIndexedEvent(
                    id = "rewards-decimal",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("rewardsAllocationAmount" to amount)
                        ),
                )
            assertEquals(
                BigDecimal("0.200000000000000000"),
                XAllocEventUtils.getRewardsAllocationAmountAsDecimal(e),
            )
        }

        @Test
        fun `scales down large amounts correctly`() {
            val amount = BigInteger("10000000000000000000") // 10 with 18 zeros
            val e =
                buildIndexedEvent(
                    id = "rewards-decimal-large",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("rewardsAllocationAmount" to amount)
                        ),
                )
            assertEquals(
                BigDecimal("10.000000000000000000"),
                XAllocEventUtils.getRewardsAllocationAmountAsDecimal(e),
            )
        }

        @Test
        fun `throws on missing rewardsAllocationAmount`() {
            val e =
                buildIndexedEvent(
                    id = "rewards-decimal-missing",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) {
                XAllocEventUtils.getRewardsAllocationAmountAsDecimal(e)
            }
        }
    }
}
