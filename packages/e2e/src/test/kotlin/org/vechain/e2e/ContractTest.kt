package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.Contract
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty

class ContractTest {

    @Test
    fun `get contracts for creator`() {
        val contracts = VeWorldAPIClient.getContractForCreator(
            address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            page = 0,
            size = Int.MAX_VALUE,
        )

        expectThat(contracts).hasSize(8)

        contracts.forEach { contract ->
            assertValidContract(contract)
        }

        // Get contract by address
        val contract = VeWorldAPIClient.getContract(contracts[0].address)

        assertValidContract(contract)
    }

    @Test
    fun `get contracts for creator paginated`() {
        val contracts = VeWorldAPIClient.getContractForCreator("0xf077b491b355e64048ce21e3a6fc4751eeea77fa", 0, 1)

        expectThat(contracts).hasSize(1)

        contracts.forEach { contract ->
            assertValidContract(contract)
        }
    }

    fun assertValidContract(contract: Contract) {

        expect {
            that(contract.txId).isNotEmpty()
            that(contract.blockId).isNotEmpty()
            that(contract.blockNumber).isGreaterThan(0)
            that(contract.creator).isNotEmpty()
            that(contract.rawData).isNotEmpty()
        }
    }
}