package org.vechain.indexer

import com.google.gson.Gson
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ThorService
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*

@ExtendWith(MockKExtension::class)
internal class ContractIndexerTest {

    @MockK
    lateinit var thorService: ThorService

    @MockK
    lateinit var contractRepo: ContractRepo

    @InjectMockKs
    lateinit var contractIndexer: ContractIndexer

    // Block #5 -> block_5_erc20_vip180_contracts.json
    // This block contains 2 ERC20/VIP180 contract deployment transactions
    @Test
    fun `Extract erc20 vip180 contract types`() {
        // Known fixture
        val blockNumber = 5L
        val block5: Block = buildBlockFixture(blockNumber)

        // Mock data returned for block#5: block & account code
        every { thorService.getBlock(blockNumber) } returns block5
        every { thorService.getAccountCode(any()) } returns getContractData(
            block5,
            "0x75c96bf8661b665d3053ab9dcc1b1241d6e4e6750c355b14009d88e607add34a"
        )

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()


        // Process block for contract indexing
        contractIndexer.processBlock(blockNumber)


        val contracts = contractsSlot.captured
        expect {
            that(contracts).hasSize(2)
            that(contracts).map(Contract::isErc20).all { isEqualTo(true) }
            that(contracts).map(Contract::isVip180).all { isEqualTo(true) }
            that(contracts).map(Contract::isErc721).all { isEqualTo(false) }
            that(contracts).map(Contract::isVip181).all { isEqualTo(false) }
        }
    }

    // Block #6 -> block_6_erc721_vip181_contracts.json
    // This block contains 1 ERC20/VIP180 contract deployment transaction
    @Test
    fun `Extract erc721 vip181 contract types`() {
        // Known fixture
        val blockNumber = 6L
        val block6: Block = buildBlockFixture(blockNumber)

        // Mock data returned for block#6: block & account code
        every { thorService.getBlock(blockNumber) } returns block6
        every { thorService.getAccountCode(any()) } returns getContractData(
            block6,
            "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0"
        )

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()


        // Process block for contract indexing
        contractIndexer.processBlock(blockNumber)


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
        val block6: Block = buildBlockFixture(blockNumber)
        val txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0"
        val contractData = getContractData(block6, txId)

        // Mock data returned for block#6: block & account code
        every { thorService.getBlock(blockNumber) } returns block6
        every { thorService.getAccountCode(any()) } returns contractData

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()


        contractIndexer.processBlock(blockNumber)


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
        // Known fixture
        val blockNumber = 16L
        val block16: Block = buildBlockFixture(blockNumber)
        val existingContract = buildContract()

        // Mock data returned for block#16: block, null account code & existing mongo document
        every { thorService.getBlock(blockNumber) } returns block16
        every { thorService.getAccountCode(any()) } returns null
        every { contractRepo.findById(any()) } returns Optional.of(existingContract)

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<Contract>>()
        every { contractRepo.saveAll(capture(contractsSlot)) } returns mutableListOf()

        val oldMaster = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
        expectThat(existingContract.master).isEqualTo(oldMaster)
        contractIndexer.processBlock(blockNumber)

        val contracts = contractsSlot.captured
        val newMaster = "0xa077d962dfa446661d63c97f68d9628f908a5f43"
        expect {
            that(contracts).hasSize(2)
            that(contracts).map(Contract::master).all { isEqualTo(newMaster) }
        }
    }

    private fun buildBlockFixture(blockNumber: Long): Block {
        return Gson().fromJson(readBlockFixture(blockNumber), Block::class.java)
    }

    private fun readBlockFixture(blockNumber: Long): String {
        val jsonFile =
            when (blockNumber) {
                5L -> "/fixtures/block_5_erc20_vip180_contracts.json"
                6L -> "/fixtures/block_6_erc721_vip181_contracts.json"
                16L -> "/fixtures/block_16_master_event_update.json"
                else -> ""
            }
        return ContractIndexerTest::class.java.getResource(jsonFile)!!.readText()
    }

    private fun getContractData(block: Block, txId: String): String {
        return block.transactions.first { it.id == txId }.clauses.first().data!!
    }

    private fun buildContract() = Contract(
        address = "0x1f734d58eb6a349f038c28f112478bf90981c87e",
        blockId = "0x000000067d3b4b3bbefc6efdf463ee8932c52ba6358f675e43ab1e7036678f4e",
        blockNumber = 6L,
        txId = "0xfc1d2a1a32823418bf24f4b1da56fe5b0f6b60707863a443e9779f19e18894b0",
        creator = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        master = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
        rawData = "rawData",
        isVip180 = false,
        isVip181 = true,
        isErc20 = false,
        isErc721 = true,
    )

}