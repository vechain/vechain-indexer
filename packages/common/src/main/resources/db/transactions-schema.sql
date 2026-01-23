CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    type BIGINT NULL,
    size BIGINT NOT NULL,
    chain_tag BIGINT NOT NULL,
    block_ref TEXT NOT NULL,
    expiration BIGINT NOT NULL,
    gas_price_coef BIGINT NULL,
    gas BIGINT NOT NULL,
    max_fee_per_gas TEXT NULL,
    max_priority_fee_per_gas TEXT NULL,
    depends_on TEXT NULL,
    nonce TEXT NOT NULL,
    gas_used BIGINT NOT NULL,
    gas_payer TEXT NOT NULL,
    paid TEXT NOT NULL,
    reward TEXT NOT NULL,
    reverted BOOLEAN NOT NULL,
    origin TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS transaction_clauses (
    tx_id TEXT NOT NULL,
    clause_index INT NOT NULL,
    to_address TEXT NULL,
    value TEXT NOT NULL,
    data TEXT NOT NULL,
    PRIMARY KEY (tx_id, clause_index),
    CONSTRAINT fk_transaction_clauses_tx
        FOREIGN KEY (tx_id)
        REFERENCES transactions (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transaction_outputs (
    tx_id TEXT NOT NULL,
    output_index INT NOT NULL,
    contract_address TEXT NULL,
    PRIMARY KEY (tx_id, output_index),
    CONSTRAINT fk_transaction_outputs_tx
        FOREIGN KEY (tx_id)
        REFERENCES transactions (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transaction_output_events (
    tx_id TEXT NOT NULL,
    output_index INT NOT NULL,
    event_index INT NOT NULL,
    address TEXT NOT NULL,
    data TEXT NOT NULL,
    name TEXT NULL,
    params JSONB NULL,
    PRIMARY KEY (tx_id, output_index, event_index),
    CONSTRAINT fk_output_events_output
        FOREIGN KEY (tx_id, output_index)
        REFERENCES transaction_outputs (tx_id, output_index)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transaction_output_event_topics (
    tx_id TEXT NOT NULL,
    output_index INT NOT NULL,
    event_index INT NOT NULL,
    topic_index INT NOT NULL,
    topic TEXT NOT NULL,
    PRIMARY KEY (tx_id, output_index, event_index, topic_index),
    CONSTRAINT fk_event_topics_event
        FOREIGN KEY (tx_id, output_index, event_index)
        REFERENCES transaction_output_events (tx_id, output_index, event_index)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transaction_output_transfers (
    tx_id TEXT NOT NULL,
    output_index INT NOT NULL,
    transfer_index INT NOT NULL,
    sender TEXT NOT NULL,
    recipient TEXT NOT NULL,
    amount TEXT NOT NULL,
    PRIMARY KEY (tx_id, output_index, transfer_index),
    CONSTRAINT fk_output_transfers_output
        FOREIGN KEY (tx_id, output_index)
        REFERENCES transaction_outputs (tx_id, output_index)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transactions_origin_block
    ON transactions (origin, block_number DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_gas_payer_block
    ON transactions (gas_payer, block_number DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_clauses_to_address
    ON transaction_clauses (to_address);
