package org.vechain.indexer.service

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive
import org.vechain.indexer.repository.NFTBlacklistRepository
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("nft-events")
@Service
open class NFTBlacklistService(
    private val mongoTemplate: MongoTemplate,
    private val repository: NFTBlacklistRepository,
    private val nftBlacklistArchiveService: ArchiveService<NFTBlacklist, NFTBlacklistArchive>,
) {

    private val logger = LoggerFactory.getLogger(NFTBlacklistService::class.java)

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
    ): List<NFTBlacklist> {
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
                event.params.getAsBoolean("isBlacklisted")
                    ?: throw IllegalArgumentException(
                        "Missing 'isBlacklisted' param in event: ${event.id}"
                    )

            val version = existingByAddress[contractAddress]?.version?.plus(1) ?: 1

            NFTBlacklist(
                version = version,
                contractAddress = contractAddress,
                isBlacklisted = isBlacklisted,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )
        }
    }

    open fun getExisting(blackListEvents: List<IndexedEvent>): List<NFTBlacklist> {
        val uniqueAddresses =
            blackListEvents
                .map {
                    it.params.getAsString("nft")
                        ?: throw IllegalArgumentException("Missing 'nft' param in event: ${it.id}")
                }
                .distinct()
        return repository.findAllById(uniqueAddresses).toList()
    }

    /**
     * Syncs the NFTs with the blacklist. Ensures that the `isBlacklisted` flag on the `nfts`
     * collection is in sync with the nft_blacklist collection.
     */
    open fun syncBlacklistedNFTs() {
        logger.info("Syncing NFTs with blacklist")

        val contracts = findContractsToUpdate()

        val pipeline =
            listOf(
                Document("\$match", Document("_id", Document("\$in", contracts))),
                Document(
                    "\$lookup",
                    Document()
                        .append("from", BLACKLIST_COLLECTION)
                        .append("localField", CONTRACT_ADDRESS)
                        .append("foreignField", "_id")
                        .append("as", "blacklistInfo")
                ),
                Document(
                    "\$match",
                    Document(
                            "\$expr",
                            Document(
                                "\$ne",
                                listOf(
                                    "\$isBlacklisted",
                                    Document(
                                        "\$arrayElemAt",
                                        listOf("\$blacklistInfo.$IS_BLACKLISTED", 0)
                                    )
                                )
                            )
                        )
                        .append("blacklistInfo.0.$IS_BLACKLISTED", Document("\$exists", true))
                ),
                Document(
                    "\$set",
                    Document(
                        "isBlacklisted",
                        Document("\$arrayElemAt", listOf("\$blacklistInfo.$IS_BLACKLISTED", 0))
                    )
                ),
                Document("\$unset", "blacklistInfo"),
                Document(
                    "\$merge",
                    Document()
                        .append("into", NFTS_COLLECTION)
                        .append("whenMatched", "merge")
                        .append("whenNotMatched", "discard")
                )
            )

        val command =
            Document()
                .append("aggregate", NFTS_COLLECTION)
                .append("pipeline", pipeline)
                .append("cursor", Document()) // Required for command to work

        mongoTemplate.db.runCommand(command)

        logger.info("Successfully synced NFTs with blacklist")
    }

    /**
     * Looks for blacklisted contracts that have an NFT with a different blacklist status. This
     * function is used to improve the performance of the syncBlacklistedNFTs function by reducing
     * the size of the data that needs to be processed by the pipeline.
     */
    open fun findContractsToUpdate(): List<String> {
        logger.info("Finding contracts with mismatched blacklist status")

        val pipeline =
            listOf(
                Document(
                    "\$lookup",
                    Document()
                        .append("from", NFTS_COLLECTION)
                        .append("localField", "_id")
                        .append("foreignField", CONTRACT_ADDRESS)
                        .append("let", Document("expectedFlag", "\$isBlacklisted"))
                        .append(
                            "pipeline",
                            listOf(
                                Document(
                                    "\$match",
                                    Document(
                                        "\$expr",
                                        Document(
                                            "\$ne",
                                            listOf("\$isBlacklisted", "\$\$expectedFlag")
                                        )
                                    )
                                ),
                                Document("\$limit", 1)
                            )
                        )
                        .append("as", "mismatches")
                ),
                Document("\$match", Document("mismatches.0", Document("\$exists", true))),
                Document("\$project", Document("_id", 1))
            )

        val command =
            Document()
                .append("aggregate", BLACKLIST_COLLECTION)
                .append("pipeline", pipeline)
                .append("cursor", Document())

        val result = mongoTemplate.db.runCommand(command)
        val docs =
            result.get("cursor", Document::class.java)?.get("firstBatch") as? List<*>
                ?: emptyList<Any>()

        val mismatchedContracts =
            docs.filterIsInstance<Document>().mapNotNull { it.getString("_id") }

        logger.info("Found ${mismatchedContracts.size} blacklisted contracts to update")

        return mismatchedContracts
    }

    companion object {
        const val NFTS_COLLECTION = "nfts"
        const val BLACKLIST_COLLECTION = "nft_blacklist"
        val CONTRACT_ADDRESS = IndexedNFT::contractAddress.name
        val IS_BLACKLISTED = IndexedNFT::isBlacklisted.name
    }
}
