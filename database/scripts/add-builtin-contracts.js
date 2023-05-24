db = db.getSiblingDB("vechain");

const GENESIS_BLOCK_ID = process.env.GENESIS_BLOCK_ID

const contracts = require("/scripts/built-in-contracts.json")

contracts.forEach(contract => {
    contract.blockId = GENESIS_BLOCK_ID
})

db.contracts.insertMany(contracts);
