package org.vechain.indexer.validator

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.DetectedNetwork
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.scheduler.EpochSeedProvider
import org.vechain.indexer.validator.scheduler.ThorSchedulerProcess
import strikt.api.expectThat
import strikt.assertions.contains

class ValidatorServiceTest {
    private lateinit var repository: ValidatorRepository
    private lateinit var thorClient: ThorClient
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var epochSeedProvider: EpochSeedProvider
    private lateinit var thorScheduler: ThorSchedulerProcess
    private lateinit var networkDetectionService: NetworkDetectionService
    private lateinit var service: ValidatorService

    @BeforeEach
    fun setup() {
        repository = mockk()
        thorClient = mockk()
        mongoTemplate = mockk(relaxed = true)
        epochSeedProvider = mockk()
        thorScheduler = mockk()
        networkDetectionService = mockk()
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(network = VeChainNetwork.MAINNET, genesisBlock = mockk())
        service =
            ValidatorService(
                repository,
                thorClient,
                mongoTemplate,
                InlineVersioningProperties(),
                epochSeedProvider,
                thorScheduler,
                networkDetectionService,
                STAKER_ADDRESS,
                validatorStartBlock = 0L,
            )
    }

    @Test
    fun `processBlock reports empty staker inspect response with config context`() {
        every { repository.findByStatusNot(Status.WITHDRAWN) } returns emptyList()
        coEvery { epochSeedProvider.seedFor(any()) } returns null
        coEvery { thorClient.inspectClauses(any<List<Clause>>(), any()) } returns
            listOf(emptyInspectionResult())

        val exception =
            assertThrows<IllegalStateException> {
                kotlinx.coroutines.runBlocking { service.processBlock(block(), emptyList()) }
            }

        expectThat(exception.message!!)
            .contains("Built-in staker inspect call 'firstActive' returned no ABI data")
            .contains("block 23414400")
            .contains("INDEXER_START_BLOCK_VALIDATOR")
            .contains("THOR_URL")
            .contains("BUILTIN_STAKER_CONTRACT")
    }

    private fun block(): Block =
        Block(
            id = "0x" + "1".repeat(64),
            number = 23414400,
            timestamp = 1760000000,
            parentID = "0x" + "0".repeat(64),
            size = 1,
            gasLimit = 1,
            baseFeePerGas = "0x0",
            beneficiary = "0xbeneficiary",
            gasUsed = 1,
            totalScore = 1,
            txsRoot = "0x" + "2".repeat(64),
            txsFeatures = 0,
            stateRoot = "0x" + "3".repeat(64),
            receiptsRoot = "0x" + "4".repeat(64),
            com = false,
            signer = "0x0000000000000000000000000000000000000001",
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
        )

    private fun emptyInspectionResult(): InspectionResult =
        InspectionResult(
            data = "",
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
            reverted = false,
            vmError = null,
        )

    private companion object {
        const val STAKER_ADDRESS = "0x00000000000000000000000000005374616B6572"
    }
}
