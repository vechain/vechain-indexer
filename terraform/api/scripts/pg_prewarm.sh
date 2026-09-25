#!/usr/bin/env bash

# Reads every table and index once, in 1 GiB block ranges, PREWARM_PARALLELISM at a time. A restored
# volume fetches each block from S3 on first read (~50 ms), so one session alone takes days.
# The prewarm_* lines are what the Grafana "Postgres · Prewarm" row parses.

set -euo pipefail

psql -qX -v ON_ERROR_STOP=1 \
  -c "CREATE EXTENSION IF NOT EXISTS pg_prewarm" \
  -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements"

psql -qAtX -v ON_ERROR_STOP=1 -c "
  SELECT 'prewarm_total', coalesce(sum((pages + 131071) / 131072), 0), coalesce(sum(pages), 0)
  FROM (
    SELECT pg_relation_size(c.oid) / 8192 AS pages
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname NOT LIKE 'pg\_%' AND n.nspname <> 'information_schema' AND c.relkind IN ('r', 'i')
  ) r"

# Indexes first, then newest (last) ranges before older ones; 'read' mode spares shared_buffers.
# Each range re-checks its relation, which a backfill may drop or truncate mid-sweep.
psql -qAtX -v ON_ERROR_STOP=1 -c "
  SELECT format(
    'SELECT ''prewarm_range'', c.oid::regclass, %s, pg_prewarm(c.oid, ''read'', ''main'', %s, least(%s, pg_relation_size(c.oid) / 8192 - 1)) FROM pg_class c WHERE c.oid = %s AND pg_relation_size(c.oid) / 8192 > %s',
    b, b, b + 131071, c.oid, b)
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  CROSS JOIN LATERAL generate_series(0, pg_relation_size(c.oid) / 8192 - 1, 131072) AS b
  WHERE n.nspname NOT LIKE 'pg\_%' AND n.nspname <> 'information_schema' AND c.relkind IN ('r', 'i')
  ORDER BY c.relkind = 'r', (pg_relation_size(c.oid) / 8192 - 1 - b) / 131072, c.oid" |
  xargs -r -d '\n' -n 1 -P "${PREWARM_PARALLELISM:?PREWARM_PARALLELISM is required}" \
    psql -qAtX -v ON_ERROR_STOP=1 -c

echo "prewarm_done"
