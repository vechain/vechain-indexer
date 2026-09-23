package org.vechain.indexer.validator

import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
class ValidatorBlockServiceTest {
    private lateinit var repository: ValidatorBlockWriteRepository
    private lateinit var thorClient: ThorClient
    private lateinit var service: ValidatorBlockService

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        every { repository.latestSampled(any()) } returns emptyMap()
        thorClient = mockk(relaxed = true)
        service =
            spyk(
                ValidatorBlockService(
                    repository,
                    thorClient,
                    validatorStartBlock = 0L,
                )
            )
    }

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

    private fun validatorV2(
        id: String,
        delegatorStake: BigDecimal = BigDecimal.ZERO,
        lastMissed: Long? = null,
        lastProposed: Long? = null,
    ): Validator =
        Validator(
            id = id,
            blockId = "blk",
            blockNumber = 0,
            blockTimestamp = 0,
            status = Status.ACTIVE,
            delegatorVetStaked = delegatorStake,
            lastMissedBlockNumber = lastMissed,
            lastProposedBlockNumber = lastProposed,
        )

    @Test
    fun `getValidationInfo calculates rewards correctly`() = runBlocking {
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

        // Cold-start fallback: parent block totalSupply = 900
        coEvery { service.getTotalVTHOIssuedAtBlock("block-99") } returns BigInteger.valueOf(900)

        val result =
            service.getValidationInfo(
                block,
                BigInteger.valueOf(1000),
                validatorV2("0xVAL1", delegatorStake = BigDecimal("0.5")),
            )!!

        assertEquals("100-0xVAL1", result.id)
        assertEquals(BigInteger.valueOf(100), result.blockReward) // 1000 - 900
        assertEquals(BigInteger.valueOf(21), result.priorityReward) // 16 + 5
        assertEquals(BigInteger.valueOf(121), result.total)
        assertEquals(BlockStatus.VALIDATED, result.status)
        assertEquals(BigInteger.valueOf(70), result.delegatorRewards) // 100 * 0.7 (has delegations)
    }

    @Test
    fun `getValidationInfo computes delta correctly across multiple blocks`() = runBlocking {
        val block1 = createBlock(num = 101, signer = "0xVAL1")
        coEvery { service.getTotalVTHOIssuedAtBlock("block-100") } returns BigInteger.valueOf(900)
        val res1 =
            service.getValidationInfo(block1, BigInteger.valueOf(1000), validatorV2("0xVAL1"))!!
        assertEquals(BigInteger.valueOf(100), res1.blockReward)

        // Second block: cache now holds 1000 from the prior call.
        val block2 = createBlock(num = 102, signer = "0xVAL2")
        val res2 =
            service.getValidationInfo(block2, BigInteger.valueOf(1200), validatorV2("0xVAL2"))!!
        assertEquals(BigInteger.valueOf(200), res2.blockReward) // 1200 - 1000
    }

    @Test
    fun `getValidatorsWithMissedSlots returns just-missed validators`() {
        val block = createBlock(num = 50, signer = "0xvalidator")

        val changed =
            listOf(validatorV2("0xA", lastMissed = 50), validatorV2("0xB", lastMissed = 49))

        val result = service.getValidatorsWithMissedSlots(block, changed)
        assertEquals(1, result.size)
        assertEquals("0xA", result[0].validator)
        assertEquals(BlockStatus.MISSED, result[0].status)
    }

    @Test
    fun `getValidatorsWithMissedSlots emits one MISSED row per missed slot, no gating`() {
        // Same validator misses at two consecutive blocks — both should be recorded.
        val block1 = createBlock(num = 50, signer = "0xX")
        assertEquals(
            1,
            service
                .getValidatorsWithMissedSlots(block1, listOf(validatorV2("0xA", lastMissed = 50)))
                .size,
        )

        val block2 = createBlock(num = 51, signer = "0xX")
        val second =
            service.getValidatorsWithMissedSlots(
                block2,
                listOf(validatorV2("0xA", lastMissed = 51)),
            )
        assertEquals(1, second.size)
        assertEquals(51L, second[0].blockNumber)
        assertEquals(BlockStatus.MISSED, second[0].status)
    }

    @Test
    fun `getValidatorsWithMissedSlots emits one row per validator missing at the same block`() {
        val block = createBlock(num = 200, signer = "0xX")
        val changed =
            listOf(validatorV2("0xA", lastMissed = 200), validatorV2("0xB", lastMissed = 200))

        val result = service.getValidatorsWithMissedSlots(block, changed)
        assertEquals(2, result.size)
        assertEquals(setOf("0xA", "0xB"), result.map { it.validator }.toSet())
        assertEquals(setOf("200-0xA-MISSED", "200-0xB-MISSED"), result.map { it.id }.toSet())
    }

    @Test
    fun `MISSED id is disambiguated from VALIDATED id when signer also missed at same block`() {
        // Signer V proposes block 300 (VALIDATED id = "300-0xV") and ALSO missed an earlier
        // elapsed slot at the same block (lastMissedBlockNumber == 300). The MISSED row must not
        // share an id with the VALIDATED row or saveAll would overwrite the reward record.
        val block = createBlock(num = 300, signer = "0xV")
        val changed = listOf(validatorV2("0xV", lastMissed = 300))

        val missed = service.getValidatorsWithMissedSlots(block, changed).single()
        assertEquals("300-0xV-MISSED", missed.id)
    }

    @Test
    fun `processBlock takes the signer and the just-missed from the block's changed validators`() {
        runBlocking {
            val block = createBlock(num = 400, signer = "0xABC")
            coEvery { service.getTotalVTHOIssuedAtBlock("block-399") } returns BigInteger.ONE
            val changed =
                listOf(
                    validatorV2("0xabc", delegatorStake = BigDecimal.ONE),
                    validatorV2("0xdef", lastMissed = 400),
                )

            val rows = service.processBlock(block, supplyResponse(BigInteger.TEN), changed)

            assertEquals(
                listOf(BlockStatus.VALIDATED to "0xABC", BlockStatus.MISSED to "0xdef"),
                rows.map { it.status to it.validator },
            )
            assertEquals(BigInteger.valueOf(6), rows.first().delegatorRewards)
            assertTrue(
                service.processBlock(block, supplyResponse(BigInteger.TEN), emptyList()).isEmpty()
            )
        }
    }

    private fun supplyResponse(supply: BigInteger) =
        listOf(
            InspectionResult(
                data = "0x" + supply.toString(16).padStart(64, '0'),
                events = emptyList(),
                transfers = emptyList(),
                gasUsed = 0,
                reverted = false,
                vmError = null,
            )
        )

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

        verify { repository.save(listOf(block)) }
        val hourlyCache =
            service.javaClass
                .getDeclaredField("hourlyCache")
                .apply { isAccessible = true }
                .get(service) as ConcurrentHashMap<*, *>
        assertEquals(12345L, hourlyCache["0xvalidator"])
    }
}
