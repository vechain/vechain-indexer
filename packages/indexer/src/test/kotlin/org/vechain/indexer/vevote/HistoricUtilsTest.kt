package org.vechain.indexer.vevote

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.*

internal class HistoricUtilsTest {

    private val steeringCommitteeAddress = "0x7e54f0790153647ec0651c35ced28171adb5d44a"
    private val allStakeholdersAddress = "0xa6416a72f816d3a69f33d0814700545c8e3fe4be"

    @Test
    fun `extractChoices returns null for unknown contract address`() {
        val basicInfo = mapOf("options" to listOf("Yes", "No"))
        val result =
            HistoricUtils.extractChoices(
                basicInfo,
                "0xunknown",
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNull()
    }

    @Test
    fun `extractChoices returns choices for steering committee contract with list`() {
        val basicInfo = mapOf("options" to listOf("Yes", "No", "Abstain"))
        val result =
            HistoricUtils.extractChoices(
                basicInfo,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(3)
        expectThat(result[0]).isEqualTo("Yes")
        expectThat(result[1]).isEqualTo("No")
        expectThat(result[2]).isEqualTo("Abstain")
    }

    @Test
    fun `extractChoices returns choices for steering committee contract with array`() {
        val basicInfo = mapOf("options" to arrayOf("Yes", "No"))
        val result =
            HistoricUtils.extractChoices(
                basicInfo,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(2)
        expectThat(result[0]).isEqualTo("Yes")
        expectThat(result[1]).isEqualTo("No")
    }

    @Test
    fun `extractChoices returns choices for all stakeholders contract`() {
        val basicInfo = mapOf("options" to listOf("Option1", "Option2"))
        val result =
            HistoricUtils.extractChoices(
                basicInfo,
                allStakeholdersAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(2)
        expectThat(result[0]).isEqualTo("Option1")
        expectThat(result[1]).isEqualTo("Option2")
    }

    @Test
    fun `extractChoices returns null when options is not list or array`() {
        val basicInfo = mapOf("options" to "not a list")
        val result =
            HistoricUtils.extractChoices(
                basicInfo,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNull()
    }

    @Test
    fun `extractChoices returns null when basicInfo is null`() {
        val result =
            HistoricUtils.extractChoices(
                null,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNull()
    }

    @Test
    fun `extractChoices handles null values in options list`() {
        val basicInfo = mapOf("options" to listOf("Yes", null, "No"))
        val result =
            HistoricUtils.extractChoices(
                basicInfo,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(3)
        expectThat(result[0]).isEqualTo("Yes")
        expectThat(result[1]).isEqualTo("null")
        expectThat(result[2]).isEqualTo("No")
    }

    @Test
    fun `extractChoices trims null characters from steering committee options`() {
        val basicInfo = mapOf("options" to listOf("Yes\u0000", "No\u0000"))
        val result =
            HistoricUtils.extractChoices(
                basicInfo,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(2)
        expectThat(result[0]).isEqualTo("Yes")
        expectThat(result[1]).isEqualTo("No")
    }

    @Test
    fun `extractVoteTallies returns null for unknown contract address`() {
        val tally = mapOf("tally" to listOf(100L, 200L))
        val result =
            HistoricUtils.extractVoteTallies(
                tally,
                "0xunknown",
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNull()
    }

    @Test
    fun `extractVoteTallies returns tallies for steering committee contract with list`() {
        val tally = mapOf("tally" to listOf(100L, 200L, 300L))
        val result =
            HistoricUtils.extractVoteTallies(
                tally,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(3)
        expectThat(result[0]).isEqualTo(100L)
        expectThat(result[1]).isEqualTo(200L)
        expectThat(result[2]).isEqualTo(300L)
    }

    @Test
    fun `extractVoteTallies returns tallies for steering committee contract with array`() {
        val tally = mapOf("tally" to arrayOf(100L, 200L))
        val result =
            HistoricUtils.extractVoteTallies(
                tally,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(2)
        expectThat(result[0]).isEqualTo(100L)
        expectThat(result[1]).isEqualTo(200L)
    }

    @Test
    fun `extractVoteTallies returns tallies for all stakeholders contract`() {
        val tally = mapOf("tally" to listOf(150L, 250L))
        val result =
            HistoricUtils.extractVoteTallies(
                tally,
                allStakeholdersAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(2)
        expectThat(result[0]).isEqualTo(150L)
        expectThat(result[1]).isEqualTo(250L)
    }

    @Test
    fun `extractVoteTallies returns null when tally is not list or array`() {
        val tally = mapOf("tally" to "not a list")
        val result =
            HistoricUtils.extractVoteTallies(
                tally,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNull()
    }

    @Test
    fun `extractVoteTallies returns null when tally is null`() {
        val result =
            HistoricUtils.extractVoteTallies(
                null,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNull()
    }

    @Test
    fun `extractVoteTallies handles null values in tally list`() {
        val tally = mapOf("tally" to listOf(100L, null, 300L))
        val result =
            HistoricUtils.extractVoteTallies(
                tally,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(3)
        expectThat(result[0]).isEqualTo(100L)
        expectThat(result[1]).isEqualTo(0L)
        expectThat(result[2]).isEqualTo(300L)
    }

    @Test
    fun `extractVoteTallies handles non-number values in tally list`() {
        val tally = mapOf("tally" to listOf(100L, "not a number", 300L))
        val result =
            HistoricUtils.extractVoteTallies(
                tally,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isNotNull()
        expectThat(result!!).hasSize(3)
        expectThat(result[0]).isEqualTo(100L)
        expectThat(result[1]).isEqualTo(0L)
        expectThat(result[2]).isEqualTo(300L)
    }

    @Test
    fun `calculateTotalVotes returns sum of tallies for steering committee`() {
        val tally = mapOf("tally" to listOf(100L, 200L, 300L))
        val result =
            HistoricUtils.calculateTotalVotes(
                tally,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isEqualTo(600L)
    }

    @Test
    fun `calculateTotalVotes returns sum of tallies for all stakeholders`() {
        val tally = mapOf("tally" to listOf(150L, 250L))
        val result =
            HistoricUtils.calculateTotalVotes(
                tally,
                allStakeholdersAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isEqualTo(400L)
    }

    @Test
    fun `calculateTotalVotes returns zero when no tallies available`() {
        val result =
            HistoricUtils.calculateTotalVotes(
                null,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isEqualTo(0L)
    }

    @Test
    fun `calculateTotalVotes returns zero for empty tally list`() {
        val tally = mapOf("tally" to emptyList<Long>())
        val result =
            HistoricUtils.calculateTotalVotes(
                tally,
                steeringCommitteeAddress,
                steeringCommitteeAddress,
                allStakeholdersAddress,
            )

        expectThat(result).isEqualTo(0L)
    }
}
