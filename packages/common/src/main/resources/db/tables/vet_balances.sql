------------------------------------------------------------
-- VET BALANCE TABLE (non-versioned, append-only)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS vet_balances (
    id TEXT NOT NULL PRIMARY KEY,
    address TEXT NOT NULL,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    balance NUMERIC NOT NULL
);

-- Index for queries by address with timestamp ordering
CREATE INDEX IF NOT EXISTS idx_vet_balances_address_timestamp
    ON vet_balances (address, block_timestamp DESC);
-- Index for rollback operations
CREATE INDEX IF NOT EXISTS idx_vet_balances_block_number
    ON vet_balances (block_number);
