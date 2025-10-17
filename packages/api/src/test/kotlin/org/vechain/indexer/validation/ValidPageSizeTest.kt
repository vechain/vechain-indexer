package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.rest.PAGE_SIZE_LIMIT
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidPageSizeTest {

    private val validator = PageSizeValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts valid page sizes`() {
        expect {
            that(validator.isValid(1, DummyContext())).isTrue()
            that(validator.isValid(PAGE_SIZE_LIMIT, DummyContext())).isTrue()
            that(validator.isValid(PAGE_SIZE_LIMIT / 2, DummyContext())).isTrue()
        }
    }

    @Test
    fun `rejects invalid page sizes`() {
        expect {
            that(validator.isValid(0, DummyContext())).isFalse()
            that(validator.isValid(-1, DummyContext())).isFalse()
            that(validator.isValid(PAGE_SIZE_LIMIT + 1, DummyContext())).isFalse()
        }
    }
}
