package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.stargate.TokenLevel
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidTokenLevelTest {
    private val validator = TokenLevelValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts valid token level values (case-insensitive)`() {
        TokenLevel.entries.forEach { level ->
            if (level !== TokenLevel.All) {
                expect {
                    that(validator.isValid(level.name.lowercase(), DummyContext())).isTrue()
                    that(validator.isValid(level.name.uppercase(), DummyContext())).isTrue()
                }
            }
        }
    }

    @Test
    fun `All is not accepted`() {
        expect { that(validator.isValid(TokenLevel.All.name, DummyContext())).isFalse() }
    }

    @Test
    fun `rejects invalid token level value`() {
        expect { that(validator.isValid("not_a_level", DummyContext())).isFalse() }
    }
}
