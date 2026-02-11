package org.vechain.indexer.accounts

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.config.ForkConfig
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils.toBigInteger
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ExecuteAccountResponse
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * A simple read-once cache that clears entries after they are consumed. Useful for caching values
 * that will be needed exactly once in a subsequent operation.
 */
private class ReadOnceCache<K, V> {
    private val cache = mutableMapOf<K, V>()

    /**
     * Get a cached value if present (removes it from cache), or fetch and cache a new value.
     *
     * @param key The cache key
     * @param fetcher Function to fetch the value if not cached
     * @return The cached or fetched value
     */
    suspend fun getOrFetch(key: K, fetcher: suspend () -> V): V {
        val cached = cache.remove(key)
        if (cached != null) {
            return cached
        }
        val fetched = fetcher()
        cache[key] = fetched
        return fetched
    }

    /**
     * Store a value in the cache for later retrieval.
     *
     * @param key The cache key
     * @param value The value to cache
     */
    fun put(key: K, value: V) {
        cache[key] = value
    }

    /** Clear all cached entries. */
    fun clear() {
        cache.clear()
    }
}

@Profile("accounts", "account-overview")
@Service
open class AccountOverviewService(
    private val repository: AccountOverviewRepository,
    private val archiveService: ArchiveService<AccountOverview>,
    private val accountOverviewPruner: TargetedPruner<AccountOverview>,
    private val forkConfig: ForkConfig,
    private val networkDetectionService: NetworkDetectionService,
    private val thorClient: ThorClient,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Cache for account states keyed by (address, blockId). Used to avoid redundant API calls when
     * the state fetched for block N can be reused as the parent state for block N+1.
     */
    private val accountStateCache = ReadOnceCache<Pair<String, String>, ExecuteAccountResponse>()

    private val detectedNetwork: VeChainNetwork by lazy {
        networkDetectionService.detectBlocking().network
    }

    /**
     * Get account state with caching support. Fetches account state and caches it. If a cached
     * value exists for the key, it is returned and removed from the cache (read-once behavior).
     *
     * @param address The account address
     * @param blockId The block ID to query state at
     * @return The account state
     */
    private suspend fun getAccountStateWithCache(
        address: String,
        blockId: String,
    ): ExecuteAccountResponse {
        return accountStateCache.getOrFetch(address to blockId) {
            thorClient.getAccountState(address, BlockRevision.Id(blockId)).also {
                accountStateCache.put(address to blockId, it)
            }
        }
    }

    open suspend fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<List<AccountOverview>, List<AccountOverview>> {
        assertEventTypes(events, "VET_TRANSFER", "Transfer")

        val updatedResult = mutableMapOf<String, AccountOverview>()
        val archiveResult = mutableMapOf<String, AccountOverview>()

        // Execute rules
        if (block.transactions.isNotEmpty()) {
            transactionsSentRule(block, updatedResult, archiveResult)
            vthoBurnedRule(block, updatedResult, archiveResult)
            vthoDelegatedRule(block, updatedResult, archiveResult)
            gasUsedRule(block, updatedResult, archiveResult)
        }

        val vetTransferEvents = events.filter { it.eventType == "VET_TRANSFER" }
        if (vetTransferEvents.isNotEmpty()) {
            // Calculate passive VTHO before updating balances (settles earnings since last
            // activity)
            vthoPassiveGenerationRule(block, vetTransferEvents, updatedResult, archiveResult)
            // Update VET sent/received totals and balance
            vetSentRule(block, vetTransferEvents, updatedResult, archiveResult)
            vetReceivedRule(block, vetTransferEvents, updatedResult, archiveResult)
        }

        // Calculate and apply block rewards for pre-Hayabusa blocks
        vthoBlockRewardsRule(block, events, updatedResult, archiveResult)

        return Pair(updatedResult.values.toList(), archiveResult.values.toList())
    }

    @Transactional
    open fun save(updated: List<AccountOverview>, existing: List<AccountOverview>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            repository = repository,
            archiveService = archiveService,
            pruner = accountOverviewPruner,
        )
    }

    /**
     * Add number of transactions and clauses sent per account
     *
     * @param block Block being processed
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected fun transactionsSentRule(
        block: Block,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        block.transactions.forEach { tx ->
            val recordId = tx.origin
            val updated =
                resolveAccountOverviewForUpdateAndArchive(
                    recordId,
                    block,
                    updatedResult,
                    archiveResult,
                )

            // Update counts
            updated.transactionsSent += 1
            updated.clausesSent += tx.clauses.size.toLong()
        }
    }

    /**
     * Add VTHO burned per account
     *
     * @param block Block being processed
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected fun vthoBurnedRule(
        block: Block,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        block.transactions.forEach { tx ->
            val recordId = tx.gasPayer
            val updated =
                resolveAccountOverviewForUpdateAndArchive(
                    recordId,
                    block,
                    updatedResult,
                    archiveResult,
                )

            // Update VTHO burned
            updated.vthoBurned += toBigInteger(tx.paid)
        }
    }

    /**
     * Add VTHO delegated per account
     *
     * @param block Block being processed
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected fun vthoDelegatedRule(
        block: Block,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        block.transactions
            .filter { it.origin != it.gasPayer }
            .forEach { tx ->
                val recordId = tx.gasPayer
                val updated =
                    resolveAccountOverviewForUpdateAndArchive(
                        recordId,
                        block,
                        updatedResult,
                        archiveResult,
                    )

                // Update VTHO delegated
                updated.vthoDelegated += toBigInteger(tx.paid)
            }
    }

    /**
     * Add gas used where the account is the origin
     *
     * @param block Block being processed
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected fun gasUsedRule(
        block: Block,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        block.transactions.forEach { tx ->
            val recordId = tx.origin
            val updated =
                resolveAccountOverviewForUpdateAndArchive(
                    recordId,
                    block,
                    updatedResult,
                    archiveResult,
                )

            // Update gas used
            updated.gasUsed += BigInteger.valueOf(tx.gasUsed)
        }
    }

    /**
     * Add VET sent per account and update balance
     *
     * @param block Block being processed
     * @param vetTransferEvents List of VET_TRANSFER events in the block
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected fun vetSentRule(
        block: Block,
        vetTransferEvents: List<IndexedEvent>,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        vetTransferEvents.forEach { event ->
            val recordId =
                event.params.getAsString("from")
                    ?: error("Invalid VET_TRANSFER event: missing 'from' param")
            val updated =
                resolveAccountOverviewForUpdateAndArchive(
                    recordId,
                    block,
                    updatedResult,
                    archiveResult,
                )

            // Update VET sent and decrease balance
            val value = event.params.getAsBigInteger("amount") ?: BigInteger.ZERO
            updated.vetSent += value
            updated.vetBalance -= value
        }
    }

    /**
     * Add VET received per account and update balance
     *
     * @param block Block being processed
     * @param vetTransferEvents List of VET_TRANSFER events in the block
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected fun vetReceivedRule(
        block: Block,
        vetTransferEvents: List<IndexedEvent>,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        vetTransferEvents.forEach { event ->
            val recordId =
                event.params.getAsString("to")
                    ?: error("Invalid VET_TRANSFER event: missing 'to' param")
            val updated =
                resolveAccountOverviewForUpdateAndArchive(
                    recordId,
                    block,
                    updatedResult,
                    archiveResult,
                )

            // Update VET received and increase balance
            val value = event.params.getAsBigInteger("amount") ?: BigInteger.ZERO
            updated.vetReceived += value
            updated.vetBalance += value
        }
    }

    /**
     * Calculate and apply passive VTHO generation for accounts involved in VET transfers.
     *
     * This rule settles the passive VTHO earned since the last settlement, based on the account's
     * VET balance and the time elapsed. The rate is 0.000432 VTHO/VET/day (≈5e-9 VTHO/VET/second).
     *
     * Only processes pre-Hayabusa blocks as passive generation ends at the Hayabusa fork.
     *
     * @param block Block being processed
     * @param vetTransferEvents List of VET_TRANSFER events in the block
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected fun vthoPassiveGenerationRule(
        block: Block,
        vetTransferEvents: List<IndexedEvent>,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        val hayabusaBlock = forkConfig.getHayabusaBlock(detectedNetwork)

        // Only process pre-Hayabusa blocks (passive generation ends at Hayabusa)
        if (block.number >= hayabusaBlock) {
            return
        }

        // Collect all unique addresses involved in VET transfers
        val addresses = mutableSetOf<String>()
        vetTransferEvents.forEach { event ->
            event.params.getAsString("from")?.let { addresses.add(it) }
            event.params.getAsString("to")?.let { addresses.add(it) }
        }

        // For each address, calculate passive VTHO earned since last settlement
        addresses.forEach { address ->
            val updated =
                resolveAccountOverviewForUpdateAndArchive(
                    address,
                    block,
                    updatedResult,
                    archiveResult,
                )

            // Use lastVthoSettlement if available, otherwise this is a new account with no prior
            // VET
            val lastSettlement = updated.lastVthoSettlement
            if (
                lastSettlement != null &&
                    updated.vetBalance > BigInteger.ZERO &&
                    lastSettlement < block.timestamp
            ) {
                val durationSeconds = BigInteger.valueOf(block.timestamp - lastSettlement)
                val passiveVtho = calculatePassiveVtho(updated.vetBalance, durationSeconds)
                updated.vthoPassiveGeneration += passiveVtho
            }

            // Update settlement timestamp for next calculation
            updated.lastVthoSettlement = block.timestamp
        }
    }

    /** Check if the given block is the Hayabusa fork block. */
    open fun isHayabusaBlock(blockNumber: Long): Boolean {
        val hayabusaBlock = forkConfig.getHayabusaBlock(detectedNetwork)
        return blockNumber == hayabusaBlock
    }

    /**
     * Get a batch of accounts that need passive VTHO settlement at Hayabusa.
     *
     * @param hayabusaTimestamp The timestamp of the Hayabusa block
     * @param pageable Pagination parameters
     * @return Slice of accounts needing settlement
     */
    open fun getAccountsNeedingHayabusaSettlement(
        hayabusaTimestamp: Long,
        pageable: Pageable,
    ): Slice<AccountOverview> {
        return repository.findAccountsNeedingVthoSettlement(hayabusaTimestamp, pageable)
    }

    /**
     * Settle passive VTHO for a batch of accounts at the Hayabusa fork.
     *
     * Calculates the final passive VTHO earnings from each account's lastVthoSettlement up to the
     * Hayabusa timestamp, then saves the updates.
     *
     * @param accounts The batch of accounts to settle
     * @param hayabusaTimestamp The timestamp of the Hayabusa block
     */
    @Transactional
    open fun settleHayabusaBatch(accounts: List<AccountOverview>, hayabusaTimestamp: Long) {
        val updated = mutableListOf<AccountOverview>()
        val existing = mutableListOf<AccountOverview>()

        accounts.forEach { account ->
            val lastSettlement = account.lastVthoSettlement ?: return@forEach

            // Calculate passive VTHO from last settlement to Hayabusa
            val durationSeconds = BigInteger.valueOf(hayabusaTimestamp - lastSettlement)
            val passiveVtho = calculatePassiveVtho(account.vetBalance, durationSeconds)

            // Create updated record with settled passive VTHO
            val updatedAccount =
                account.copy(version = account.version + 1, lastSeen = hayabusaTimestamp)
            updatedAccount.vthoPassiveGeneration += passiveVtho
            updatedAccount.lastVthoSettlement = hayabusaTimestamp

            existing.add(account)
            updated.add(updatedAccount)
        }

        if (updated.isNotEmpty()) {
            save(updated, existing)
        }
    }

    /**
     * Calculate and apply block rewards to the block beneficiary using universal balance-based
     * calculation. This methodology works across all eras.
     *
     * All values relate to the beneficiary B:
     * - vthoAtNMinus1 = settled VTHO balance at block n-1
     * - vthoAtN = settled VTHO balance at block n
     * - passiveVtho = passive VTHO generation (pre-Hayabusa only)
     * - vthoTransferDelta = VTHO_in - VTHO_out (positive if net inflow)
     * - vthoUsed = VTHO paid as gas in block n
     * - btrue = vthoAtNMinus1 + passiveVtho
     *
     * Block reward: R = (vthoAtN - vthoTransferDelta + vthoUsed) - btrue
     *
     * @param block Block being processed
     * @param events List of all events in the block
     * @param updatedResult Map of updated AccountOverview records
     * @param archiveResult Map of AccountOverview records to be archived
     */
    protected suspend fun vthoBlockRewardsRule(
        block: Block,
        events: List<IndexedEvent>,
        updatedResult: MutableMap<String, AccountOverview>,
        archiveResult: MutableMap<String, AccountOverview>,
    ) {
        // Skip genesis block (no parent block to compare against)
        if (block.number == 0L) {
            return
        }

        val beneficiary = block.beneficiary

        // 1. Get account state at block n-1 (VTHO and VET balances)
        val accountStateAtNMinus1 = getAccountStateWithCache(beneficiary, block.parentID)
        val vthoAtNMinus1 = accountStateAtNMinus1.energy.hexToBigInteger()
        val vetAtNMinus1 = accountStateAtNMinus1.balance.hexToBigInteger()

        // 2. Calculate passive VTHO generation from timestamp(n-1) to timestamp(n)
        val beneficiaryAccount = repository.findByIdOrNull(beneficiary)
        val passiveVtho =
            calculatePassiveVthoForBlock(vetAtNMinus1, block.number, beneficiaryAccount)

        // 3. Calculate Btrue (settled balance at n-1 plus passive generation up to block n)
        val btrue = vthoAtNMinus1 + passiveVtho

        // 4. Get VTHO balance at block n
        val accountStateAtN = getAccountStateWithCache(beneficiary, block.id)
        val vthoAtN = accountStateAtN.energy.hexToBigInteger()

        // 5. Calculate VTHO transfer delta in block n
        val vthoTransferDelta = calculateVthoTransferDelta(beneficiary, events)

        // 6. Calculate VTHO used by beneficiary as gasPayer in block n
        val vthoUsed = calculateVthoUsedByBeneficiary(beneficiary, block)

        // 7. Calculate block reward: R = (balanceAtN - delta + used) - Btrue
        val adjustedBalance = vthoAtN - vthoTransferDelta + vthoUsed
        val reward = adjustedBalance - btrue

        if (reward <= BigInteger.ZERO) {
            return
        }

        val updated =
            resolveAccountOverviewForUpdateAndArchive(
                beneficiary,
                block,
                updatedResult,
                archiveResult,
            )

        updated.vthoBlockRewards += reward
    }

    /**
     * Calculate passive VTHO generation based on VET balance and time duration.
     *
     * Rate: 0.000432 VTHO/VET/day = 5e-9 VTHO/VET/second Formula: passiveVtho = vetBalance *
     * durationSeconds * 5 / 1_000_000_000
     *
     * @param vetBalance The VET balance
     * @param durationSeconds The duration in seconds
     * @return The passive VTHO generated
     */
    protected fun calculatePassiveVtho(
        vetBalance: BigInteger,
        durationSeconds: BigInteger,
    ): BigInteger {
        return vetBalance
            .multiply(durationSeconds)
            .multiply(BigInteger.valueOf(5))
            .divide(BigInteger.valueOf(1_000_000_000))
    }

    /**
     * Calculate passive VTHO generation for a single block (10 seconds).
     *
     * Passive VTHO is only generated pre-Hayabusa for accounts with a lastVthoSettlement.
     *
     * @param vetBalance The VET balance at block n-1
     * @param blockNumber The current block number
     * @param beneficiaryAccount The beneficiary's AccountOverview (may be null)
     * @return The passive VTHO generated (BigInteger.ZERO if not applicable)
     */
    protected fun calculatePassiveVthoForBlock(
        vetBalance: BigInteger,
        blockNumber: Long,
        beneficiaryAccount: AccountOverview?,
    ): BigInteger {
        val hayabusaBlock = forkConfig.getHayabusaBlock(detectedNetwork)

        if (
            blockNumber < hayabusaBlock &&
                beneficiaryAccount != null &&
                beneficiaryAccount.lastVthoSettlement != null
        ) {
            return calculatePassiveVtho(vetBalance, BigInteger.TEN)
        }

        return BigInteger.ZERO
    }

    /**
     * Calculate the VTHO transfer delta for an address in a block.
     *
     * Delta = VTHO_in - VTHO_out (positive if net inflow, negative if net outflow)
     *
     * @param address The address to calculate the delta for
     * @param events All events in the block
     * @return The net VTHO transfer delta
     */
    private fun calculateVthoTransferDelta(
        address: String,
        events: List<IndexedEvent>,
    ): BigInteger {
        val vthoTransfers =
            events.filter { it.eventType == "Transfer" && it.address == VTHO_CONTRACT_ADDRESS }

        var delta = BigInteger.ZERO
        vthoTransfers.forEach { event ->
            val from = event.params.getAsString("from")
            val to = event.params.getAsString("to")
            val value = event.params.getAsBigInteger("value") ?: BigInteger.ZERO

            if (to == address) delta += value // Inflow
            if (from == address) delta -= value // Outflow
        }
        return delta
    }

    /**
     * Calculate the VTHO used by the beneficiary as gasPayer in this block.
     *
     * @param beneficiary The beneficiary address
     * @param block The block containing transactions
     * @return The total VTHO used (from tx.paid)
     */
    private fun calculateVthoUsedByBeneficiary(beneficiary: String, block: Block): BigInteger {
        return block.transactions
            .filter { it.gasPayer == beneficiary }
            .sumOf { toBigInteger(it.paid) }
    }

    /**
     * Creates a new AccountOverview record with initial values from the given block.
     *
     * @param address The account address
     * @param block The block in which the account was first seen
     * @return A new AccountOverview record
     */
    protected fun createNewAccountOverview(address: String, block: Block): AccountOverview {
        return AccountOverview(
            address = address,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            version = 0,
            firstSeen = block.timestamp,
            lastSeen = block.timestamp,
            transactionsSent = 0L,
            clausesSent = 0L,
            vthoBurned = BigInteger.ZERO,
            vthoDelegated = BigInteger.ZERO,
            gasUsed = BigInteger.ZERO,
            vetSent = BigInteger.ZERO,
            vetReceived = BigInteger.ZERO,
            vetBalance = BigInteger.ZERO,
            vthoBlockRewards = BigInteger.ZERO,
            vthoPassiveGeneration = BigInteger.ZERO,
        )
    }

    /**
     * Resolves an AccountOverview for update, creating a new one if it does not exist.
     *
     * If an existing record is found, it is added to [archived] and a version-bumped copy is stored
     * in [updated]. If the record is created in the current block, it is only stored in [updated].
     *
     * @param recordId The ID of the AccountOverview record
     * @param block The current block being processed
     * @param updated Map of updated AccountOverview records
     * @param archived Map of AccountOverview records to be archived
     * @return The AccountOverview record for update
     */
    protected fun resolveAccountOverviewForUpdateAndArchive(
        recordId: String,
        block: Block,
        updated: MutableMap<String, AccountOverview>,
        archived: MutableMap<String, AccountOverview>,
    ): AccountOverview {
        updated[recordId]?.let {
            return it
        }

        // Check if a record exists in the DB
        repository.findByIdOrNull(recordId)?.let {
            // If a record exists add it to the archive and add a copy with incremented version to
            // updated
            archived[recordId] = it
            val updatedRecord = it.copy(version = it.version + 1, lastSeen = block.timestamp)
            updated[recordId] = updatedRecord
            return updatedRecord
        }

        // No record exists, create a new one
        val newRecord = createNewAccountOverview(recordId, block)
        updated[recordId] = newRecord
        return newRecord
    }
}
