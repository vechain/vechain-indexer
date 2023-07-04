package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedNFT

@ChangeUnit(id = "nfts-initializer", order = "8", author = "nawfal-labrahmi")
class NftsInitializerChangeUnit {

    companion object {
        val NFTS = IndexedNFT::class.java
        const val NFT_BLOCKNUMBER_IDX = "nft_blockNumber_-1"
        const val NFT_CONTRACTADDRESS_TOKENID_IDX = "nft_contractAddress_1_tokenId_1"
        const val NFT_OWNER_IDX = "nft_owner_1_blockNumber_-1_txId_-1__id_-1"
        const val NFT_CONTRACTADDRESS_IDX = "nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1"
        const val NFT_OWNER_CONTRACTADDRESS_IDX =
            "nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1"
        const val NFT_OWNER_CONTRACTADDRESS_TOKENADDRESS_IDX =
            "nft_owner_1_contractAddress_1_tokenId_1_blockNumber_-1_txId_-1__id_-1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(NFTS)) mongoTemplate.createCollection(NFTS)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(NFT_BLOCKNUMBER_IDX)
                .on(IndexedNFT::blockNumber.name, Sort.Direction.DESC)
                .background()
        val contractAddressTokenIdIdx: IndexDefinition =
            Index()
                .named(NFT_CONTRACTADDRESS_TOKENID_IDX)
                .on(IndexedNFT::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedNFT::tokenId.name, Sort.Direction.DESC)
                .unique()
                .background()
        val ownerIdx: IndexDefinition =
            Index()
                .named(NFT_OWNER_IDX)
                .on(IndexedNFT::owner.name, Sort.Direction.ASC)
                .on(IndexedNFT::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedNFT::txId.name, Sort.Direction.DESC)
                .on(IndexedNFT::id.name, Sort.Direction.DESC)
                .background()
        val contractAddressIdx: IndexDefinition =
            Index()
                .named(NFT_CONTRACTADDRESS_IDX)
                .on(IndexedNFT::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedNFT::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedNFT::txId.name, Sort.Direction.DESC)
                .on(IndexedNFT::id.name, Sort.Direction.DESC)
                .background()
        val ownerContractAddressIdx: IndexDefinition =
            Index()
                .named(NFT_OWNER_CONTRACTADDRESS_IDX)
                .on(IndexedNFT::owner.name, Sort.Direction.ASC)
                .on(IndexedNFT::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedNFT::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedNFT::txId.name, Sort.Direction.DESC)
                .on(IndexedNFT::id.name, Sort.Direction.DESC)
                .background()
        val ownerContractAddressTokenIdIdx: IndexDefinition =
            Index()
                .named(NFT_OWNER_CONTRACTADDRESS_TOKENADDRESS_IDX)
                .on(IndexedNFT::owner.name, Sort.Direction.ASC)
                .on(IndexedNFT::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedNFT::tokenId.name, Sort.Direction.ASC)
                .on(IndexedNFT::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedNFT::txId.name, Sort.Direction.DESC)
                .on(IndexedNFT::id.name, Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(NFTS).ensureIndex(blockNumberIdx)
        mongoTemplate.indexOps(NFTS).ensureIndex(contractAddressTokenIdIdx)
        mongoTemplate.indexOps(NFTS).ensureIndex(ownerIdx)
        mongoTemplate.indexOps(NFTS).ensureIndex(contractAddressIdx)
        mongoTemplate.indexOps(NFTS).ensureIndex(ownerContractAddressIdx)
        mongoTemplate.indexOps(NFTS).ensureIndex(ownerContractAddressTokenIdIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(NFTS).dropIndex(NFT_BLOCKNUMBER_IDX)
        mongoTemplate.indexOps(NFTS).dropIndex(NFT_CONTRACTADDRESS_TOKENID_IDX)
        mongoTemplate.indexOps(NFTS).dropIndex(NFT_OWNER_IDX)
        mongoTemplate.indexOps(NFTS).dropIndex(NFT_CONTRACTADDRESS_IDX)
        mongoTemplate.indexOps(NFTS).dropIndex(NFT_OWNER_CONTRACTADDRESS_IDX)
        mongoTemplate.indexOps(NFTS).dropIndex(NFT_OWNER_CONTRACTADDRESS_TOKENADDRESS_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(NFTS)) mongoTemplate.dropCollection(NFTS)
    }
}
