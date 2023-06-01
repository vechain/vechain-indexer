package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.io.Resource
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_16_MASTER_EVENT_UPDATE
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_42_ERC1155_VIP210_CONTRACTS
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_5_VIP180_CONTRACTS
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_6_VIP181_CONTRACTS
import org.vechain.indexer.fixtures.ContractFixtures.CONTRACT_WITH_CREATOR_SAME_AS_MASTER
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block
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

    @MockK
    lateinit var mongoTemplate: MongoTemplate

    @MockK
    lateinit var contractsResource: Resource

    lateinit var contractService: ContractService

    private lateinit var contractIndexer: ContractIndexer


    @BeforeEach
    fun setUp() {
        every { thorService.executeReadOnlyCode(any()) } returns emptyList()
        every { thorService.getBlock(0) } returns BlockFixtures.BLOCK_0_GENESIS
        contractService = ContractService(thorService)
        MockKAnnotations.init(this)
        contractIndexer = ContractIndexer(thorService, contractService, contractRepo, mongoTemplate, contractsResource)
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
        every { contractRepo.findById(any()) } returns Optional.empty()

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<IndexedContract>>()
        every { mongoTemplate.insert(capture(contractsSlot), IndexedContract::class.java) } returns mutableListOf()


        // Process block for contract indexing
        contractIndexer.processBlock(BLOCK_5_VIP180_CONTRACTS)


        val contracts = contractsSlot.captured
        expect {
            that(contracts).hasSize(2)
            that(contracts).map(IndexedContract::isErc20).all { isTrue() }
            that(contracts).map(IndexedContract::isVip180).all { isTrue() }
            that(contracts).map(IndexedContract::isErc721).all { isFalse() }
            that(contracts).map(IndexedContract::isVip181).all { isFalse() }
        }
    }


    // Block #42 -> block_42.json
    // This block contains 1 ERC1155 and 1 VIP210 contract deployment transaction
    // The contracts do not implement each other's interfaces
    @Test
    fun `should capture erc1155 and vip210 contract deployments`() {

        //Mock contract responses
        every { thorService.getAccountCode("0xfab1f71b7e37157416935ad591eb34169a8e2db3") } returns getContractData(
            BLOCK_42_ERC1155_VIP210_CONTRACTS,
            "0x3044907ea7443d2f795aca473eb641b8355ef554cffed760f4629ffdd7847fe7"
        )
        every { thorService.getAccountCode("0x5024d193c8ec0ee084995de603365c3560d7ba6e") } returns getContractData(
            BLOCK_42_ERC1155_VIP210_CONTRACTS,
            "0x1155ffe079b8060410cbdc66028664a592f5d3cfb6a20fcc4deb564ac42c8448"
        )
        every { contractRepo.findById(any()) } returns Optional.empty()

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<IndexedContract>>()
        every { mongoTemplate.insert(capture(contractsSlot), IndexedContract::class.java) } returns mutableListOf()

        // Process block for contract indexing
        contractIndexer.processBlock(BLOCK_42_ERC1155_VIP210_CONTRACTS)

        val contracts = contractsSlot.captured

        val vip210: IndexedContract? = contracts.find { it.isVip210 && !it.isErc1155 }
        val erc1155: IndexedContract? = contracts.find { it.isErc1155 && !it.isVip210 }

        expect {
            that(vip210).isNotNull()
            that(erc1155).isNotNull()
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
        every { contractRepo.findById(any()) } returns Optional.empty()

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<IndexedContract>>()
        every { mongoTemplate.insert(capture(contractsSlot), IndexedContract::class.java) } returns mutableListOf()

        // Process block for contract indexing
        contractIndexer.processBlock(BLOCK_6_VIP181_CONTRACTS)

        val contracts = contractsSlot.captured
        expect {
            that(contracts).hasSize(1)
        }
        val contract = contracts.first()
        expect {
            that(contract).get(IndexedContract::isErc721).isTrue()
            that(contract).get(IndexedContract::isVip181).isTrue()
            that(contract).get(IndexedContract::isErc20).isFalse()
            that(contract).get(IndexedContract::isVip180).isFalse()
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
        every { contractRepo.findById(any()) } returns Optional.empty()

        // Capture entities saved upon the block processing
        val contractsSlot = slot<List<IndexedContract>>()
        every { mongoTemplate.insert(capture(contractsSlot), IndexedContract::class.java) } returns mutableListOf()

        contractIndexer.processBlock(BLOCK_6_VIP181_CONTRACTS)

        val contracts = contractsSlot.captured
        expect {
            that(contracts).all { isA<IndexedContract>() }.hasSize(1)
        }
        val contract = contracts.first()
        expect {
            that(contract).get(IndexedContract::address).isEqualTo("0x1f734d58eb6a349f038c28f112478bf90981c87e")
            that(contract).get(IndexedContract::blockId)
                .isEqualTo("0x000000067d3b4b3bbefc6efdf463ee8932c52ba6358f675e43ab1e7036678f4e")
            that(contract).get(IndexedContract::blockNumber).isEqualTo(blockNumber)
            that(contract).get(IndexedContract::blockTimestamp).isEqualTo(1680177334)
            that(contract).get(IndexedContract::txId).isEqualTo(txId)
            that(contract).get(IndexedContract::creator).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(contract).get(IndexedContract::master).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(contract).get(IndexedContract::rawData).isEqualTo(contractData)
        }
    }

    @Test
    fun `Update contract master when contract already indexed`() {

        // Mock data returned for block#16: block, any account code & existing mongo document
        every { thorService.getAccountCode(any()) } returns "any account code"
        every { contractRepo.findById(any()) } returns Optional.of(CONTRACT_WITH_CREATOR_SAME_AS_MASTER)

        // Capture entities saved upon the block processing
        val updatedContractSlot = slot<IndexedContract>()
        every { contractRepo.save(capture(updatedContractSlot)) } returnsArgument 0

        val oldMaster = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
        expectThat(CONTRACT_WITH_CREATOR_SAME_AS_MASTER.master).isEqualTo(oldMaster)
        contractIndexer.processBlock(BLOCK_16_MASTER_EVENT_UPDATE)

        val updatedContract = updatedContractSlot.captured
        val newMaster = "0xa077d962dfa446661d63c97f68d9628f908a5f43"

        expectThat(updatedContract.master).isEqualTo(newMaster)

        // No inserts should be called here, as this block contains contract master updates only
        verify { mongoTemplate wasNot Called }
    }

    private fun getContractData(block: Block, txId: String): String {
        return block.transactions.first { it.id == txId }.clauses.first().data
    }

}
