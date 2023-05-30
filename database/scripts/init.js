// Initialise Replica Set
//rs.initiate()

// Create database
db = db.getSiblingDB("vechain");


// Create collections
db.createCollection("blocks");
db.createCollection("transactions");
db.createCollection("clauses");
db.createCollection("contracts");
db.createCollection("transfer_events");
db.createCollection("nfts");


// Create indexes

// blocks
db.blocks.createIndex({blockNumber: -1}, {name: "block_blockNumber_-1", unique: true});

// transactions
db.transactions.createIndex({blockNumber: -1}, {name: "tx_blockNumber_-1"});
db.transactions.createIndex({origin: 1, blockNumber: -1, _id: -1}, {name: "tx_origin_1_blockNumber_-1__id_-1"});
db.transactions.createIndex({gasPayer: 1, blockNumber: -1, _id: -1}, {name: "tx_gasPayer_1_blockNumber_-1__id_-1"});

// clauses
db.clauses.createIndex({blockNumber: -1}, {name: "clause_blockNumber_-1"});
db.clauses.createIndex(
    {
        origin: 1,
        blockNumber: -1,
        txId: -1,
        _id: -1
    },
    {
        name: "clause_origin_1_blockNumber_-1_txId_-1__id_-1"
    }
);
db.clauses.createIndex(
    {
        to: 1,
        blockNumber: -1,
        txId: -1,
        _id: -1
    },
    {
        name: "clause_to_1_blockNumber_-1_txId_-1__id_-1"
    }
);

// contracts
db.contracts.createIndex({blockNumber: -1}, {name: "contract_blockNumber_-1"});
db.contracts.createIndex(
    {
        creator: 1,
        blockNumber: -1,
        txId: -1,
        _id: -1
    },
    {
        name: "contract_creator_1_blockNumber_-1_txId_-1__id_-1"
    }
);

// transfer_events
db.transfer_events.createIndex({blockNumber: -1}, {name: "transfer_blockNumber_-1"});
db.transfer_events.createIndex(
    {
        to: 1,
        blockNumber: -1,
        txId: -1,
        _id: -1
    },
    {
        name: "transfer_to_1_blockNumber_-1_txId_-1__id_-1"
    }
);
db.transfer_events.createIndex(
    {
        from: 1,
        blockNumber: -1,
        txId: -1,
        _id: -1
    },
    {
        name: "transfer_from_1_blockNumber_-1_txId_-1__id_-1"
    }
);
db.transfer_events.createIndex(
    {
        tokenAddress: 1,
        blockNumber: -1,
        txId: -1,
        _id: -1
    },
    {
        name: "transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1"
    }
);

// nfts
db.nfts.createIndex({blockNumber: -1}, {name: "nft_blockNumber_-1"});
db.nfts.createIndex({contractAddress: 1, tokenId: 1}, {name: "nft_contractAddress_1_tokenId_1", unique: true});
db.nfts.createIndex(
    {
        owner: 1,
        contractAddress: 1,
        blockNumber: -1,
        txId: -1,
        _id: -1
    },
    {
        name: "nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1"
    }
);

