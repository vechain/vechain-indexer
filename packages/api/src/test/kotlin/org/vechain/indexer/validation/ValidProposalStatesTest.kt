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
    fun `accepts empty list`() {
        expect { that(validator.isValid(emptyList(), DummyContext())).isTrue() }
    }

    @Test
    fun `accepts single valid proposal state`() {
        ProposalState.entries.forEach { state ->
            expect { that(validator.isValid(listOf(state.name), DummyContext())).isTrue() }
        }
    }

    @Test
    fun `accepts multiple valid proposal states`() {
        expect {
            that(validator.isValid(listOf("Pending", "Active"), DummyContext())).isTrue()
            that(validator.isValid(listOf("Pending", "Active", "Succeeded"), DummyContext()))
                .isTrue()
            that(
                    validator.isValid(
                        listOf("Executed", "DepositNotMet", "Canceled", "Defeated"),
                        DummyContext(),
                    )
                )
                .isTrue()
        }
    }

    @Test
    fun `accepts multiple valid proposal states with spaces`() {
        expect {
            that(validator.isValid(listOf("Pending ", " Active"), DummyContext())).isTrue()
            that(validator.isValid(listOf(" Pending", " Active ", " Succeeded "), DummyContext()))
                .isTrue()
        }
    }

    @Test
    fun `accepts case-insensitive proposal states`() {
        expect {
            that(validator.isValid(listOf("pending"), DummyContext())).isTrue()
            that(validator.isValid(listOf("ACTIVE"), DummyContext())).isTrue()
            that(validator.isValid(listOf("PeNdInG", "AcTiVe"), DummyContext())).isTrue()
        }
    }

    @Test
    fun `rejects invalid proposal state`() {
        expect { that(validator.isValid(listOf("InvalidState"), DummyContext())).isFalse() }
    }

    @Test
    fun `rejects list with one invalid state among valid ones`() {
        expect {
            that(validator.isValid(listOf("Pending", "InvalidState", "Active"), DummyContext()))
                .isFalse()
        }
    }

    @Test
    fun `rejects empty string in list`() {
        expect {
            that(validator.isValid(listOf("Pending", "", "Active"), DummyContext())).isFalse()
        }
    }
}
