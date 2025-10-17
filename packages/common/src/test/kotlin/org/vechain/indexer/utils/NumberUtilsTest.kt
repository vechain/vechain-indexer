package org.vechain.indexer.utils

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NumberUtilsTest {
    // Test the scaleDown function
    @Test
    fun `test scaleDown for wei to ether conversion`() {
        // Test with 1 ether in wei (10^18 wei)
        val oneEtherInWei = BigInteger("1000000000000000000") // 1 ether in wei
        val expected = BigDecimal(1.0) // 1 ether in Double
        val result = scaleDown(oneEtherInWei, 18)

        assertEquals(0, expected.compareTo(result))
    }

    @Test
    fun `test scaleDown with large wei value`() {
        // Test with large amount of wei (1000 ether in wei)
        val oneThousandEtherInWei = BigInteger("1000000000000000000000") // 1000 ether in wei
        val expected = BigDecimal(1000.0) // 1000 ether in Double
        val result = scaleDown(oneThousandEtherInWei, 18)

        assertEquals(0, expected.compareTo(result))
    }

    @Test
    fun `test scaleDown with zero value`() {
        // Test with zero wei
        val zeroWei = BigInteger.ZERO
        val expected = BigDecimal(0.0) // Expect 0 ether
        val result = scaleDown(zeroWei, 18)

        assertEquals(0, expected.compareTo(result))
    }
}
