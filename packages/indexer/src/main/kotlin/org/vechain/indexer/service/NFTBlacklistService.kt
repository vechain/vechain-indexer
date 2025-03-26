package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive
import org.vechain.indexer.repository.NFTBlacklistRepository
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("nft-events")
@Service
open class NFTBlacklistService(
    private val repository: NFTBlacklistRepository,
    private val nftBlacklistArchiveService: ArchiveService<NFTBlacklist, NFTBlacklistArchive>,
) {
    @Transactional(rollbackFor = [Exception::class])
    open fun update(
        updated: List<NFTBlacklist>,
        existing: List<NFTBlacklist>,
    ) {
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        if (existing.isNotEmpty()) {
            nftBlacklistArchiveService.saveAll(existing)
        }
    }

    open fun parseRecords(
        data: List<IndexedEvent>,
        existing: List<NFTBlacklist>,
    ): List<NFTBlacklist> =
        data.map {
            val contractAddress = it.params.getAsString("nft")!!
            val isBlacklisted = it.params.getAsBoolean("isBlacklisted")!!

            val version =
                existing.find { bl -> bl.contractAddress == contractAddress }?.version?.plus(1) ?: 1

            NFTBlacklist(
                version = version,
                contractAddress = contractAddress,
                isBlacklisted = isBlacklisted,
                blockId = it.blockId,
                blockNumber = it.blockNumber,
                blockTimestamp = it.blockTimestamp,
            )
        }

    open fun getExisting(blackListEvents: List<IndexedEvent>): List<NFTBlacklist> {
        val uniqueAddresses = blackListEvents.map { it.params.getAsString("nft")!! }.distinct()
        return repository.findAllById(uniqueAddresses).toList()
    }
}
