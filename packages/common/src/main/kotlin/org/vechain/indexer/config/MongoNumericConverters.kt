package org.vechain.indexer.config

import java.math.BigDecimal
import java.math.BigInteger
import org.bson.types.Decimal128
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@ReadingConverter
class StringToDecimal128Converter : Converter<String, Decimal128> {
    override fun convert(source: String): Decimal128 =
        try {
            Decimal128(BigDecimal(source))
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Cannot convert String [$source] to Decimal128: malformed numeric value",
                e,
            )
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException(
                "Cannot convert String [$source] to Decimal128: value out of range",
                e,
            )
        }
}

@ReadingConverter
class StringToBigIntegerConverter : Converter<String, BigInteger> {
    override fun convert(source: String): BigInteger =
        try {
            BigInteger(source)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Cannot convert String [$source] to BigInteger: malformed numeric value",
                e,
            )
        }
}

@ReadingConverter
class Decimal128ToBigIntegerConverter : Converter<Decimal128, BigInteger> {
    override fun convert(source: Decimal128): BigInteger =
        try {
            source.bigDecimalValue().toBigIntegerExact()
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException(
                "Cannot convert Decimal128 [$source] to BigInteger without losing precision",
                e,
            )
        }
}

@WritingConverter
class BigIntegerToDecimal128Converter : Converter<BigInteger, Decimal128> {
    override fun convert(source: BigInteger): Decimal128 =
        try {
            Decimal128(BigDecimal(source))
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException(
                "Cannot convert BigInteger [$source] to Decimal128: value out of range",
                e,
            )
        }
}
