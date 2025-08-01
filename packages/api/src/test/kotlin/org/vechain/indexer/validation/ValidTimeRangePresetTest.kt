package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.timeseries.TimeRangePreset
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidTimeRangePresetTest {

    private val validator = ValidTimeRangePresetValidator()

    @Test
    fun `accepts valid time range preset values`() {
        TimeRangePreset.entries.forEach { preset ->
            expect { that(validator.isValid(preset.pathValue, DummyContext())).isTrue() }
        }
    }

    @Test
    fun `rejects invalid time range preset value`() {
        expect {
            that(validator.isValid("not_a_preset", DummyContext())).isFalse()
            that(validator.isValid("", DummyContext())).isFalse()
        }
    }
}
