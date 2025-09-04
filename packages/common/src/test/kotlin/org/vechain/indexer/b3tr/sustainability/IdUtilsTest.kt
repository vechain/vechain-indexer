package org.vechain.indexer.b3tr.sustainability

import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IdUtilsTest {
    @Test
    fun `test generateId with single input`() {
        val input = "testInput"
        val expectedHash = DigestUtils.sha1Hex(input)
        val result = IdUtils.generateId(input)

        assertEquals(expectedHash, result)
    }

    @Test
    fun `test generateId with multiple inputs`() {
        val inputs = arrayOf("input1", "input2", "input3")
        val expectedHash = DigestUtils.sha1Hex("input1-input2-input3")
        val result = IdUtils.generateId(*inputs)
        assertEquals(expectedHash, result)
    }
}
