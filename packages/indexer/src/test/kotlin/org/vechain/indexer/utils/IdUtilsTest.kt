package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IdUtilsTest {
    @Test
    fun `generateId with single input returns correct hash`() {
        val input = "testInput"
        val expectedHash = DigestUtils.sha1Hex(input)
        val result = IdUtils.generateId(input)

        assertEquals(expectedHash, result)
    }

    @Test
    fun `generateId with multiple inputs joins with dash and returns hash`() {
        val inputs = arrayOf("input1", "input2", "input3")
        val expectedHash = DigestUtils.sha1Hex("input1-input2-input3")
        val result = IdUtils.generateId(*inputs)
        assertEquals(expectedHash, result)
    }

    @Test
    fun `generateId with empty varargs returns hash of empty string`() {
        val expectedHash = DigestUtils.sha1Hex("")
        val result = IdUtils.generateId()

        assertEquals(expectedHash, result)
    }

    @Test
    fun `generateId creates consistent hashes for same input`() {
        val input1 = IdUtils.generateId("a", "b", "c")
        val input2 = IdUtils.generateId("a", "b", "c")

        // Same inputs should produce same hash
        assertEquals(input1, input2)

        // Hash should match sha1 of joined string
        val expectedHash = DigestUtils.sha1Hex("a-b-c")
        assertEquals(expectedHash, input1)
    }

    @Test
    fun `generateId with trailing empty string produces different hash than without`() {
        // Simulate accidental trailing comma by passing empty string
        val withTrailingEmpty = IdUtils.generateId("a", "b", "c", "")
        val withoutTrailing = IdUtils.generateId("a", "b", "c")

        // These should produce DIFFERENT hashes because the joined strings differ
        // "a-b-c-" vs "a-b-c"
        assertNotEquals(withTrailingEmpty, withoutTrailing)

        // Verify the actual hashes
        assertEquals(DigestUtils.sha1Hex("a-b-c-"), withTrailingEmpty)
        assertEquals(DigestUtils.sha1Hex("a-b-c"), withoutTrailing)
    }

    @Test
    fun `generateId with empty strings in middle produces correct hash`() {
        // Empty strings in the middle should still be joined with dashes
        val result = IdUtils.generateId("a", "", "c")

        // Should produce "a--c" when joined
        val expectedHash = DigestUtils.sha1Hex("a--c")
        assertEquals(expectedHash, result)
    }

    @Test
    fun `generateId with only empty strings produces hash of dashes`() {
        // Multiple empty strings should produce multiple dashes
        val result = IdUtils.generateId("", "", "")

        // Should produce "--" when joined
        val expectedHash = DigestUtils.sha1Hex("--")
        assertEquals(expectedHash, result)
    }
}
