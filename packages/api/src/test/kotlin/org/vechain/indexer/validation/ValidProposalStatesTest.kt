package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.b3tr.proposal.ProposalState
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidProposalStatesTest {
    private val validator = ProposalStatesValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts empty string`() {
        expect { that(validator.isValid("", DummyContext())).isTrue() }
    }

    @Test
    fun `accepts blank string`() {
        expect { that(validator.isValid("   ", DummyContext())).isTrue() }
    }

    @Test
    fun `accepts single valid proposal state`() {
        ProposalState.entries.forEach { state ->
            expect { that(validator.isValid(state.name, DummyContext())).isTrue() }
        }
    }

    @Test
    fun `accepts multiple valid proposal states (comma-separated)`() {
        expect {
            that(validator.isValid("Pending,Active", DummyContext())).isTrue()
            that(validator.isValid("Pending,Active,Succeeded", DummyContext())).isTrue()
            that(validator.isValid("Executed,DepositNotMet,Canceled,Defeated", DummyContext()))
                .isTrue()
        }
    }

    @Test
    fun `accepts multiple valid proposal states with spaces`() {
        expect {
            that(validator.isValid("Pending , Active", DummyContext())).isTrue()
            that(validator.isValid(" Pending, Active , Succeeded ", DummyContext())).isTrue()
        }
    }

    @Test
    fun `accepts case-insensitive proposal states`() {
        expect {
            that(validator.isValid("pending", DummyContext())).isTrue()
            that(validator.isValid("ACTIVE", DummyContext())).isTrue()
            that(validator.isValid("PeNdInG,AcTiVe", DummyContext())).isTrue()
        }
    }

    @Test
    fun `rejects invalid proposal state`() {
        expect { that(validator.isValid("InvalidState", DummyContext())).isFalse() }
    }

    @Test
    fun `rejects list with one invalid state among valid ones`() {
        expect { that(validator.isValid("Pending,InvalidState,Active", DummyContext())).isFalse() }
    }

    @Test
    fun `rejects empty state in comma-separated list`() {
        expect { that(validator.isValid("Pending,,Active", DummyContext())).isFalse() }
    }
}
