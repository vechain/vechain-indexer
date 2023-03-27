package org.vechain.indexer.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.utils.AddressUtil

class AddressUtilTest {

    private val notPrefixed = "8d66DA6448c6144E894B7cD91Fa1Ae65310A4855"
    private val prefixed = "0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855"

    @Nested
    inner class IsValid {
        @Test
        fun `should return TRUE for valid address`() {
            assertTrue(AddressUtil.isValid(prefixed))
        }

        @Test
        fun `should return FALSE for address that is too short`() {
            assertFalse(AddressUtil.isValid("0x8d66DA6448"))
        }

        @Test
        fun `should return FALSE for address that is too long`() {
            assertFalse(AddressUtil.isValid("${prefixed}abc"))
        }

        @Test
        fun `should return FALSE for address that contains invalid characters`() {
            assertFalse(AddressUtil.isValid("${prefixed}GG"))
        }

        @Test
        fun `should return TRUE for address that is not prefixed with 0x`() {
            assertTrue(AddressUtil.isValid("8d66DA6448c6144E894B7cD91Fa1Ae65310A4855"))
        }

        @Test
        fun `should return TRUE for uppercase address`() {
            assertTrue(AddressUtil.isValid(notPrefixed.uppercase()))
        }

        @Test
        fun `should return TRUE for lowercase address`() {
            assertTrue(AddressUtil.isValid(prefixed.lowercase()))
        }
    }

    @Nested
    inner class IsNotValid {
        @Test
        fun `should return FALSE for invalid address`() {
            assertFalse(AddressUtil.isNotValid(prefixed))
        }

        @Test
        fun `should return FALSE for valid address`() {
            assertTrue(AddressUtil.isNotValid("0x8d66DA6448"))
        }

        @Test
        fun `should return FALSE`() {
            assertFalse(AddressUtil.isNotValid("0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"))
        }
    }


}