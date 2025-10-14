package org.vechain.indexer.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TimestampUtilsTest {

    // VeChain blocks are produced every 10 seconds (SLOT_STEP)
    private val slotStep = 10L

    // Test isHourly function - checks if hour boundary is crossed
    @Test
    fun `test isHourly returns true when crossing hour boundary`() {
        // Cross from 59:50 to 00:10 (boundary at 00:00 is crossed in between)
        // Steps checked: 3600 (which is 3600 % 3600 == 0)
        assertTrue(TimestampUtils.isHourly(3590L, 3610L))

        // Cross from 59:00 to 01:00 (hour boundary within range)
        // Steps checked: 3550, 3560, ..., 3600 (hits 3600)
        assertTrue(TimestampUtils.isHourly(3540L, 3650L))

        // Multiple 10-second steps crossing hour boundary
        // Steps checked: 3590, 3600, 3610 (hits 3600)
        assertTrue(TimestampUtils.isHourly(3580L, 3620L))
    }

    @Test
    fun `test isHourly returns false when not crossing hour boundary`() {
        // Within same hour
        assertFalse(TimestampUtils.isHourly(3500L, 3550L))

        // Just before hour boundary
        assertFalse(TimestampUtils.isHourly(3570L, 3590L))

        // Just after hour boundary
        assertFalse(TimestampUtils.isHourly(3600L, 3650L))
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
        // Cross from 23:59:50 to 00:00:00 (day boundary)
        assertTrue(TimestampUtils.isDaily(86390L, 86410L))

        // Cross day boundary with larger gap
        assertTrue(TimestampUtils.isDaily(86300L, 86500L))
    }

    @Test
    fun `test isDaily returns false when not crossing day boundary`() {
        // Within same day
        assertFalse(TimestampUtils.isDaily(3600L, 7200L))

        // Just before day boundary
        assertFalse(TimestampUtils.isDaily(86300L, 86390L))

        // Just after day boundary
        assertFalse(TimestampUtils.isDaily(86400L, 90000L))
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
    fun `test isWeekly returns true when crossing week boundary`() {
        // Cross week boundary (604800 seconds = 1 week)
        assertTrue(TimestampUtils.isWeekly(604790L, 604810L))

        // Cross with larger gap
        assertTrue(TimestampUtils.isWeekly(604700L, 604900L))
    }

    @Test
    fun `test isWeekly returns false when not crossing week boundary`() {
        // Within same week
        assertFalse(TimestampUtils.isWeekly(86400L, 172800L))

        // Just before week boundary
        assertFalse(TimestampUtils.isWeekly(604700L, 604790L))

        // Just after week boundary
        assertFalse(TimestampUtils.isWeekly(604800L, 700000L))
    }

    // Test isMonthly function - checks if month boundary is crossed (30 days)
    @Test
    fun `test isMonthly returns true when crossing month boundary`() {
        // Cross month boundary (2592000 seconds = 30 days)
        assertTrue(TimestampUtils.isMonthly(2591990L, 2592010L))

        // Cross with larger gap
        assertTrue(TimestampUtils.isMonthly(2591900L, 2592100L))
    }

    @Test
    fun `test isMonthly returns false when not crossing month boundary`() {
        // Within same month
        assertFalse(TimestampUtils.isMonthly(604800L, 1209600L))

        // Just before month boundary
        assertFalse(TimestampUtils.isMonthly(2591900L, 2591990L))

        // Just after month boundary
        assertFalse(TimestampUtils.isMonthly(2592000L, 2700000L))
    }

    // Test isMultipleOf - the core logic
    @Test
    fun `test isMultipleOf with custom multiple`() {
        // Test with 60 seconds (1 minute)
        assertTrue(TimestampUtils.isMultipleOf(50L, 70L, 60L))
        assertFalse(TimestampUtils.isMultipleOf(70L, 90L, 60L))
    }

    @Test
    fun `test isMultipleOf checks all 10-second steps`() {
        // With SLOT_STEP = 10, it checks 3610, 3620, 3630, etc.
        // Should find 3600 is not checked, but 3610, 3620 are checked
        // So crossing 3600 boundary at step 3610
        assertTrue(TimestampUtils.isMultipleOf(3590L, 3610L, 3600L))

        // Should not find boundary if all steps miss it
        assertFalse(TimestampUtils.isMultipleOf(3610L, 3630L, 3600L))
    }

    @Test
    fun `test isMultipleOf with exact boundary hit`() {
        // When the step lands exactly on boundary
        assertTrue(TimestampUtils.isMultipleOf(3590L, 3620L, 3600L))
    }

    // Test validateTimestamps function
    @Test
    fun `test validateTimestamps throws when current is not greater than previous`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TimestampUtils.validateTimestamps(3600L, 3600L)
            }
        assertTrue(exception.message!!.contains("must be greater than"))
    }

    @Test
    fun `test validateTimestamps throws when current is less than previous`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TimestampUtils.validateTimestamps(3600L, 3590L)
            }
        assertTrue(exception.message!!.contains("must be greater than"))
    }

    @Test
    fun `test validateTimestamps throws when timestamp is not multiple of SLOT_STEP`() {
        // 3605 is not a multiple of 10
        val exception =
            assertThrows<IllegalArgumentException> {
                TimestampUtils.validateTimestamps(3600L, 3605L)
            }
        assertTrue(exception.message!!.contains("Invalid block timestamp"))
    }

    @Test
    fun `test validateTimestamps succeeds with valid timestamps`() {
        // Should not throw
        assertDoesNotThrow { TimestampUtils.validateTimestamps(3600L, 3610L) }
    }

    // Test checkValidTimestamp function
    @Test
    fun `test checkValidTimestamp throws for invalid timestamp`() {
        val exception =
            assertThrows<IllegalArgumentException> { TimestampUtils.checkValidTimestamp(3605L) }
        assertTrue(exception.message!!.contains("Invalid block timestamp"))
    }

    @Test
    fun `test checkValidTimestamp succeeds for valid timestamp`() {
        // Should not throw for multiples of 10
        assertDoesNotThrow {
            TimestampUtils.checkValidTimestamp(3600L)
            TimestampUtils.checkValidTimestamp(3610L)
            TimestampUtils.checkValidTimestamp(0L)
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
        assertTrue(TimestampUtils.isHourly(0L, 7200L))

        // But only reports first boundary crossing
        // Should find 3600 before 7200
    }

    @Test
    fun `test no boundaries crossed in small gap`() {
        // Two consecutive blocks (10 seconds apart) rarely cross boundaries
        assertFalse(TimestampUtils.isHourly(3610L, 3620L))
        assertFalse(TimestampUtils.isDaily(3610L, 3620L))
        assertFalse(TimestampUtils.isWeekly(3610L, 3620L))
        assertFalse(TimestampUtils.isMonthly(3610L, 3620L))
    }

    @Test
    fun `test boundary crossed at first step`() {
        // Previous is 3590, first step is 3600 (boundary)
        // Current is 3610
        assertTrue(TimestampUtils.isHourly(3590L, 3610L))
    }

    @Test
    fun `test boundary crossed at last step before current`() {
        // Previous is 3580, steps are 3590, 3600 (boundary)
        // Current is 3610
        assertTrue(TimestampUtils.isHourly(3580L, 3610L))
    }
}
