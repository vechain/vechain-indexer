package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.ContractArchive
import org.vechain.indexer.model.IndexedContract

@Profile("contracts")
@ChangeUnit(id = "contracts-initializer", order = "5", author = "nawfal-labrahmi")
class ContractsInitializerChangeUnit {

    companion object {
        val CONTRACTS = IndexedContract::class.java
        const val CONTRACT_BLOCKNUMBER_IDX = "contract_blockNumber_-1"

        const val CREATOR_ISVIP180_IDX =
            "contract_creator_1_isVip180_1_blockNumber_-1_txId_-1__id_-1"
        const val CREATOR_ISVIP181_IDX =
            "contract_creator_1_isVip181_1_blockNumber_-1_txId_-1__id_-1"
        const val CREATOR_ISVIP210_IDX =
            "contract_creator_1_isVip210_1_blockNumber_-1_txId_-1__id_-1"
        const val CREATOR_ISERC20_IDX = "contract_creator_1_isErc20_1_blockNumber_-1_txId_-1__id_-1"
        const val CREATOR_ISERC721_IDX =
            "contract_creator_1_isErc721_1_blockNumber_-1_txId_-1__id_-1"
        const val CREATOR_ISERC1155_IDX =
            "contract_creator_1_isErc1155_1_blockNumber_-1_txId_-1__id_-1"

        const val ISVIP180_IDX = "isVip180_1_blockNumber_-1_txId_-1__id_-1"
        const val ISVIP181_IDX = "isVip181_1_blockNumber_-1_txId_-1__id_-1"
        const val ISVIP210_IDX = "isVip210_1_blockNumber_-1_txId_-1__id_-1"
        const val ISERC20_IDX = "isErc20_1_blockNumber_-1_txId_-1__id_-1"
        const val ISERC721_IDX = "isErc721_1_blockNumber_-1_txId_-1__id_-1"
        const val ISERC1155_IDX = "isErc1155_1_blockNumber_-1_txId_-1__id_-1"

        val ARCHIVE_OBJ = ContractArchive::class.java
        const val ARCHIVE_BLOCKNUMBER_IDX = "data.blockNumber_-1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(CONTRACTS)) mongoTemplate.createCollection(CONTRACTS)
        if (!mongoTemplate.collectionExists(ARCHIVE_OBJ))
            mongoTemplate.createCollection(ARCHIVE_OBJ)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(CONTRACT_BLOCKNUMBER_IDX)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .background()

        val creatorIsVip180Idx: IndexDefinition =
            Index()
                .named(CREATOR_ISVIP180_IDX)
                .on(IndexedContract::creator.name, Sort.Direction.ASC)
                .on(IndexedContract::isVip180.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val creatorIsVip181Idx: IndexDefinition =
            Index()
                .named(CREATOR_ISVIP181_IDX)
                .on(IndexedContract::creator.name, Sort.Direction.ASC)
                .on(IndexedContract::isVip181.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val creatorIsVip210Idx: IndexDefinition =
            Index()
                .named(CREATOR_ISVIP210_IDX)
                .on(IndexedContract::creator.name, Sort.Direction.ASC)
                .on(IndexedContract::isVip210.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val creatorIsErc20Idx: IndexDefinition =
            Index()
                .named(CREATOR_ISERC20_IDX)
                .on(IndexedContract::creator.name, Sort.Direction.ASC)
                .on(IndexedContract::isErc20.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val creatorIsErc721Idx: IndexDefinition =
            Index()
                .named(CREATOR_ISERC721_IDX)
                .on(IndexedContract::creator.name, Sort.Direction.ASC)
                .on(IndexedContract::isErc721.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val creatorIsErc1155Idx: IndexDefinition =
            Index()
                .named(CREATOR_ISERC1155_IDX)
                .on(IndexedContract::creator.name, Sort.Direction.ASC)
                .on(IndexedContract::isErc1155.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()

        val isVip180Idx: IndexDefinition =
            Index()
                .named(ISVIP180_IDX)
                .on(IndexedContract::isVip180.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val isVip181Idx: IndexDefinition =
            Index()
                .named(ISVIP181_IDX)
                .on(IndexedContract::isVip181.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val isVip210Idx: IndexDefinition =
            Index()
                .named(ISVIP210_IDX)
                .on(IndexedContract::isVip210.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val isErc20Idx: IndexDefinition =
            Index()
                .named(ISERC20_IDX)
                .on(IndexedContract::isErc20.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val isErc721Idx: IndexDefinition =
            Index()
                .named(ISERC721_IDX)
                .on(IndexedContract::isErc721.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()
        val isErc1155Idx: IndexDefinition =
            Index()
                .named(ISERC1155_IDX)
                .on(IndexedContract::isErc1155.name, Sort.Direction.ASC)
                .on(IndexedContract::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(CONTRACTS).ensureIndex(blockNumberIdx)

        mongoTemplate.indexOps(CONTRACTS).ensureIndex(creatorIsVip180Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(creatorIsVip181Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(creatorIsVip210Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(creatorIsErc20Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(creatorIsErc721Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(creatorIsErc1155Idx)

        mongoTemplate.indexOps(CONTRACTS).ensureIndex(isVip180Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(isVip181Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(isVip210Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(isErc20Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(isErc721Idx)
        mongoTemplate.indexOps(CONTRACTS).ensureIndex(isErc1155Idx)

        val archiveBlockIdx: IndexDefinition =
            Index()
                .named(ARCHIVE_BLOCKNUMBER_IDX)
                .on("data.blockNumber", Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(ARCHIVE_OBJ).ensureIndex(archiveBlockIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(CONTRACTS).dropIndex(CONTRACT_BLOCKNUMBER_IDX)

        mongoTemplate.indexOps(CONTRACTS).dropIndex(CREATOR_ISVIP180_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(CREATOR_ISVIP181_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(CREATOR_ISVIP210_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(CREATOR_ISERC20_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(CREATOR_ISERC721_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(CREATOR_ISERC1155_IDX)

        mongoTemplate.indexOps(CONTRACTS).dropIndex(ISVIP180_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(ISVIP181_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(ISVIP210_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(ISERC20_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(ISERC721_IDX)
        mongoTemplate.indexOps(CONTRACTS).dropIndex(ISERC1155_IDX)

        mongoTemplate.indexOps(ARCHIVE_OBJ).dropIndex(ARCHIVE_BLOCKNUMBER_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(CONTRACTS)) mongoTemplate.dropCollection(CONTRACTS)
        if (mongoTemplate.collectionExists(ARCHIVE_OBJ)) mongoTemplate.dropCollection(ARCHIVE_OBJ)
    }
}
