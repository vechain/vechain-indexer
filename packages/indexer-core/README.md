# Indexer Core

This package contains an abstract VeChain thor indexer class. This class can be extended to create a custom indexer.
Simply extend the class and implement the abstract methods:

- `getBlockFromChain` - should retrieve a block from a thor node
- `getLastSyncedBlockNumber` - should calculate the number of the last block that was synced by the indexer
- `purgeRecords` - should remove any records indexed by the indexer for the given block number
- `processBlock` - the core business logic of the indexer. Generally the block data will be parsed and stored in a
  database

## Implemention

It is important to note that it is the responsibility of the implementing code to keep track of the last synced block.
There are many strategies for doing this. The simplest is to store the block number in whatever record you are storing
as part of your processBlock implementation.
Then the last synced block can be estimated by querying the database for the highest block number. This implementation
isn't perfect, but it is a safe strategy to use.
If the data stored is sparse then you may need to reprocess a number of records when the indexer is restarted. But this
is a small tradeoff for the simplicity of the approach.

Also, it is important that the indexer is implemented in such a way that it is possible to roll back to a previous
block.
This is where the purgeRecords method comes in. In some scenarios such as a chain re-org, the indexer will need to roll
back to a previous block in order to maintain data integrity.
