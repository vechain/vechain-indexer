package org.vechain.indexer.validator

import io.mockk.*
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Transaction
import org.vechain.indexer.validator.models.DecodedValidatorInfo

@ExtendWith(MockKExtension::class)
class ValidatorBlockServiceTest {
    private lateinit var repository: ValidatorBlockRepository
    private lateinit var thorService: ThorService
    private lateinit var service: ValidatorBlockService

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        thorService = mockk(relaxed = true)
        service = spyk(ValidatorBlockService(repository, thorService))

        every { repository.findLatestHourly() } returns emptyList()
        every { repository.findLatestDaily() } returns emptyList()
        every { repository.findLatestWeekly() } returns emptyList()
        every { repository.findLatestMonthly() } returns emptyList()
    }

    private fun buildDecoded(): Map<String, Any?> =
        mapOf(
            "masters" to listOf("0xVAL1", "0xVAL2"),
            "endorsors" to listOf("0xEND1"),
            "statuses" to listOf(BigInteger.TWO, BigInteger.TWO),
            "onlines" to listOf(true),
            "offlineBlocks" to listOf(BigInteger.ZERO),
            "stakingPeriodLengths" to listOf(10),
            "startBlocks" to listOf(BigInteger.TEN),
            "exitBlocks" to listOf(BigInteger.valueOf(4294967295)),
            "completedPeriods" to listOf(BigInteger.valueOf(5)),
            "validatorLockedStakes" to listOf(BigInteger("1000000000000000000")), // 1 VET
            "validatorLockedWeights" to listOf(BigInteger.valueOf(100)),
            "delegatorsStake" to
                listOf(BigInteger("500000000000000000"), BigInteger.ZERO), // 0.5 VET
            "totalQueuedStakes" to listOf(BigInteger.ZERO),
            "totalExitingStakes" to listOf(BigInteger.ZERO),
            "validatorQueuedStakes" to listOf(BigInteger.ZERO),
            "totalNextPeriodWeights" to listOf(BigInteger.valueOf(100)),
            "nextPeriodDelegationStakes" to listOf(BigInteger.ZERO),
        )

    private fun createBlock(
        num: Long = 100,
        signer: String = "0xvalidator",
        ts: Long = 12345,
        txs: List<Transaction> = emptyList(),
    ): Block =
        Block(
            id = "block-$num",
            number = num,
            timestamp = ts,
            parentID = "block-${num - 1}",
            size = 1,
            gasLimit = 1,
            baseFeePerGas = "0x",
            beneficiary = "beneficiary",
            gasUsed = 1,
            totalScore = 1,
            txsRoot = "txsRoot",
            txsFeatures = 0,
            stateRoot = "stateRoot",
            receiptsRoot = "receiptsRoot",
            com = false,
            signer = signer,
            isTrunk = true,
            isFinalized = true,
            transactions = txs,
        )

    private fun createDecoded(supply: Long, burned: Long): DecodedValidatorInfo =
        DecodedValidatorInfo(
            decodedValidators = buildDecoded(),
            vthoTotalSupply = BigInteger.valueOf(supply),
            vthoBurned = BigInteger.valueOf(burned),
            totalWeight = BigInteger.ZERO,
            vetPriceUsd = BigInteger.ZERO,
            vthoPriceUsd = BigInteger.ZERO,
        )

    @Test
    fun `getValidationInfo calculates rewards correctly`() {
        val block =
            createBlock(
                num = 100,
                signer = "0xVAL1",
                txs =
                    listOf(
                        Transaction(
                            id = "0x1",
                            reward = "0x10", // hex 16
                            chainTag = 1,
                            blockRef = "0x00",
                            expiration = 720,
                            clauses = emptyList(),
                            gasPriceCoef = 0,
                            gas = 21000,
                            maxFeePerGas = "0x0",
                            maxPriorityFeePerGas = "0x0",
                            origin = "0x0",
                            delegator = null,
                            nonce = "0x1",
                            dependsOn = null,
                            size = 100,
                            gasUsed = 21000,
                            gasPayer = "0x0",
                            paid = "0x0",
                            outputs = emptyList(),
                            reverted = false,
                            type = 1,
                        ),
                        Transaction(
                            id = "0x2",
                            reward = "0x05", // hex 5
                            chainTag = 1,
                            blockRef = "0x00",
                            expiration = 720,
                            clauses = emptyList(),
                            gasPriceCoef = 0,
                            gas = 21000,
                            maxFeePerGas = "0x0",
                            maxPriorityFeePerGas = "0x0",
                            origin = "0x0",
                            delegator = null,
                            nonce = "0x2",
                            dependsOn = null,
                            size = 100,
                            gasUsed = 21000,
                            gasPayer = "0x0",
                            paid = "0x0",
                            reverted = false,
                            outputs = emptyList(),
                            type = 1,
                        ),
                    ),
            )

        val decodedInfo = createDecoded(1000, 100) // supply=1000, burned=100

        // simulate initial cache
        every { service.getTotalVTHOIssuedAtBlock("block-99") } returns BigInteger.valueOf(900)

        val result = service.getValidationInfo(block, decodedInfo)!!

        assertEquals("100-0xVAL1", result.id)
        assertEquals(BigInteger.valueOf(100), result.blockReward) // (1000+100) - 900
        assertEquals(BigInteger.valueOf(21), result.priorityReward) // 16+5
        assertEquals(BigInteger.valueOf(121), result.total)
        assertEquals(BlockStatus.VALIDATED, result.status)
    }

    @Test
    fun `getValidationInfo computes delta correctly across multiple blocks`() {
        // First block initializes cache
        val block1 = createBlock(num = 101, signer = "0xVAL1")
        val info1 = createDecoded(1000, 0)

        every { service.getTotalVTHOIssuedAtBlock("block-100") } returns BigInteger.valueOf(900)
        val res1 = service.getValidationInfo(block1, info1)!!
        assertEquals(BigInteger.valueOf(100), res1.blockReward)

        // Second block uses updated cache
        val block2 = createBlock(num = 102, signer = "0xVAL2")
        val info2 = createDecoded(1200, 0)

        val res2 = service.getValidationInfo(block2, info2)!!
        assertEquals(BigInteger.valueOf(200), res2.blockReward) // 1200 - 1000
    }

    @Test
    fun `getValidatorsWithMissedSlots returns missed validators`() {
        val block = createBlock(num = 50, signer = "0xvalidator")

        val decodedInfo =
            DecodedValidatorInfo(
                decodedValidators =
                    mapOf(
                        "masters" to listOf("0xA", "0xB"),
                        "onlines" to listOf(false, true),
                        "offlineBlocks" to listOf(BigInteger.valueOf(50), BigInteger.ZERO),
                        "statuses" to listOf(BigInteger.TWO, BigInteger.ONE),
                    ),
                vthoTotalSupply = BigInteger.ZERO,
                vthoBurned = BigInteger.ZERO,
                totalWeight = BigInteger.ZERO,
                vetPriceUsd = BigInteger.ZERO,
                vthoPriceUsd = BigInteger.ZERO,
            )

        val result = service.getValidatorsWithMissedSlots(decodedInfo, block)
        assertEquals(1, result.size)
        assertEquals("0xA", result[0].validator)
        assertEquals(BlockStatus.MISSED, result[0].status)
    }

    @Test
    fun `getTotalVTHOIssued uses decoded info if available`() {
        val decodedInfo = createDecoded(500, 100)
        val total = service.getTotalVTHOIssued(decodedInfo, "block-1")
        assertEquals(BigInteger.valueOf(500), total)
    }

    @Test
    fun `save persists records and updates caches`() {
        val block =
            ValidatorBlock(
                id = "100-0xvalidator",
                blockId = "block-100",
                blockNumber = 100,
                blockTimestamp = 12345L,
                validator = "0xvalidator",
                blockReward = BigInteger.ONE,
                priorityReward = BigInteger.TWO,
                total = BigInteger.TEN,
                status = BlockStatus.VALIDATED,
                isHourly = true,
            )

        service.save(listOf(block))

        verify { repository.saveAll(listOf(block)) }
        val hourlyCache =
            service.javaClass
                .getDeclaredField("hourlyCache")
                .apply { isAccessible = true }
                .get(service) as ConcurrentHashMap<*, *>
        assertEquals(12345L, hourlyCache["0xvalidator"])
    }
}
