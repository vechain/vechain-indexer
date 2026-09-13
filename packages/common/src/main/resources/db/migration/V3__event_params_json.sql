-- jsonb rejects U+0000, which chain data carries; json stores the escape verbatim and nothing extracts from this column in SQL.
ALTER TABLE blocks.event ALTER COLUMN params TYPE json USING params::text::json;
