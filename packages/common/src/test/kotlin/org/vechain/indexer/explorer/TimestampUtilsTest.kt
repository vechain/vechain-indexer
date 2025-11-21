package org.vechain.indexer.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TimestampUtilsTest {

    // VeChain blocks are produced every 10 seconds (SLOT_STEP)
    private val slotStep = 10L

    // Testnet genesis timestamp
    private val genesisTimestamp = 1530014400L

    // Test isHourly function - checks if hour boundary is crossed
    @Test
    fun `test isHourly returns true when crossing hour boundary`() {
        // Cross from 59:50 to 00:10 (boundary at 00:00 is crossed in between)
        // Steps checked: 1530014400 + 3600 = 1530018000 (which is divisible by 3600)
        assertTrue(TimestampUtils.isHourly(genesisTimestamp + 3590, genesisTimestamp + 3610))

        // Cross from 59:00 to 01:00 (hour boundary within range)
        assertTrue(TimestampUtils.isHourly(genesisTimestamp + 3540, genesisTimestamp + 3650))

        // Multiple 10-second steps crossing hour boundary
        assertTrue(TimestampUtils.isHourly(genesisTimestamp + 3580, genesisTimestamp + 3620))
    }

    @Test
    fun `test isHourly returns false when not crossing hour boundary`() {
        // Within same hour
        assertFalse(TimestampUtils.isHourly(genesisTimestamp + 3500, genesisTimestamp + 3550))

        // Just before hour boundary
        assertFalse(TimestampUtils.isHourly(genesisTimestamp + 3570, genesisTimestamp + 3590))

        // Just after hour boundary
        assertFalse(TimestampUtils.isHourly(genesisTimestamp + 3600, genesisTimestamp + 3650))
    }

    @Test
    fun `test isHourly with realistic VeChain timestamps`() {
        // VeChain mainnet genesis: July 2018
        // Timestamps around 1704067200 (Jan 1, 2024, 00:00:00 UTC)

        // Cross hour boundary: 1704067200 is divisible by 3600
        // Steps checked: 1704067200 (hits boundary)
        assertTrue(TimestampUtils.isHourly(1704067190L, 1704067210L))

        // Within same hour - no boundary between these
        assertFalse(TimestampUtils.isHourly(1704067210L, 1704067300L))
    }

    // Test isDaily function - checks if day boundary is crossed
    @Test
    fun `test isDaily returns true when crossing day boundary`() {
        // Cross from 23:59:50 to 00:00:10 (day boundary)
        // Using known boundary: 1704153600 (Jan 2, 2024, 00:00:00)
        assertTrue(TimestampUtils.isDaily(1704153590L, 1704153610L))

        // Cross day boundary with larger gap
        assertTrue(TimestampUtils.isDaily(1704153500L, 1704153700L))
    }

    @Test
    fun `test isDaily returns false when not crossing day boundary`() {
        // Within same day
        assertFalse(TimestampUtils.isDaily(genesisTimestamp + 3600, genesisTimestamp + 7200))

        // Just before day boundary
        assertFalse(TimestampUtils.isDaily(genesisTimestamp + 86300, genesisTimestamp + 86390))

        // Just after day boundary
        assertFalse(TimestampUtils.isDaily(genesisTimestamp + 86400, genesisTimestamp + 90000))
    }

    @Test
    fun `test isDaily with realistic VeChain timestamps`() {
        // Jan 1, 2024, 23:59:50 to Jan 2, 2024, 00:00:10
        // 1704153600 is divisible by 86400 (day boundary)
        // Steps checked: 1704153600 (hits boundary)
        assertTrue(TimestampUtils.isDaily(1704153590L, 1704153610L))

        // Same day - no boundary crossed
        assertFalse(TimestampUtils.isDaily(1704067210L, 1704070800L))
    }

    // Test isWeekly function - checks if week boundary is crossed
    @Test
    fun `test isWeekly returns false when not crossing week boundary`() {
        // Within same week
        assertFalse(TimestampUtils.isWeekly(1704067200L, 1704070800L))
    }

    // Test isMonthly function - checks if month boundary is crossed (30 days)
    @Test
    fun `test isMonthly returns false when not crossing month boundary`() {
        // Within same month
        assertFalse(TimestampUtils.isMonthly(1704067200L, 1704070800L))
    }

    // Test isMultipleOf - the core logic
    @Test
    fun `test isMultipleOf with custom multiple`() {
        // Test with 60 seconds (1 minute)
        assertTrue(TimestampUtils.isMultipleOf(genesisTimestamp + 50, genesisTimestamp + 70, 60L))
        assertFalse(TimestampUtils.isMultipleOf(genesisTimestamp + 70, genesisTimestamp + 90, 60L))
    }

    @Test
    fun `test isMultipleOf checks all 10-second steps`() {
        // With SLOT_STEP = 10, it checks every 10 seconds
        // So crossing 3600 boundary at step 3610
        assertTrue(
            TimestampUtils.isMultipleOf(genesisTimestamp + 3590, genesisTimestamp + 3610, 3600L)
        )

        // Should not find boundary if all steps miss it
        assertFalse(
            TimestampUtils.isMultipleOf(genesisTimestamp + 3610, genesisTimestamp + 3630, 3600L)
        )
    }

    @Test
    fun `test isMultipleOf with exact boundary hit`() {
        // When the step lands exactly on boundary
        assertTrue(
            TimestampUtils.isMultipleOf(genesisTimestamp + 3590, genesisTimestamp + 3620, 3600L)
        )
    }

    // Test validateTimestamps function
    @Test
    fun `test validateTimestamps throws when current is not greater than previous`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TimestampUtils.validateTimestamps(genesisTimestamp, genesisTimestamp)
            }
        assertTrue(exception.message!!.contains("must be greater than"))
    }

    @Test
    fun `test validateTimestamps throws when current is less than previous`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TimestampUtils.validateTimestamps(genesisTimestamp + 100, genesisTimestamp + 50)
            }
        assertTrue(exception.message!!.contains("must be greater than"))
    }

    @Test
    fun `test validateTimestamps throws when timestamp is below genesis timestamp`() {
        // 1000L is less than TESTNET_GENESIS_TIMESTAMP (1530014400L)
        val exception =
            assertThrows<IllegalArgumentException> {
                TimestampUtils.validateTimestamps(1000L, 2000L)
            }
        assertTrue(exception.message!!.contains("Invalid block timestamp"))
    }

    @Test
    fun `test validateTimestamps succeeds with valid timestamps`() {
        // Should not throw for timestamps >= TESTNET_GENESIS_TIMESTAMP
        assertDoesNotThrow { TimestampUtils.validateTimestamps(1530014400L, 1530014410L) }
    }

    // Test checkValidTimestamp function
    @Test
    fun `test checkValidTimestamp throws for timestamp below genesis`() {
        val exception =
            assertThrows<IllegalArgumentException> { TimestampUtils.checkValidTimestamp(1000L) }
        assertTrue(exception.message!!.contains("Invalid block timestamp"))
    }

    @Test
    fun `test checkValidTimestamp succeeds for valid timestamp`() {
        // Should not throw for timestamps >= TESTNET_GENESIS_TIMESTAMP
        assertDoesNotThrow {
            TimestampUtils.checkValidTimestamp(1530014400L)
            TimestampUtils.checkValidTimestamp(1530014410L)
            TimestampUtils.checkValidTimestamp(1704067200L)
        }
    }

    // Integration tests with realistic scenarios
    @Test
    fun `test realistic VeChain block sequence crossing hour`() {
        // Simulate VeChain blocks at 10-second intervals
        val beforeHour = 1704067190L // 10 seconds before hour (1704067200)
        val atHour = 1704067200L // exactly on hour boundary
        val afterHour = 1704067210L // 10 seconds after hour

        // Before to after crosses hour (checks step 1704067200 which is the boundary)
        assertTrue(TimestampUtils.isHourly(beforeHour, afterHour))

        // At hour to after does not cross hour (no boundary between them)
        assertFalse(TimestampUtils.isHourly(atHour, afterHour))
    }

    @Test
    fun `test realistic VeChain block sequence crossing day`() {
        // Jan 1, 2024, 23:59:50 to Jan 2, 2024, 00:00:00 and beyond
        val beforeMidnight = 1704153590L // 10 seconds before day boundary
        val atMidnight = 1704153600L // exactly on day boundary (divisible by 86400)
        val afterMidnight = 1704153610L // 10 seconds after day boundary

        // Before to after crosses day (checks step 1704153600 which is the boundary)
        assertTrue(TimestampUtils.isDaily(beforeMidnight, afterMidnight))

        // At midnight to after does not cross day (no boundary between them)
        assertFalse(TimestampUtils.isDaily(atMidnight, afterMidnight))
    }

    @Test
    fun `test multiple boundaries crossed in large gap`() {
        // If gap is large enough, multiple boundaries can be crossed
        // From hour 0 to hour 2 crosses hour 1
        assertTrue(TimestampUtils.isHourly(genesisTimestamp, genesisTimestamp + 7200))

        // But only reports first boundary crossing
        // Should find a boundary before 7200 seconds out
    }

    @Test
    fun `test no boundaries crossed in small gap`() {
        // Two consecutive blocks (10 seconds apart) rarely cross boundaries
        assertFalse(TimestampUtils.isHourly(genesisTimestamp + 3610, genesisTimestamp + 3620))
        assertFalse(TimestampUtils.isDaily(genesisTimestamp + 3610, genesisTimestamp + 3620))
        assertFalse(TimestampUtils.isWeekly(genesisTimestamp + 3610, genesisTimestamp + 3620))
        assertFalse(TimestampUtils.isMonthly(genesisTimestamp + 3610, genesisTimestamp + 3620))
    }

    @Test
    fun `test boundary crossed at first step`() {
        // Previous is genesis + 3590, first step is genesis + 3600 (boundary)
        // Current is genesis + 3610
        assertTrue(TimestampUtils.isHourly(genesisTimestamp + 3590, genesisTimestamp + 3610))
    }

    @Test
    fun `test boundary crossed at last step before current`() {
        // Previous is genesis + 3580, steps are genesis + 3590, genesis + 3600 (boundary)
        // Current is genesis + 3610
        assertTrue(TimestampUtils.isHourly(genesisTimestamp + 3580, genesisTimestamp + 3610))
    }
}
