package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.IndexedContract
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
            page = 0
        )

        // 8 regular contract deployments + 2 deployments from a factory contract
        expectThat(contracts.data).hasSize(10)

        contracts.data.forEach { contract: IndexedContract ->
            assertValidContract(contract)
        }

        // Get contract by address
        val contract = VeWorldAPIClient.getContract(contracts.data[0].address)

        assertValidContract(contract)
    }

    @Test
    fun `get contracts for creator paginated`() {
        val contracts = VeWorldAPIClient.getContractForCreator("0xf077b491b355e64048ce21e3a6fc4751eeea77fa", 0, 1)

        expectThat(contracts.data).hasSize(1)

        contracts.data.forEach { contract: IndexedContract ->
            assertValidContract(contract)
        }
    }

    fun assertValidContract(contract: IndexedContract) {

        expect {
            that(contract.txId).isNotEmpty()
            that(contract.blockId).isNotEmpty()
            that(contract.blockNumber).isGreaterThan(0)
            that(contract.creator).isNotEmpty()
            that(contract.rawData).isNotEmpty()
        }
    }
}