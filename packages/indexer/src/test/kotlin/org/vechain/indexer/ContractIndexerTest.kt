package org.vechain.indexer

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_16_MASTER_EVENT_UPDATE
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_5_VIP180_CONTRACTS
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_6_VIP181_CONTRACTS
import org.vechain.indexer.fixtures.ContractFixtures.CONTRACT_WITH_CREATOR_SAME_AS_MASTER
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.ThorService
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*

internal class ContractIndexerTest {

    private val thorService: ThorService = mockk()
    private var contractRepo: ContractRepo = mockk()
    private val contractService: ContractService = ContractService(thorService)

    //Using constructor invocation because MockK has problems with @SpyK + @InjectMocks
    private val contractIndexer: ContractIndexer = ContractIndexer(thorService, contractService, contractRepo)

    init {
        every { thorService.executeReadOnlyCode(any()) } returns emptyList()
    }

    // Block #5 -> block_5.json
    // This block contains 2 ERC20/VIP180 contract deployment transactions
    @Test
    fun `Extract erc20 vip180 contract types`() {

        // Mock data returned for block#5: block & account code
        every { thorService.getAccountCode(any()) } returns getContractData(
            BLOCK_5_VIP180_CONTRACTS,
            "0x75c96bf8661b665d3053ab9dcc1b1241d6e4e6750c355b14009d88e607add34a"
        )

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()


        // Process block for contract indexing
        contractIndexer.processBlock(BLOCK_5_VIP180_CONTRACTS)


        val contracts = contractsSlot.captured
        expect {
            that(contracts).hasSize(2)
            that(contracts).map(Contract::isErc20).all { isTrue() }
            that(contracts).map(Contract::isVip180).all { isTrue() }
            that(contracts).map(Contract::isErc721).all { isFalse() }
            that(contracts).map(Contract::isVip181).all { isFalse() }
        }
    }

    // Block #6 -> block_6.json
    // This block contains 1 ERC20/VIP180 contract deployment transaction
    @Test
    fun `Extract erc721 vip181 contract types`() {

        // Mock data returned for block#6: block & account code
        every { thorService.getAccountCode(any()) } returns getContractData(
            BLOCK_6_VIP181_CONTRACTS,
            "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0"
        )

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()


        // Process block for contract indexing
        contractIndexer.processBlock(BLOCK_6_VIP181_CONTRACTS)


        val contracts = contractsSlot.captured
        expect {
            that(contracts).hasSize(1)
        }
        val contract = contracts.first()
        expect {
            that(contract).get(Contract::isErc721).isTrue()
            that(contract).get(Contract::isVip181).isTrue()
            that(contract).get(Contract::isErc20).isFalse()
            that(contract).get(Contract::isVip180).isFalse()
        }
    }

    @Test
    fun `Save contract document with correct data`() {
        // Known fixture
        val blockNumber = 6L
        val txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0"
        val contractData = getContractData(BLOCK_6_VIP181_CONTRACTS, txId)

        // Mock data returned for block#6: block & account code
        every { thorService.getAccountCode(any()) } returns contractData

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()

        contractIndexer.processBlock(BLOCK_6_VIP181_CONTRACTS)

        val contracts = contractsSlot.captured
        expect {
            that(contracts).all { isA<Contract>() }.hasSize(1)
        }
        val contract = contracts.first()
        expect {
            that(contract).get(Contract::address).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(contract).get(Contract::blockId)
                .isEqualTo("0x000000067d3b4b3bbefc6efdf463ee8932c52ba6358f675e43ab1e7036678f4e")
            that(contract).get(Contract::blockNumber).isEqualTo(blockNumber)
            that(contract).get(Contract::txId).isEqualTo(txId)
            that(contract).get(Contract::creator).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(contract).get(Contract::master).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(contract).get(Contract::rawData).isEqualTo(contractData)
        }
    }

    @Test
    fun `Update contract master when no contract data`() {

        // Mock data returned for block#16: block, null account code & existing mongo document
        every { thorService.getAccountCode(any()) } returns "0x"
        every { contractRepo.findById(any()) } returns Optional.of(CONTRACT_WITH_CREATOR_SAME_AS_MASTER)

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()

        val oldMaster = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
        expectThat(CONTRACT_WITH_CREATOR_SAME_AS_MASTER.master).isEqualTo(oldMaster)
        contractIndexer.processBlock(BLOCK_16_MASTER_EVENT_UPDATE)

        val contracts = contractsSlot.captured
        val newMaster = "0xa077d962dfa446661d63c97f68d9628f908a5f43"
        expect {
            that(contracts).hasSize(2)
            that(contracts).map(Contract::master).all { isEqualTo(newMaster) }
        }
    }

    private fun getContractData(block: Block, txId: String): String {
        return block.transactions.first { it.id == txId }.clauses.first().data
    }

}