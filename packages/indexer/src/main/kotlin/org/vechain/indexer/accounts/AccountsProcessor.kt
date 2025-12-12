package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts")
@Component
open class AccountsProcessor(
    private val service: AccountsService,
    repository: AccountsRepository,
    archiveService: ArchiveService<Accounts, AccountsArchive>,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.ACCOUNTS_INDEXER,
    ) {
    override fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val newRecords = service.processBlock(entry.block, entry.callResults())

        if (newRecords.first.isNotEmpty()) {
            service.save(newRecords.first, newRecords.second)
        }
    }
}
