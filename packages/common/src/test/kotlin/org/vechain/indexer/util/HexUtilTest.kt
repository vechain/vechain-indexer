package org.vechain.indexer.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.utils.HexUtil

class HexUtilTest {

    @Nested
    inner class IsValid {
        @Test
        fun `should return false for valid hex - empty`() {
            assertFalse(HexUtil.isValid("0x"))
        }

        @Test
        fun `should return true for valid hex - 0`() {
            assertTrue(HexUtil.isValid("0x0"))
        }

        @Test
        fun `should return true for valid hex - 1`() {
            assertTrue(HexUtil.isValid("0x0"))
        }

        @Test
        fun `should return true hex without a prefix`() {
            assertTrue(HexUtil.isValid("8d66DA6448c6144E894B7cD91Fa1Ae65310A4855"))
        }

        @Test
        fun `should return true for valid hex - 3`() {
            assertTrue(HexUtil.isValid("0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855"))
        }

        @Test
        fun `should return false for bad hex - 1`() {
            assertFalse(HexUtil.isValid("Hello World!!"))
        }

        @Test
        fun `should return false for bad hex - 2`() {
            assertFalse(HexUtil.isValid("xxxxxxxxxxxxxxxxxx"))
        }

        @Test
        fun `should return false for bad hex - 3`() {
            assertFalse(HexUtil.isValid("hex"))
        }
    }

    @Nested
    inner class AddPrefix {
        @Test
        fun `should add prefix`() {
            assertEquals("0x123", HexUtil.addPrefix("123"))
        }

        @Test
        fun `should not add prefix`() {
            assertEquals("0x123", HexUtil.addPrefix("0x123"))
        }

        @Test
        fun `prefix already present`() {
            assertEquals("0x123", HexUtil.addPrefix("0x123"))
        }
    }

    @Nested
    inner class Normalise {
        @Test
        fun `should normalise`() {
            assertEquals(HexUtil.normalise("ABC"), "0xabc")
            assertEquals(HexUtil.normalise("0xabc"), "0xabc")
            assertEquals(HexUtil.normalise("0xAbC"), "0xabc")
        }
    }

}