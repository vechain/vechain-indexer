package org.vechain.indexer

import org.springframework.data.repository.CrudRepository
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService

/**
 * Shared persistence helper for versioned documents.
 *
 * Handles saving current records, archiving previous versions, and invoking the pruner when older
 * archives can be removed.
 */
fun <T, S> saveVersionedDocuments(
    updated: List<T>,
    existing: List<T>,
    repository: CrudRepository<T, *>,
    archiveService: ArchiveService<T, S>,
    pruner: Pruner,
) where T : VersionedDocument, S : Archive<T> {
    if (updated.isNotEmpty()) {
        repository.saveAll(updated)
    }

    if (existing.isEmpty()) return

    archiveService.saveAll(existing)

    val idsToPrune = existing.filter { it.version > 1 }.map { it.getDocumentId() }
    if (idsToPrune.isEmpty()) return

    val latestBlock =
        (updated.asSequence() + existing.asSequence()).maxOfOrNull { it.blockNumber } ?: 0L
    pruner.run(latestBlock, idsToPrune)
}
