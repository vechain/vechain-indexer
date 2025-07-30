package org.vechain.indexer.nft

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.NftBlacklist
import org.vechain.indexer.model.NftBlacklistArchive
import org.vechain.indexer.repository.NftBlacklistRepository
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("nfts")
@Service
open class NftBlacklistService(
    private val mongoTemplate: MongoTemplate,
    private val repository: NftBlacklistRepository,
    private val nftBlacklistArchiveService: ArchiveService<NftBlacklist, NftBlacklistArchive>,
) {
    private val logger = LoggerFactory.getLogger(NftBlacklistService::class.java)

    @Transactional(rollbackFor = [Exception::class])
    open fun update(updated: List<NftBlacklist>, existing: List<NftBlacklist>) {
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        if (existing.isNotEmpty()) {
            nftBlacklistArchiveService.saveAll(existing)
        }
    }

    open fun parseRecords(
        data: List<IndexedEvent>,
        existing: List<NftBlacklist>,
    ): List<NftBlacklist> {
        // Pre-index existing records for faster lookup
        val existingByAddress = existing.associateBy { it.contractAddress }

        // Group events by contract address, keeping only the latest event per address
        val latestEventsByAddress =
            data
                .map { event ->
                    val contractAddress =
                        event.params.getAsString("nft")
                            ?: throw IllegalArgumentException(
                                "Missing 'nft' param in event: ${event.id}"
                            )
                    contractAddress to event
                }
                .groupingBy { it.first }
                .reduce { _, acc, e ->
                    if (e.second.blockNumber > acc.second.blockNumber) e else acc
                }
                .mapValues { it.value.second }

        return latestEventsByAddress.map { (contractAddress, event) ->
            val isBlacklisted =
                when (event.eventType) {
                    "NFTBlacklisted" -> true
                    "NFTWhitelisted" -> false
                    else ->
                        throw IllegalArgumentException(
                            "Unexpected eventType '${event.eventType}' in event: ${event.id}"
                        )
                }

            val version = existingByAddress[contractAddress]?.version?.plus(1) ?: 1

            NftBlacklist(
                version = version,
                contractAddress = contractAddress,
                isBlacklisted = isBlacklisted,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )
        }
    }

    open fun getExisting(blackListEvents: List<IndexedEvent>): List<NftBlacklist> {
        val uniqueAddresses =
            blackListEvents
                .map {
                    it.params.getAsString("nft")
                        ?: throw IllegalArgumentException("Missing 'nft' param in event: ${it.id}")
                }
                .distinct()
        return repository.findAllById(uniqueAddresses).toList()
    }
}
