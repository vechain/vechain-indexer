package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.b3tr.navigator.NavigatorStatus
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidNavigatorStatusTest {
    private val validator = NavigatorStatusValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts empty list`() {
        expect { that(validator.isValid(emptyList(), DummyContext())).isTrue() }
    }

    @Test
    fun `accepts single valid status`() {
        NavigatorStatus.entries.forEach { status ->
            expect { that(validator.isValid(listOf(status.name), DummyContext())).isTrue() }
        }
    }

    @Test
    fun `accepts case-insensitive statuses with spaces`() {
        expect {
            that(validator.isValid(listOf("active"), DummyContext())).isTrue()
            that(validator.isValid(listOf(" EXITING "), DummyContext())).isTrue()
            that(validator.isValid(listOf("deactivated", " Active "), DummyContext())).isTrue()
        }
    }

    @Test
    fun `rejects invalid status`() {
        expect { that(validator.isValid(listOf("INVALID"), DummyContext())).isFalse() }
    }

    @Test
    fun `rejects list with one invalid status among valid ones`() {
        expect {
            that(validator.isValid(listOf("ACTIVE", "INVALID", "EXITING"), DummyContext()))
                .isFalse()
        }
    }

    @Test
    fun `rejects empty string in list`() {
        expect {
            that(validator.isValid(listOf("ACTIVE", "", "EXITING"), DummyContext())).isFalse()
        }
    }
}
