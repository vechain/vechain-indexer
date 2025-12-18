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
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

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
        assertEventTypes(events, "\$Master", "VET_TRANSFER")
        //        val masterChangeEvents = events.filter { it.eventType == "\$Master" }
        //        //        val vetTransferEvents = events.filter { it.eventType == "VET_TRANSFER" }
        //        if (masterChangeEvents.isNotEmpty()) {
        //            // Log the events
        //            masterChangeEvents.forEach {
        //                logger.info(
        //                    "Processing Master Change Event: ${it.eventType} at block
        // ${block.number}. \n\t Contract Address: ${it.address} \n\t New Master:
        // ${it.params.getAsString("newMaster") ?: "N/A"}}"
        //                )
        //            }
        //        }

        val updatedResult = mutableMapOf<String, AccountOverview>()
        val archiveResult = mutableMapOf<String, AccountOverview>()

        // Execute rules
        transactionsSentRule(block, updatedResult, archiveResult)

        return Pair(emptyList(), emptyList())
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

    protected fun createNewAccountOverview(address: String, block: Block): AccountOverview {
        val blockIdentifier = BlockIdentifier(block.number, block.id)
        return AccountOverview(
            address = address,
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            version = 0,
            firstSeen = blockIdentifier,
            lastSeen = blockIdentifier,
            transactionsSent = 0L,
            clausesSent = 0L,
            vthoGenerated = BigInteger.ZERO,
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
            val updatedRecord = it.copy(version = it.version + 1)
            updated[recordId] = updatedRecord
            return updatedRecord
        }

        // No record exists, create a new one
        val newRecord = createNewAccountOverview(recordId, block)
        updated[recordId] = newRecord
        return newRecord
    }
}
