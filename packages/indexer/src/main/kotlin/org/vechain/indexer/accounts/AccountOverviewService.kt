package org.vechain.indexer.accounts

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils.toBigInteger
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("accounts", "account-overview")
@Service
open class AccountOverviewService(
    private val repository: AccountOverviewRepository,
    private val archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
    private val accountOverviewPruner: TargetedPruner<AccountOverview, AccountOverviewArchive>,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    open fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<List<AccountOverview>, List<AccountOverview>> {
        assertEventTypes(events, "VET_TRANSFER")

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
            vetSentRule(block, vetTransferEvents, updatedResult, archiveResult)
            vetReceivedRule(block, vetTransferEvents, updatedResult, archiveResult)
        }

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
     * Add VET sent per account
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

            // Update VET sent
            val value = event.params.getAsBigInteger("amount") ?: BigInteger.ZERO
            updated.vetSent += value
        }
    }

    /**
     * Add VET received per account
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

            // Update VET received
            val value = event.params.getAsBigInteger("amount") ?: BigInteger.ZERO
            updated.vetReceived += value
        }
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
