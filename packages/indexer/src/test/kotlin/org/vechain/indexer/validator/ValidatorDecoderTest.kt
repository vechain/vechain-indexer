package org.vechain.indexer.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.domain.ValidatorDecoder

class ValidatorDecoderTest {

    private fun makeInspectionResult(data: String, reverted: Boolean = false): InspectionResult =
        InspectionResult(
            data = data,
            events = emptyList(),
            transfers = emptyList(),
            reverted = reverted,
            vmError = "",
        )

    @Test
    fun `decodeFirstQueued returns address when valid data`() {
        // ABI-encoded address: 0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1
        val data = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
        val result = ValidatorDecoder.decodeFirstQueued(makeInspectionResult(data))

        assertThat(result).isEqualTo("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1")
    }

    @Test
    fun `decodeFirstQueued returns null for zero address`() {
        val data = "0x0000000000000000000000000000000000000000000000000000000000000000"
        val result = ValidatorDecoder.decodeFirstQueued(makeInspectionResult(data))

        assertThat(result).isNull()
    }

    @Test
    fun `decodeFirstQueued returns null for empty data`() {
        val result = ValidatorDecoder.decodeFirstQueued(makeInspectionResult(""))

        assertThat(result).isNull()
    }

    @Test
    fun `decodeFirstQueued returns null for 0x data`() {
        val result = ValidatorDecoder.decodeFirstQueued(makeInspectionResult("0x"))

        assertThat(result).isNull()
    }

    // --- decodeNextQueued tests ---

    @Test
    fun `decodeNextQueued returns address when valid data`() {
        val data = "0x000000000000000000000000bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val result = ValidatorDecoder.decodeNextQueued(makeInspectionResult(data))

        assertThat(result).isEqualTo("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
    }

    @Test
    fun `decodeNextQueued returns null for zero address`() {
        val data = "0x0000000000000000000000000000000000000000000000000000000000000000"
        val result = ValidatorDecoder.decodeNextQueued(makeInspectionResult(data))

        assertThat(result).isNull()
    }

    @Test
    fun `decodeNextQueued returns null for empty data`() {
        val result = ValidatorDecoder.decodeNextQueued(makeInspectionResult(""))

        assertThat(result).isNull()
    }

    @Test
    fun `decodeNextQueued returns null for 0x data`() {
        val result = ValidatorDecoder.decodeNextQueued(makeInspectionResult("0x"))

        assertThat(result).isNull()
    }

    @Test
    fun `hasAbiData returns true for valid data`() {
        val result = makeInspectionResult("0xSOMEDATA")
        assertThat(with(ValidatorDecoder) { result.hasAbiData() }).isTrue()
    }

    @Test
    fun `hasAbiData returns false for empty string`() {
        val result = makeInspectionResult("")
        assertThat(with(ValidatorDecoder) { result.hasAbiData() }).isFalse()
    }

    @Test
    fun `hasAbiData returns false for 0x only`() {
        val result = makeInspectionResult("0x")
        assertThat(with(ValidatorDecoder) { result.hasAbiData() }).isFalse()
    }

    @Test
    fun `buildFirstQueuedClause creates clause with correct contract address`() {
        val clause = ValidatorDecoder.buildFirstQueuedClause("0xcontract123")

        assertThat(clause.to).isEqualTo("0xcontract123")
        assertThat(clause.value).isEqualTo("0x0")
        assertThat(clause.data).isNotBlank()
    }

    // --- buildNextQueuedClause tests ---

    @Test
    fun `buildNextQueuedClause creates clause with correct contract and encoded prev address`() {
        val clause =
            ValidatorDecoder.buildNextQueuedClause(
                "0xcontract123",
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
            )

        assertThat(clause.to).isEqualTo("0xcontract123")
        assertThat(clause.value).isEqualTo("0x0")
        assertThat(clause.data).isNotBlank()
        // The data should contain the encoded prev address
        assertThat(clause.data.lowercase()).contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1")
    }

    // --- ABI function definition tests ---

    @Test
    fun `firstQueuedFunction has correct structure`() {
        val fn = ValidatorDecoder.firstQueuedFunction

        assertThat(fn.name).isEqualTo("firstQueued")
        assertThat(fn.inputs).isEmpty()
        assertThat(fn.outputs).hasSize(1)
        assertThat(fn.outputs[0].name).isEqualTo("first")
        assertThat(fn.outputs[0].type).isEqualTo("address")
    }

    @Test
    fun `nextQueuedFunction has correct structure`() {
        val fn = ValidatorDecoder.nextQueuedFunction

        assertThat(fn.name).isEqualTo("next")
        assertThat(fn.inputs).hasSize(1)
        assertThat(fn.inputs[0].name).isEqualTo("prev")
        assertThat(fn.inputs[0].type).isEqualTo("address")
        assertThat(fn.outputs).hasSize(1)
        assertThat(fn.outputs[0].name).isEqualTo("nextValidation")
        assertThat(fn.outputs[0].type).isEqualTo("address")
    }
}
