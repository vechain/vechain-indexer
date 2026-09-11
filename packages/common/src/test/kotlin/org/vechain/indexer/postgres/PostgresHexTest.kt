package org.vechain.indexer.postgres

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Pins the column encoders to Thor's conventions so the assembled JSON matches byte for byte. */
class PostgresHexTest {

    @Test
    fun `hashes and addresses keep their width`() {
        val address = "0x000000000000000000000000000000000000dead"
        assertEquals(20, PostgresHex.bytes(address).size)
        assertEquals(address, PostgresHex.hex(PostgresHex.bytes(address)))
        val hash = "0x00" + "ab".repeat(31)
        assertEquals(hash, PostgresHex.hex(PostgresHex.bytes(hash)))
    }

    @Test
    fun `empty calldata is 0x`() {
        assertArrayEquals(ByteArray(0), PostgresHex.bytes("0x"))
        assertEquals("0x", PostgresHex.hex(ByteArray(0)))
    }

    @Test
    fun `quantities come back as minimal hex`() {
        assertEquals("0x0", PostgresHex.minimalHex(PostgresHex.quantity("0x0")))
        assertEquals("0x1", PostgresHex.minimalHex(PostgresHex.quantity("0x01")))
        assertEquals("0xde0b6b3a7640000", PostgresHex.minimalHex(BigDecimal("1000000000000000000")))
        val uint256Max = BigInteger.TWO.pow(256) - BigInteger.ONE
        assertEquals("0x" + "f".repeat(64), PostgresHex.minimalHex(BigDecimal(uint256Max)))
        assertEquals(BigDecimal(uint256Max), PostgresHex.quantity("0x" + "f".repeat(64)))
    }

    @Test
    fun `nonces are uint64 quantities`() {
        assertEquals(
            "0xffffffffffffffff",
            PostgresHex.minimalHex(PostgresHex.quantity("0xffffffffffffffff")),
        )
    }

    @Test
    fun `nullable helpers pass null through`() {
        assertNull(PostgresHex.bytesOrNull(null))
        assertNull(PostgresHex.hexOrNull(null))
        assertNull(PostgresHex.quantityOrNull(null))
        assertNull(PostgresHex.minimalHexOrNull(null))
    }
}
