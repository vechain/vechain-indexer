-- Local read-only login for the API; the indexer grants it the chain schema on start.
CREATE ROLE api LOGIN PASSWORD 'password';
