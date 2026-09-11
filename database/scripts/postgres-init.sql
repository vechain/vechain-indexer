-- Local read-only login for the API; the indexer grants it the tables on start.
CREATE ROLE api LOGIN PASSWORD 'password';
