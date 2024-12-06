package org.vechain.indexer

import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

abstract class StatefulIndexer<T : VersionedDocument, S : Archive<T>, U : Any>(
    repository: BaseIndexedRepository<T>,
    private val archiveService: ArchiveService<T, S>,
    thorClient: ThorClient,
    startBlock: Long,
    syncLogInterval: Long,
    private val prunerEnabled: Boolean,
    private val prunerInterval: Long,
) :
    BaseIndexer(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval
    ) {

    override fun processBlock(block: Block) {
        // First run the pruner
        runPruner(block.number)

        // Second extract any relevant data from the block
        val data = extractData(block)
        if (data.isEmpty()) return

        // Find any existing records
        val existing = findExisting(data)

        // Process the updated records
        val updated = parseRecords(block, data, existing)

        // Finally save the updated records and archive the existing ones
        archiveService.update(updated, existing)
    }

    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber)
    }

    private fun runPruner(blockNumber: Long) {
        if (prunerEnabled && blockNumber % prunerInterval == 0L) {
            prune(blockNumber)
        }
    }

    private fun prune(blockNumber: Long) {
        archiveService.prune(blockNumber)
    }

    /**
     * The concrete implementation of this method should extract the relevant data from the block
     *
     * @param block The block to extract data from
     * @return The extracted data
     */
    abstract fun extractData(block: Block): List<U>

    /**
     * The concrete implementation of this method should find any existing records in the database
     *
     * @param data The data extracted from the block
     * @return A list of existing records
     */
    abstract fun findExisting(data: List<U>): List<T>

    /**
     * The concrete implementation of this method should parse the records updated records, given
     * the Block, extracted data and existing records as inputs
     *
     * @param block The block to parse
     * @param data The extracted data
     * @param existing The existing records
     * @return The updated records
     */
    abstract fun parseRecords(block: Block, data: List<U>, existing: List<T>): List<T>
}
