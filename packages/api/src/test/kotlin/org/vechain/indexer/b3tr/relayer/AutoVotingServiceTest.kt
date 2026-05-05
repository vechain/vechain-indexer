package org.vechain.indexer.b3tr.relayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.vechain.indexer.exception.BadRequestException

@DataMongoTest
@ActiveProfiles("test", "b3tr", "b3tr-auto-voting-toggles", "b3tr-auto-voting-users")
@ContextConfiguration(classes = [AutoVotingServiceTest.TestApp::class])
internal class AutoVotingServiceTest {

    @SpringBootApplication open class TestApp

    @Autowired private lateinit var template: MongoTemplate

    private lateinit var service: AutoVotingService

    @BeforeEach
    fun setUp() {
        service = AutoVotingService(template)
        template.dropCollection(AutoVotingToggle::class.java)
    }

    private fun seed(
        address: String,
        enabled: Boolean,
        activeFromRound: Int,
        blockNumber: Long = activeFromRound * 100L,
    ) {
        val addr = address.lowercase()
        template.insert(
            AutoVotingToggle(
                id = "$addr:$activeFromRound",
                address = addr,
                enabled = enabled,
                activeFromRound = activeFromRound,
                blockId = "block-$blockNumber",
                blockNumber = blockNumber,
                blockTimestamp = blockNumber * 10,
                version = 1,
            )
        )
    }

    @Test
    fun `roundId less than 1 fails with BadRequest`() {
        assertThrows(BadRequestException::class.java) { service.findEnabledAddressesAtRound(0) }
    }

    @Test
    fun `returns empty list when no toggles exist`() {
        assertTrue(service.findEnabledAddressesAtRound(10).isEmpty())
    }

    @Test
    fun `activeFromRound = R is effective at round R`() {
        // Toggle on in source round 5 → activeFromRound = 6.
        seed(address = "0xa", enabled = true, activeFromRound = 6)

        assertTrue(service.findEnabledAddressesAtRound(5).isEmpty())
        assertEquals(listOf("0xa"), service.findEnabledAddressesAtRound(6))
        assertEquals(listOf("0xa"), service.findEnabledAddressesAtRound(7))
    }

    @Test
    fun `latest activeFromRound per address wins`() {
        // userA: on with activeFromRound=6, off with activeFromRound=8.
        // userB: on with activeFromRound=7.
        seed("0xA", enabled = true, activeFromRound = 6)
        seed("0xA", enabled = false, activeFromRound = 8)
        seed("0xB", enabled = true, activeFromRound = 7)

        // At round 6: only A is on (B's row not yet effective).
        assertEquals(listOf("0xa"), service.findEnabledAddressesAtRound(6))
        // At round 7: A still on (off-row activeFromRound=8 not yet), B on.
        assertEquals(listOf("0xa", "0xb"), service.findEnabledAddressesAtRound(7))
        // At round 8: A's off-row is effective; only B remains.
        assertEquals(listOf("0xb"), service.findEnabledAddressesAtRound(8))
    }

    @Test
    fun `returns full set in one call`() {
        listOf("0x1", "0x2", "0x3", "0x4", "0x5", "0x6", "0x7").forEach { addr ->
            seed(address = addr, enabled = true, activeFromRound = 1)
        }

        val result = service.findEnabledAddressesAtRound(2)

        assertEquals(listOf("0x1", "0x2", "0x3", "0x4", "0x5", "0x6", "0x7"), result)
    }
}
