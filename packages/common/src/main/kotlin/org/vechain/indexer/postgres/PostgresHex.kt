package org.vechain.indexer.postgres

import java.math.BigDecimal
import java.math.BigInteger
import org.apache.commons.codec.binary.Hex

/** Thor hex to columns: fixed-width values as BYTEA, quantities as NUMERIC, back as minimal hex. */
object PostgresHex {
    private const val PREFIX = "0x"

    fun bytes(hex: String): ByteArray = Hex.decodeHex(hex.removePrefix(PREFIX))

    fun bytesOrNull(hex: String?): ByteArray? = hex?.let(::bytes)

    fun hex(bytes: ByteArray): String = PREFIX + Hex.encodeHexString(bytes)

    fun hexOrNull(bytes: ByteArray?): String? = bytes?.let(::hex)

    /** sha1 ids are the one hex the API renders without `0x`; [bytes] accepts either form. */
    fun bareHex(bytes: ByteArray): String = Hex.encodeHexString(bytes)

    fun quantity(hex: String): BigDecimal = BigDecimal(BigInteger(hex.removePrefix(PREFIX), 16))

    fun quantityOrNull(hex: String?): BigDecimal? = hex?.let(::quantity)

    fun minimalHex(quantity: BigDecimal): String =
        PREFIX + quantity.toBigIntegerExact().toString(16)

    fun minimalHexOrNull(quantity: BigDecimal?): String? = quantity?.let(::minimalHex)
}
