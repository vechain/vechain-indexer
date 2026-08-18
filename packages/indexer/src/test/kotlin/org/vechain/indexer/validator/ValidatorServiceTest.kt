package org.vechain.indexer.validator

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.DetectedNetwork
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.BlockUnexpanded
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class ValidatorServiceTest {
    private lateinit var repository: ValidatorRepository
    private lateinit var thorClient: ThorClient
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var networkDetectionService: NetworkDetectionService
    private lateinit var service: ValidatorService

    @BeforeEach
    fun setup() {
        repository = mockk()
        thorClient = mockk()
        mongoTemplate = mockk(relaxed = true)
        networkDetectionService = mockk()
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(network = VeChainNetwork.MAINNET, genesisBlock = mockk())
        // Default parent timestamp: 10s before the test block so slotsElapsed = 1 and the
        // gap-block path short-circuits. Tests exercising the gap path override via
        // stubParentTimestamp().
        stubParentTimestamp(parentTimestamp = DEFAULT_BLOCK_TIMESTAMP - 10L)
        service =
            ValidatorService(
                repository,
                thorClient,
                mongoTemplate,
                InlineVersioningProperties(),
                networkDetectionService,
                STAKER_ADDRESS,
                validatorStartBlock = 0L,
            )
    }

    @Test
    fun `processBlock reports empty staker inspect response with config context`() {
        every { repository.findAll() } returns emptyList()
        coEvery { thorClient.inspectClauses(any<List<Clause>>(), any()) } returns
            listOf(emptyInspectionResult())

        val exception =
            assertThrows<IllegalStateException> {
                runBlocking { service.processBlock(block(), emptyList()) }
            }

        expectThat(exception.message!!)
            .contains("Built-in staker inspect call 'firstActive' returned no ABI data")
            .contains("block 23414400")
            .contains("INDEXER_START_BLOCK_VALIDATOR")
            .contains("THOR_URL")
            .contains("BUILTIN_STAKER_CONTRACT")
    }

    @Test
    fun `ValidationWithdrawn decrements exiting buckets without flipping status`() {
        val existing =
            baseValidator(VALIDATOR_ID)
                .copy(
                    status = Status.EXITING,
                    validatorVetStaked = BigDecimal("25000000"),
                    validatorLockedWeight = BigDecimal("97620000"),
                    validatorQueuedVetStaked = BigDecimal.ZERO,
                    delegatorVetStaked = BigDecimal("50000000"),
                    queuedVetStaked = BigDecimal.ZERO,
                    exitingVetStaked = BigDecimal("35000000"),
                    validatorExitingVetStaked = BigDecimal("25000000"),
                    totalNextPeriodWeight = BigDecimal("100000000"),
                    exitBlock = 23500000L,
                )
        every { repository.findAll() } returns listOf(existing)

        // 20M VET withdrawn — within both exiting buckets
        val event = validationWithdrawnEvent(VALIDATOR_ID, "20000000000000000000000000")

        val (updates, _) = runBlocking { service.processBlock(midEpochBlock(), listOf(event)) }

        // `updates` also contains an entry for the block.signer (created via newDoc by the
        // signer-credit path) — that's not what this test is about, so filter to the validator
        // the event targets.
        val updated = updates.single { it.id == VALIDATOR_ID }
        // Exiting buckets decremented by the withdrawn stake
        assertEquals(0, updated.exitingVetStaked!!.compareTo(BigDecimal("15000000")))
        assertEquals(0, updated.validatorExitingVetStaked!!.compareTo(BigDecimal("5000000")))
        // Everything else left alone — the original bug was zeroing these / flipping status
        expectThat(updated) {
            get { status }.isEqualTo(Status.EXITING)
            get { validatorVetStaked }.isEqualTo(BigDecimal("25000000"))
            get { validatorLockedWeight }.isEqualTo(BigDecimal("97620000"))
            get { delegatorVetStaked }.isEqualTo(BigDecimal("50000000"))
            get { totalNextPeriodWeight }.isEqualTo(BigDecimal("100000000"))
            get { exitBlock }.isEqualTo(23500000L)
        }
    }

    @Test
    fun `ValidationWithdrawn clamps exiting buckets at zero`() {
        // decreaseStake (no prior signalExit) → validatorExitingVetStaked is 0;
        // withdrawStake must not underflow it.
        val existing =
            baseValidator(VALIDATOR_ID)
                .copy(
                    status = Status.ACTIVE,
                    exitingVetStaked = BigDecimal("100000"),
                    validatorExitingVetStaked = BigDecimal.ZERO,
                )
        every { repository.findAll() } returns listOf(existing)

        // Withdraw more than what's in either bucket — both clamp to zero, no underflow
        val event = validationWithdrawnEvent(VALIDATOR_ID, "500000000000000000000000")

        val (updates, _) = runBlocking { service.processBlock(midEpochBlock(), listOf(event)) }

        // `updates` also contains an entry for the block.signer (created via newDoc by the
        // signer-credit path) — that's not what this test is about, so filter to the validator
        // the event targets.
        val updated = updates.single { it.id == VALIDATOR_ID }
        assertEquals(0, updated.exitingVetStaked!!.compareTo(BigDecimal.ZERO))
        assertEquals(0, updated.validatorExitingVetStaked!!.compareTo(BigDecimal.ZERO))
        expectThat(updated).get { status }.isEqualTo(Status.ACTIVE)
    }

    // -------- Liveness attribution (chain-truth model) --------
    //
    // Algorithm in plain English:
    // - `block.signer` always gets `proposedBlocks++`, `scheduledSlots++`, and our cached
    //   `offlineBlock` cleared to null (they just signed → they're online).
    // - When `slotsElapsed == 1` (the common case: 10s between parent and this block) nobody
    //   could have missed, so no chain queries happen.
    // - When `slotsElapsed > 1` (a gap block: the previous slots were skipped), we batch-call
    //   `getValidation` on every non-signer ACTIVE/EXITING validator. Any whose chain
    //   `offlineBlock` advanced past what we have cached gets `missedSlots++`,
    //   `scheduledSlots++`, and we update our cache.
    //
    // Structural invariant: `scheduledSlots == missedSlots + proposedBlocks`.

    @Test
    fun `clean block credits signer and does not query other validators`() {
        // slotsElapsed=1 (parent 10s ago) → no gap-block fetch should happen.
        val signer = activeValidator(VID_A, weight = 100)
        val other = activeValidator(VID_B, weight = 100)
        every { repository.findAll() } returns listOf(signer, other)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 10L)

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_A), emptyList()) }

        // Only the signer's counters moved.
        val updated = updates.single()
        expectThat(updated) {
            get { id }.isEqualTo(VID_A)
            get { proposedBlocks }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
            get { missedSlots }.isEqualTo(0L)
            get { lastProposedBlockNumber }.isEqualTo(LIVENESS_BLOCK_NUMBER)
            get { offlineBlock }.isEqualTo(null)
        }
        // No inspect-clauses call should have been made for the gap-block path. (The mock will
        // throw if any unstubbed call is made, since `thorClient` is a strict mockk.)
        coVerify(exactly = 0) { thorClient.inspectClauses(any<List<Clause>>(), any()) }
    }

    @Test
    fun `clean block where signer was previously offline clears their offlineBlock`() {
        // The signer was marked offline at block 100. They just signed this block, so the chain
        // has cleared their OfflineBlock — mirror that locally.
        val signer = activeValidator(VID_A, weight = 100).copy(offlineBlock = 100L)
        every { repository.findAll() } returns listOf(signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 10L)

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_A), emptyList()) }

        val updated = updates.single()
        expectThat(updated) {
            get { offlineBlock }.isEqualTo(null) // cleared
            get { proposedBlocks }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
            get { missedSlots }.isEqualTo(0L)
        }
    }

    @Test
    fun `gap block attributes one miss when one validator was skipped`() {
        // slotsElapsed = 2 → one validator scheduled before the signer missed their slot.
        // Chain's offlineBlock for the missed validator was just advanced to LIVENESS_BLOCK_NUMBER.
        val missed = activeValidator(VID_A, weight = 100)
        val signer = activeValidator(VID_B, weight = 100)
        every { repository.findAll() } returns listOf(missed, signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 20L)

        stubOfflineBlockResponse(mapOf(VID_A to LIVENESS_BLOCK_NUMBER))

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_B), emptyList()) }

        val byId = updates.associateBy { it.id }
        expectThat(byId.getValue(VID_A)) {
            get { proposedBlocks }.isEqualTo(0L)
            get { scheduledSlots }.isEqualTo(1L)
            get { missedSlots }.isEqualTo(1L)
            get { offlineBlock }.isEqualTo(LIVENESS_BLOCK_NUMBER)
            get { lastMissedBlockNumber }.isEqualTo(LIVENESS_BLOCK_NUMBER)
        }
        expectThat(byId.getValue(VID_B)) {
            get { proposedBlocks }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
            get { missedSlots }.isEqualTo(0L)
        }
    }

    @Test
    fun `gap block attributes a miss per validator across multiple skipped slots`() {
        // slotsElapsed = 3 → two validators were scheduled before the signer and both missed.
        val missed1 = activeValidator(VID_A, weight = 100)
        val missed2 = activeValidator(VID_B, weight = 100)
        val signer = activeValidator(VID_C, weight = 100)
        every { repository.findAll() } returns listOf(missed1, missed2, signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 30L)

        // Both VID_A and VID_B were marked offline by thor at LIVENESS_BLOCK_NUMBER.
        stubOfflineBlockResponse(
            mapOf(VID_A to LIVENESS_BLOCK_NUMBER, VID_B to LIVENESS_BLOCK_NUMBER)
        )

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_C), emptyList()) }

        val byId = updates.associateBy { it.id }
        expectThat(byId.getValue(VID_A)) {
            get { missedSlots }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
        }
        expectThat(byId.getValue(VID_B)) {
            get { missedSlots }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
        }
        expectThat(byId.getValue(VID_C)) {
            get { proposedBlocks }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
        }
    }

    @Test
    fun `repeat-miss for an already-offline validator counts exactly one new miss`() {
        // V was offline at block 100 (cached). On this gap block thor advanced their chain
        // OfflineBlock to LIVENESS_BLOCK_NUMBER. We see M -> N transition; count one new miss.
        val alreadyOffline =
            activeValidator(VID_A, weight = 100)
                .copy(offlineBlock = 100L, missedSlots = 5L, scheduledSlots = 5L)
        val signer = activeValidator(VID_B, weight = 100)
        every { repository.findAll() } returns listOf(alreadyOffline, signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 20L)

        stubOfflineBlockResponse(mapOf(VID_A to LIVENESS_BLOCK_NUMBER))

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_B), emptyList()) }

        val updatedA = updates.single { it.id == VID_A }
        expectThat(updatedA) {
            get { missedSlots }.isEqualTo(6L) // one more than the seeded 5
            get { scheduledSlots }.isEqualTo(6L)
            get { offlineBlock }.isEqualTo(LIVENESS_BLOCK_NUMBER)
        }
    }

    @Test
    fun `gap block does not attribute a miss when chain offlineBlock matches cache`() {
        // V's cached offlineBlock matches the chain's value — no new miss happened to V on this
        // block. Some OTHER validator must have been the one skipped; we don't attribute to V.
        val alreadyOfflineSameBlock =
            activeValidator(VID_A, weight = 100)
                .copy(offlineBlock = LIVENESS_BLOCK_NUMBER, missedSlots = 1L, scheduledSlots = 1L)
        val missed = activeValidator(VID_C, weight = 100)
        val signer = activeValidator(VID_B, weight = 100)
        every { repository.findAll() } returns listOf(alreadyOfflineSameBlock, missed, signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 20L)

        stubOfflineBlockResponse(
            mapOf(VID_A to LIVENESS_BLOCK_NUMBER, VID_C to LIVENESS_BLOCK_NUMBER)
        )

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_B), emptyList()) }

        // VID_A had no change; should NOT be in updates (counters unchanged).
        expectThat(updates.map { it.id }.toSet()).isEqualTo(setOf(VID_B, VID_C))
        val updatedC = updates.single { it.id == VID_C }
        expectThat(updatedC) {
            get { missedSlots }.isEqualTo(1L)
            get { offlineBlock }.isEqualTo(LIVENESS_BLOCK_NUMBER)
        }
    }

    @Test
    fun `gap block excludes the signer from the offlineBlock scan`() {
        // The signer just signed, so by definition they're online — there's no point asking the
        // chain about them. The batch scan only goes to non-signer ACTIVE/EXITING validators.
        val signer = activeValidator(VID_A, weight = 100)
        val missed = activeValidator(VID_B, weight = 100)
        every { repository.findAll() } returns listOf(signer, missed)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 20L)

        val clausesSlot = slot<List<Clause>>()
        coEvery { thorClient.inspectClauses(capture(clausesSlot), any()) } returns
            listOf(getValidationResponse(offlineBlock = LIVENESS_BLOCK_NUMBER))

        runBlocking { service.processBlock(livenessBlock(signer = VID_A), emptyList()) }

        // Exactly one clause — for VID_B (signer VID_A excluded).
        expectThat(clausesSlot.captured.size).isEqualTo(1)
    }

    @Test
    fun `gap block scan excludes QUEUED and EXITED validators`() {
        val queued = activeValidator(VID_A, weight = 100).copy(status = Status.QUEUED)
        val exited = activeValidator(VID_B, weight = 100).copy(status = Status.EXITED)
        val activeMissed = activeValidator(VID_C, weight = 100)
        val exitingMissed =
            activeValidator(VID_D, weight = 100)
                .copy(status = Status.EXITING, exitBlock = 99_999_999L)
        val signer = activeValidator(VID_E, weight = 100)
        every { repository.findAll() } returns
            listOf(queued, exited, activeMissed, exitingMissed, signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 30L)

        val clausesSlot = slot<List<Clause>>()
        coEvery { thorClient.inspectClauses(capture(clausesSlot), any()) } returns
            listOf(
                getValidationResponse(offlineBlock = LIVENESS_BLOCK_NUMBER), // VID_C
                getValidationResponse(offlineBlock = LIVENESS_BLOCK_NUMBER), // VID_D
            )

        runBlocking { service.processBlock(livenessBlock(signer = VID_E), emptyList()) }

        // Exactly two clauses — VID_C and VID_D. QUEUED, EXITED, and signer are excluded.
        expectThat(clausesSlot.captured.size).isEqualTo(2)
    }

    @Test
    fun `gap block does not attribute misses when chain reports the MaxUint32 sentinel`() {
        // Regression for the PR #1412 bug: thor serialises a nil OfflineBlock pointer as
        // math.MaxUint32 (see thor/builtin/staker_native.go), i.e. "this validator is online /
        // has never been marked offline". An earlier `value > 0` filter let that sentinel
        // through, so on the first gap block every currently-online candidate was credited a
        // bogus miss at block 4,294,967,295. Confirmed against mainnet block 25,010,538:
        // the indexer attributed 29 misses to that block, but querying the staker contract
        // directly at the same revision showed exactly 1 validator with a non-sentinel
        // offlineBlock — the other 100 all returned MaxUint32.
        val a = activeValidator(VID_A, weight = 100)
        val b = activeValidator(VID_B, weight = 100)
        val c = activeValidator(VID_C, weight = 100)
        val signer = activeValidator(VID_D, weight = 100)
        every { repository.findAll() } returns listOf(a, b, c, signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 20L)

        stubOfflineBlockResponse(
            mapOf(
                VID_A to OFFLINE_BLOCK_SENTINEL,
                VID_B to OFFLINE_BLOCK_SENTINEL,
                VID_C to OFFLINE_BLOCK_SENTINEL,
            )
        )

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_D), emptyList()) }

        // Only the signer should move; A/B/C are all online per the chain.
        expectThat(updates.map { it.id }.toSet()).isEqualTo(setOf(VID_D))
        expectThat(updates.single()) {
            get { proposedBlocks }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
            get { missedSlots }.isEqualTo(0L)
        }
    }

    @Test
    fun `lastMissedBlockNumber records the chain offlineBlock, not the processing block`() {
        // Regression for the PR #1412 bug: when a validator's first observation after a gap
        // shows them already offline at some past block X (e.g. cold start, catch-up, or the
        // surrounding 100-validator scan only catches them now), the indexer used to record
        // lastMissedBlockNumber = block.number rather than X. X is authoritative: thor writes
        // parent.Header.Number()+1 into OfflineBlock at the block where the slot was skipped
        // (see thor/packer/pos_scheduler.go), so honouring the chain value preserves the
        // miss-block accuracy through arbitrary indexer lag.
        val pastMissBlock = LIVENESS_BLOCK_NUMBER - 500L
        val missed = activeValidator(VID_A, weight = 100)
        val signer = activeValidator(VID_B, weight = 100)
        every { repository.findAll() } returns listOf(missed, signer)
        stubParentTimestamp(LIVENESS_TIMESTAMP - 20L)

        stubOfflineBlockResponse(mapOf(VID_A to pastMissBlock))

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_B), emptyList()) }

        val updatedA = updates.single { it.id == VID_A }
        expectThat(updatedA) {
            get { missedSlots }.isEqualTo(1L)
            get { offlineBlock }.isEqualTo(pastMissBlock)
            get { lastMissedBlockNumber }.isEqualTo(pastMissBlock)
        }
    }

    @Test
    fun `solo network credits signer scheduled+proposed regardless of slotsElapsed`() {
        val service = newSoloService()
        val solo = activeValidator(VID_A, weight = 100)
        every { repository.findAll() } returns listOf(solo)
        // Parent timestamp lookup not stubbed — solo path returns before fetching it.

        val (updates, _) =
            runBlocking { service.processBlock(livenessBlock(signer = VID_A), emptyList()) }

        val updated = updates.single()
        expectThat(updated) {
            get { proposedBlocks }.isEqualTo(1L)
            get { scheduledSlots }.isEqualTo(1L)
            get { missedSlots }.isEqualTo(0L)
            get { offlineBlock }.isEqualTo(null)
        }
    }

    // -------- Test helpers --------

    /** Returns a ValidatorService whose detected network reports CUSTOM (Thor solo). */
    private fun newSoloService(): ValidatorService {
        val net = mockk<NetworkDetectionService>()
        every { net.detectBlocking() } returns
            DetectedNetwork(network = VeChainNetwork.CUSTOM, genesisBlock = mockk())
        return ValidatorService(
            repository,
            thorClient,
            mongoTemplate,
            InlineVersioningProperties(),
            net,
            STAKER_ADDRESS,
            validatorStartBlock = 0L,
        )
    }

    /** Stub the parent-block timestamp lookup. updateLiveness derives `slotsElapsed` from this. */
    private fun stubParentTimestamp(parentTimestamp: Long) {
        val parent = mockk<BlockUnexpanded>()
        every { parent.timestamp } returns parentTimestamp
        coEvery { thorClient.getBlockUnexpanded(any<BlockRevision.Id>()) } returns parent
    }

    /**
     * Stub `thorClient.inspectClauses` so it returns one `getValidation` response per validator,
     * each carrying the supplied `offlineBlock` value (or 0 if missing from [offlineBlockById]).
     * The response order matches `validatorId` iteration order from the working set's
     * ACTIVE/EXITING filter, excluding the block signer.
     */
    private fun stubOfflineBlockResponse(offlineBlockById: Map<String, Long>) {
        coEvery { thorClient.inspectClauses(any<List<Clause>>(), any()) } answers
            {
                val clauses = firstArg<List<Clause>>()
                // Each clause's `data` encodes the validator address it asks about. The address
                // appears as the last 20 bytes of the call data after the 4-byte selector. We
                // decode it here so we return responses with the right offlineBlock per validator.
                clauses.map { clause ->
                    val addr = extractAddressFromCallData(clause.data)
                    getValidationResponse(offlineBlock = offlineBlockById[addr] ?: 0L)
                }
            }
    }

    /** Pull the 20-byte address argument out of a single-address-input call data string. */
    private fun extractAddressFromCallData(callData: String): String {
        val clean = callData.removePrefix("0x")
        // 4-byte selector (8 hex chars) + 32-byte zero-padded address (64 hex chars).
        val addrWord = clean.substring(8, 8 + 64)
        return "0x" + addrWord.substring(24).lowercase()
    }

    /** Build an ABI-encoded `getValidation` response with the supplied `offlineBlock`. */
    private fun getValidationResponse(
        endorser: String = "0x0000000000000000000000000000000000000000",
        stake: BigInteger = BigInteger.ZERO,
        weight: BigInteger = BigInteger.ZERO,
        queuedStake: BigInteger = BigInteger.ZERO,
        status: Int = 2, // chain code for ACTIVE
        offlineBlock: Long = 0L,
    ): InspectionResult {
        val data =
            "0x" +
                encodeWord(endorser.removePrefix("0x")) +
                encodeWord(stake.toString(16)) +
                encodeWord(weight.toString(16)) +
                encodeWord(queuedStake.toString(16)) +
                encodeWord(status.toString(16)) +
                encodeWord(offlineBlock.toString(16))
        return InspectionResult(
            data = data,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0L,
            reverted = false,
            vmError = null,
        )
    }

    /** Zero-pad a hex string to 32 bytes (64 chars). */
    private fun encodeWord(hex: String): String = hex.padStart(64, '0')

    /**
     * Fixture validator. Pre-populates `vetStaked` to match what `withDerivedFields()` would
     * compute (validatorVetStaked + delegatorVetStaked) so the diff function doesn't treat every
     * fixture as "modified" simply because the derived field was previously null.
     */
    private fun activeValidator(id: String, weight: Long): Validator {
        val stake = BigDecimal(weight)
        return baseValidator(id)
            .copy(
                status = Status.ACTIVE,
                validatorLockedWeight = stake,
                validatorVetStaked = stake,
                delegatorVetStaked = BigDecimal.ZERO,
                vetStaked = stake,
            )
    }

    private fun livenessBlock(signer: String): Block =
        block()
            .copy(
                id = "0x" + "a".repeat(64),
                number = LIVENESS_BLOCK_NUMBER,
                timestamp = LIVENESS_TIMESTAMP,
                parentID = "0x" + "b".repeat(64),
                signer = signer,
            )

    private fun baseValidator(id: String): Validator =
        Validator(
            id = id,
            blockId = "0xvblock",
            blockNumber = 23414400,
            blockTimestamp = 1760000000,
        )

    private fun validationWithdrawnEvent(validator: String, stake: String): IndexedEvent =
        buildIndexedEvent(
            blockId = "0xblock-withdrawn",
            blockNumber = 23414401,
            blockTimestamp = 1760000010,
            address = STAKER_ADDRESS,
            eventType = "ValidationWithdrawn",
            params =
                AbiEventParameters(
                    returnValues = mapOf("validator" to validator, "stake" to stake)
                ),
        )

    // Off an epoch boundary (23414400 is one); a non-empty `existing` plus this block number
    // keeps processBlock on the event-driven path without invoking the staker walk.
    private fun midEpochBlock(): Block = block().copy(number = 23414401, signer = "0xother")

    private fun block(): Block =
        Block(
            id = "0x" + "1".repeat(64),
            number = 23414400,
            timestamp = DEFAULT_BLOCK_TIMESTAMP,
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
        const val VALIDATOR_ID = "0xfe28fa171d78fb02bc3227bec8073b4d94ab6c4d"

        const val VID_A = "0x1111111111111111111111111111111111111111"
        const val VID_B = "0x2222222222222222222222222222222222222222"
        const val VID_C = "0x3333333333333333333333333333333333333333"
        const val VID_D = "0x4444444444444444444444444444444444444444"
        const val VID_E = "0x5555555555555555555555555555555555555555"

        const val LIVENESS_BLOCK_NUMBER = 23414401L
        const val LIVENESS_TIMESTAMP = 1760000100L
        const val DEFAULT_BLOCK_TIMESTAMP = 1760000000L

        // Thor encodes a nil OfflineBlock pointer as math.MaxUint32 over the wire.
        const val OFFLINE_BLOCK_SENTINEL = 4_294_967_295L
    }
}
