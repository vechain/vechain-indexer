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
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAmount
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getDistributor
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getProof
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.isImpactAboveThreshold
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.validateAndFilterImpacts
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
    inner class GetReceiverTests {
        @Test
        fun `getReceiver gets the to param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("receiver" to "0x34234")),
                )
            assertEquals("0x34234", getReceiver(event))
        }

        @Test
        fun `getReceiver throws error if to param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { getReceiver(event) }
        }

        @Test
        fun `getReceiver is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("Receiver" to "0x34234")),
                )
            assertThrows(IllegalStateException::class.java) { getReceiver(event) }
        }
    }

    @Nested
    inner class GetDistributorTests {
        @Test
        fun `getDistributor gets the from param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("distributor" to "0x34234")),
                )
            assertEquals("0x34234", getDistributor(event))
        }

        @Test
        fun `getDistributor throws error if from param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { getDistributor(event) }
        }

        @Test
        fun `getDistributor is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("From" to "0x34234")),
                )
            assertThrows(IllegalStateException::class.java) { getDistributor(event) }
        }
    }

    @Nested
    inner class GetAmountTests {
        @Test
        fun `getAmount gets the value param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("amount" to "12345000000000000000000")
                        ),
                )

            val result = getAmount(event)
            assertEquals(result.compareTo(12345.toBigDecimal()), 0)
        }

        @Test
        fun `getAmount throws error if value param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { getAmount(event) }
        }

        @Test
        fun `getAmount is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("Amount" to "12345000000000000000000")
                        ),
                )
            assertThrows(IllegalStateException::class.java) { getAmount(event) }
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
    inner class GetCycleTests {
        @Test
        fun `getCycle gets the cycle param if available`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("cycle" to "42")),
                )
            assertEquals(42, ActionSummaryUtils.getCycle(event))
        }

        @Test
        fun `getCycle throws error if cycle param is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )
            assertThrows(IllegalStateException::class.java) { ActionSummaryUtils.getCycle(event) }
        }

        @Test
        fun `getCycle is case sensitive`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("Cycle" to "42")),
                )
            assertThrows(IllegalStateException::class.java) { ActionSummaryUtils.getCycle(event) }
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
                                    "receiver" to "0xToAddress",
                                    "distributor" to "0xFromAddress",
                                    "amount" to "1000000000000000000000",
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
                            returnValues =
                                mapOf("receiver" to "0xToAddress", "distributor" to "0xFromAddress")
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
                                    "receiver" to "0xToAddress",
                                    "distributor" to "0xFromAddress",
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
                                    "receiver" to "0xToAddress",
                                    "distributor" to "0xFromAddress",
                                )
                        ),
                )
            val globalEntity = ActionSummaryUtils.getEntity(event, EntityType.GLOBAL)

            assertEquals(EntityType.GLOBAL.name, globalEntity)
        }

        @Test
        fun `getEntity is case sensitive for USER entity type`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("To" to "0xToAddress", "distributor" to "0xFromAddress")
                        ),
                )
            assertThrows(IllegalStateException::class.java) {
                ActionSummaryUtils.getEntity(event, EntityType.USER)
            }
        }
    }

    @Nested
    inner class GroupByReceiverTest {
        @Test
        fun `groupByReceiver groups events by to address`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("receiver" to "0xABC")),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(returnValues = mapOf("receiver" to "0xabc")),
                )
            val event3 =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("receiver" to "0xDEF")),
                )

            val groupedEvents = ActionSummaryUtils.groupByReceiver(listOf(event1, event2, event3))

            assertEquals(2, groupedEvents.size)
            assertTrue(groupedEvents.containsKey("0xabc"))
            assertTrue(groupedEvents.containsKey("0xdef"))
            assertEquals(2, groupedEvents["0xabc"]!!.size)
            assertEquals(1, groupedEvents["0xdef"]!!.size)
        }

        @Test
        fun `groupByReceiver throws an error if to is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(mapOf("notto" to "amount")),
                )
            assertThrows(IllegalStateException::class.java) {
                ActionSummaryUtils.groupByReceiver(listOf(event))
            }
        }
    }

    @Nested
    inner class GroupByDistributorTests {
        @Test
        fun `groupByDistributor groups events by from address`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(returnValues = mapOf("distributor" to "0xABC")),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    params = AbiEventParameters(returnValues = mapOf("distributor" to "0xabc")),
                )
            val event3 =
                buildIndexedEvent(
                    id = "event3",
                    params = AbiEventParameters(returnValues = mapOf("distributor" to "0xDEF")),
                )

            val groupedEvents =
                ActionSummaryUtils.groupByDistributor(listOf(event1, event2, event3))

            assertEquals(2, groupedEvents.size)
            assertTrue(groupedEvents.containsKey("0xabc"))
            assertTrue(groupedEvents.containsKey("0xdef"))
            assertEquals(2, groupedEvents["0xabc"]!!.size)
            assertEquals(1, groupedEvents["0xdef"]!!.size)
        }

        @Test
        fun `groupByDistributor throws an error if from is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event1",
                    params = AbiEventParameters(mapOf("notfrom" to "amount")),
                )
            assertThrows(IllegalStateException::class.java) {
                ActionSummaryUtils.groupByDistributor(listOf(event))
            }
        }
    }

    @Nested
    inner class GroupByAppIdTests {
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
                    params = AbiEventParameters(mapOf("notappid" to "amount")),
                )
            assertThrows(IllegalStateException::class.java) { groupByAppId(listOf(event)) }
        }
    }

    @Nested
    inner class AssertEventTypeTests {
        @Test
        fun `assertEventType does not throw if all events match expected type`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    eventType = "B3TR_ActionReward",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "123")),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    eventType = "B3TR_ActionReward",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "123")),
                )

            ActionSummaryUtils.assertEventTypes(listOf(event1, event2), "B3TR_ActionReward")
        }

        @Test
        fun `assertEventType throws if any event does not match expected type`() {
            val event1 =
                buildIndexedEvent(
                    id = "event1",
                    eventType = "B3TR_ActionReward",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "123")),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event2",
                    eventType = "SomeOtherEvent",
                    params = AbiEventParameters(returnValues = mapOf("appId" to "123")),
                )

            assertThrows(IllegalStateException::class.java) {
                ActionSummaryUtils.assertEventTypes(listOf(event1, event2), "B3TR_ActionReward")
            }
        }
    }

    @Nested
    inner class IsImpactAboveThresholdTests {
        private val testConfig =
            ActionImpactConfig().apply {
                carbon = 1000
                water = 1000
                energy = 1000
                wasteMass = 1000
                timber = 1000
                plastic = 1000
                educationTime = 1000
                treesPlanted = 1000
                caloriesBurned = 1000
                cleanEnergyProductionWh = 1000
                sleepQualityPercentage = 1000
            }

        @Test
        fun `isImpactAboveThreshold returns false when all fields are below threshold`() {
            val impact = Impact(carbon = 100, water = 200, energy = 300)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(false, result)
        }

        @Test
        fun `isImpactAboveThreshold returns false when all fields are null`() {
            val impact = Impact()
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(false, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when carbon exceeds threshold`() {
            val impact = Impact(carbon = 1500, water = 200)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when water exceeds threshold`() {
            val impact = Impact(carbon = 100, water = 2000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when energy exceeds threshold`() {
            val impact = Impact(energy = 5000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when waste_mass exceeds threshold`() {
            val impact = Impact(waste_mass = 10000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when timber exceeds threshold`() {
            val impact = Impact(timber = 15000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when plastic exceeds threshold`() {
            val impact = Impact(plastic = 20000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when education_time exceeds threshold`() {
            val impact = Impact(education_time = 25000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when trees_planted exceeds threshold`() {
            val impact = Impact(trees_planted = 30000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when calories_burned exceeds threshold`() {
            val impact = Impact(calories_burned = 35000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when clean_energy_production_wh exceeds threshold`() {
            val impact = Impact(clean_energy_production_wh = 40000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when sleep_quality_percentage exceeds threshold`() {
            val impact = Impact(sleep_quality_percentage = 45000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when any one field exceeds threshold among many`() {
            val impact =
                Impact(
                    carbon = 100,
                    water = 200,
                    energy = 300,
                    waste_mass = 5000, // This exceeds threshold
                    timber = 100,
                    plastic = 50,
                )
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold returns false when value equals threshold`() {
            val impact = Impact(carbon = 1000)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(false, result)
        }

        @Test
        fun `isImpactAboveThreshold returns true when value is threshold plus one`() {
            val impact = Impact(carbon = 1001)
            val result = isImpactAboveThreshold(impact, testConfig)
            assertEquals(true, result)
        }

        @Test
        fun `isImpactAboveThreshold handles realistic VeBetterDAO values`() {
            val realisticConfig = ActionImpactConfig()
            // Based on
            // https://docs.vebetterdao.org/developer-guides/sustainability-proof-and-impacts
            // 1 liter of water = 1000 ml
            val waterImpact = Impact(water = 1000)
            assertEquals(false, isImpactAboveThreshold(waterImpact, realisticConfig))

            // 100 grams of carbon
            val carbonImpact = Impact(carbon = 100)
            assertEquals(false, isImpactAboveThreshold(carbonImpact, realisticConfig))

            // Unrealistic: 1 million liters (1 billion ml) of water saved
            val unrealisticWaterImpact = Impact(water = 1_000_000_000)
            assertEquals(true, isImpactAboveThreshold(unrealisticWaterImpact, realisticConfig))
        }
    }

    @Nested
    inner class ValidateAndFilterImpactsTests {
        private val testConfig =
            ActionImpactConfig().apply {
                carbon = 1000
                water = 1000
                energy = 1000
                wasteMass = 1000
            }

        @Test
        fun `validateAndFilterImpacts returns empty list when all impacts exceed threshold`() {
            val impacts = listOf(Impact(carbon = 2000), Impact(water = 3000), Impact(energy = 4000))
            val result = validateAndFilterImpacts(impacts, testConfig)
            assertEquals(0, result.size)
        }

        @Test
        fun `validateAndFilterImpacts returns all impacts when none exceed threshold`() {
            val impacts = listOf(Impact(carbon = 100), Impact(water = 200), Impact(energy = 300))
            val result = validateAndFilterImpacts(impacts, testConfig)
            assertEquals(3, result.size)
        }

        @Test
        fun `validateAndFilterImpacts filters out only impacts that exceed threshold`() {
            val impact1 = Impact(carbon = 100, water = 200)
            val impact2 = Impact(carbon = 5000) // Exceeds threshold
            val impact3 = Impact(energy = 300)
            val impact4 = Impact(water = 10000) // Exceeds threshold

            val impacts = listOf(impact1, impact2, impact3, impact4)
            val result = validateAndFilterImpacts(impacts, testConfig)

            assertEquals(2, result.size)
            assertTrue(result.contains(impact1))
            assertTrue(result.contains(impact3))
            assertEquals(false, result.contains(impact2))
            assertEquals(false, result.contains(impact4))
        }

        @Test
        fun `validateAndFilterImpacts handles empty list`() {
            val result = validateAndFilterImpacts(emptyList(), testConfig)
            assertEquals(0, result.size)
        }

        @Test
        fun `validateAndFilterImpacts handles impacts with null fields`() {
            val impacts =
                listOf(
                    Impact(carbon = null, water = 100),
                    Impact(carbon = 200, water = null),
                    Impact(),
                )
            val result = validateAndFilterImpacts(impacts, testConfig)
            assertEquals(3, result.size)
        }

        @Test
        fun `validateAndFilterImpacts preserves impact objects that pass validation`() {
            val impact = Impact(carbon = 100, water = 200, energy = 300, timber = 50)
            val result = validateAndFilterImpacts(listOf(impact), testConfig)

            assertEquals(1, result.size)
            assertEquals(impact, result.first())
            assertEquals(100, result.first().carbon)
            assertEquals(200, result.first().water)
            assertEquals(300, result.first().energy)
            assertEquals(50, result.first().timber)
        }

        @Test
        fun `validateAndFilterImpacts works with VeBetterDAO realistic scenario`() {
            val realisticConfig = ActionImpactConfig()
            // Real-world sustainability impacts based on VeBetterDAO docs
            val normalImpacts =
                listOf(
                    Impact(carbon = 8, timber = 5), // Literature consumption
                    Impact(water = 500, plastic = 10), // Recycling
                    Impact(waste_mass = 300, biodiversity = 1), // Litter picking
                    Impact(trees_planted = 1), // Tree planting
                    Impact(education_time = 3600), // 1 hour of education (seconds)
                )

            // One unrealistic impact: 1 billion grams of carbon (1 million kg!)
            val unrealisticImpact = Impact(carbon = 1_000_000_000)

            val allImpacts = normalImpacts + unrealisticImpact
            val result = validateAndFilterImpacts(allImpacts, realisticConfig)

            assertEquals(5, result.size) // Should filter out the unrealistic one
            assertEquals(false, result.contains(unrealisticImpact))
        }

        @Test
        fun `validateAndFilterImpacts with zero threshold filters all non-zero impacts`() {
            val zeroConfig =
                ActionImpactConfig().apply {
                    carbon = 0
                    water = 0
                }
            val impacts = listOf(Impact(carbon = 1), Impact(water = 1), Impact())
            val result = validateAndFilterImpacts(impacts, zeroConfig)
            assertEquals(1, result.size) // Only the empty impact should pass
        }

        @Test
        fun `validateAndFilterImpacts with very high threshold allows all realistic values`() {
            val highConfig = ActionImpactConfig()
            val impacts =
                listOf(Impact(carbon = 999999), Impact(water = 500000), Impact(energy = 750000))
            val result = validateAndFilterImpacts(impacts, highConfig)
            assertEquals(3, result.size)
        }
    }
}
