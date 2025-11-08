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
}
