package org.vechain.indexer.b3tr.action

import com.mongodb.assertions.Assertions.assertNotNull
import com.mongodb.assertions.Assertions.assertNull
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.accumulateImpacts
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getFrom
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getProof
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getTo
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getValue
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

class ActionSummaryUtilsTest {
    val validProof =
        "{\"app_name\": \"NFBC\",\"action_type\": \"sustainable_literature_consumption\",\"proof\": {\"proof_type\": \"minutes_streamed\",\"proof_data\": \"1 minutes\"},\"impact\": {\"carbon\": \"8\",\"timber\": \"5\"},\"metadata\": {\"description\": \"Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.\"}}"

    @Nested
    inner class AccumulateImpactsTests {
        @Test
        fun `accumulateImpacts returns null for empty list`() {
            val out = accumulateImpacts(emptyList())
            assertNull(out)
        }

        @Test
        fun `accumulateImpacts sums impacts correctly`() {
            val impacts =
                listOf(
                    Impact(carbon = 1, water = 2, energy = 3),
                    Impact(carbon = 4, water = 5, energy = 6),
                    Impact(carbon = 7, water = 8, energy = 9),
                )
            val out = accumulateImpacts(impacts)

            assertNotNull(out)
            assertEquals(12L, out?.carbon)
            assertEquals(15L, out?.water)
            assertEquals(18L, out?.energy)
        }

        @Test
        fun `accumulateImpacts sums only non-null fields`() {
            val impacts =
                listOf(
                    Impact(carbon = 1, water = null, energy = 5),
                    Impact(carbon = 3, water = 7, energy = null),
                    Impact(carbon = null, water = 2, energy = 1),
                )
            val out = accumulateImpacts(impacts)

            assertNotNull(out)
            assertEquals(4L, out?.carbon)
            assertEquals(9L, out?.water)
            assertEquals(6L, out?.energy)
        }
    }

    @Nested
    inner class GetToTests {
        @Test
        fun `getTo gets the to param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("to" to "0x34234")),
                )
            assertEquals("0x34234", getTo(event))
        }

        @Test
        fun `getTo throws error if to param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { getTo(event) }
        }

        @Test
        fun `getTo is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("To" to "0x34234")),
                )
            assertThrows(IllegalStateException::class.java) { getTo(event) }
        }
    }

    @Nested
    inner class GetFromTests {
        @Test
        fun `getFrom gets the from param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("from" to "0x34234")),
                )
            assertEquals("0x34234", getFrom(event))
        }

        @Test
        fun `getFrom throws error if from param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { getFrom(event) }
        }

        @Test
        fun `getFrom is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("From" to "0x34234")),
                )
            assertThrows(IllegalStateException::class.java) { getFrom(event) }
        }
    }

    @Nested
    inner class GetValueTests {
        @Test
        fun `getValue gets the value param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("value" to "12345000000000000000000")
                        ),
                )

            val result = getValue(event)
            assertEquals(result.compareTo(12345.toBigDecimal()), 0)
        }

        @Test
        fun `getValue throws error if value param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { getValue(event) }
        }

        @Test
        fun `getValue is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("Value" to "12345000000000000000000")
                        ),
                )
            assertThrows(IllegalStateException::class.java) { getValue(event) }
        }
    }

    @Nested
    inner class GetAppIdTests {
        @Test
        fun `getAppId gets the appId param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "myApp")),
                )
            assertEquals("myApp", getAppId(event))
        }

        @Test
        fun `getAppId throws error if appId param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { getAppId(event) }
        }

        @Test
        fun `getAppId is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("AppId" to "myApp")),
                )
            assertThrows(IllegalStateException::class.java) { getAppId(event) }
        }
    }

    @Nested
    inner class GetProofTests {
        @Test
        fun `getProof returns null if proof param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            val proof = getProof(event)
            assertNull(proof)
        }

        @Test
        fun `getProof returns null if proof param is empty string`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("proof" to "")),
                )
            val proof = getProof(event)
            assertNull(proof)
        }

        @Test
        fun `getProof returns even if invalid json`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("proof" to "not json")),
                )
            val proof = getProof(event)
            assertEquals("not json", proof?.description)
        }

        @Test
        fun `getProof parses valid proof JSON correctly`() {

            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("proof" to validProof)),
                )
            val proof = getProof(event)
            assertNotNull(proof)
            assertEquals(1, proof?.version)
            assertEquals(8, proof?.impact?.carbon)
            assertEquals(5, proof?.impact?.timber)
            assertEquals(
                "Consumed a chapter of literature sustainably, reducing carbon footprint and timber usage when compared to print.",
                proof?.description,
            )
        }
    }

    @Nested
    inner class GetActionTests {
        @Test
        fun `getAction constructs Action correctly`() {

            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "to" to "0xToAddress",
                                    "from" to "0xFromAddress",
                                    "value" to "1000000000000000000000",
                                    "appId" to "myApp",
                                    "proof" to validProof,
                                )
                        ),
                )
            val action = ActionSummaryUtils.getAction(event)
            assertEquals("myApp", action.appId)
            assertEquals("0xToAddress", action.receiver)
            assertEquals("0xFromAddress", action.distributor)
            assertEquals(action.amount.compareTo(BigDecimal(1000)), 0)
            assertNotNull(action.proof)
            assertEquals(1, action.proof?.version)
            assertEquals(8, action.proof?.impact?.carbon)
            assertEquals(5, action.proof?.impact?.timber)
        }
    }

    @Nested
    inner class GetEntityTests {
        @Test
        fun `getEntity returns correct entity for user`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("to" to "0xToAddress", "from" to "0xFromAddress")
                        ),
                )
            val toEntity = ActionSummaryUtils.getEntity(event, EntityType.USER)

            assertEquals("0xToAddress", toEntity)
        }

        @Test
        fun `getEntity returns correct entity for app`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "appId" to "myApp",
                                    "to" to "0xToAddress",
                                    "from" to "0xFromAddress",
                                )
                        ),
                )
            val appEntity = ActionSummaryUtils.getEntity(event, EntityType.APP)

            assertEquals("myApp", appEntity)
        }

        @Test
        fun `getEntity returns GLOBAL for global entity type`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "appId" to "myApp",
                                    "to" to "0xToAddress",
                                    "from" to "0xFromAddress",
                                )
                        ),
                )
            val globalEntity = ActionSummaryUtils.getEntity(event, EntityType.GLOBAL)

            assertEquals("GLOBAL", globalEntity)
        }

        @Test
        fun `getEntity is case sensitive for USER entity type`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("To" to "0xToAddress", "from" to "0xFromAddress")
                        ),
                )
            assertThrows(IllegalStateException::class.java) {
                ActionSummaryUtils.getEntity(event, EntityType.USER)
            }
        }
    }

    @Nested
    inner class GroupByToTest {
        @Test
        fun `groupByTo groups events by to address`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("to" to "0xABC")),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(returnValues = mapOf("to" to "0xabc")),
                )
            val event3 =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("to" to "0xDEF")),
                )

            val groupedEvents = ActionSummaryUtils.groupByTo(listOf(event1, event2, event3))

            assertEquals(2, groupedEvents.size)
            assertTrue(groupedEvents.containsKey("0xabc"))
            assertTrue(groupedEvents.containsKey("0xdef"))
            assertEquals(2, groupedEvents["0xabc"]!!.size)
            assertEquals(1, groupedEvents["0xdef"]!!.size)
        }

        @Test
        fun `groupByTo throws an error if to is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(mapOf("notto" to "value")),
                )
            assertThrows(IllegalStateException::class.java) {
                ActionSummaryUtils.groupByTo(listOf(event))
            }
        }
    }

    @Nested
    inner class GroupByAppIdTest {
        @Test
        fun `groupByAppId groups events by appId`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "123")),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "123")),
                )
            val event3 =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "456")),
                )

            val groupedEvents = groupByAppId(listOf(event1, event2, event3))

            assertEquals(2, groupedEvents.size)
            assertTrue(groupedEvents.containsKey("123"))
            assertTrue(groupedEvents.containsKey("456"))
            assertEquals(2, groupedEvents["123"]!!.size)
            assertEquals(1, groupedEvents["456"]!!.size)
        }

        @Test
        fun `groupByAppId throws an error if appId is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(mapOf("notappid" to "value")),
                )
            assertThrows(IllegalStateException::class.java) { groupByAppId(listOf(event)) }
        }
    }
}
