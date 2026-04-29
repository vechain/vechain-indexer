# MongoDB Collection Restore

Tooling for copying one or more MongoDB collections from a source database to a destination database. Both databases must be named `vechain`.

Two scripts live here:

- **`restore.sh`** — interactive wrapper. Most people should use this.
- **`restore_local_dump.sh`** — the underlying tool with explicit subcommands (`plan`, `dump-source`, `backup-destination`, `restore`). Use this when you need fine-grained control.

## Requirements

- `docker` (the Mongo client tools run in a container)
- `python3`
- Network reachability to both source and destination MongoDB

## Quick start

```bash
database/restore/restore.sh
```

The wrapper will:

1. Prompt for the **source** URI (preset or custom).
2. Prompt for the **destination** URI (preset or custom).
3. Hidden-prompt for any password missing from a chosen URI.
4. Prompt for the comma-separated **collection** list.
5. Show a summary and ask you to confirm.
6. Stream each collection from source to destination via `mongodump --archive | mongorestore --archive` (no on-disk dump).
7. Print the run directory.

The destination backup step is **off by default**. Pass `--with-backup` to enable it (recommended for irreversible targets).

### Stream vs disk mode

By default the wrapper streams the BSON archive directly from `mongodump` to `mongorestore` without writing the source data to disk. For each collection, document counts are queried before and after, recorded in the manifest, and verified at the end.

If you'd rather have an on-disk recovery artifact (e.g. so you can re-run the destination restore without re-reading the source), pass `--no-stream` to use the original dump-then-restore path. That mode writes `source-dump/vechain/<collection>.bson` under the run directory.

Either way, if the operation fails partway through, the destination collection has already been dropped before the new data was written, so a re-run is required to fully repopulate it. Use `--with-backup` if you need a rollback path.

## Presets

Presets are environment variables prefixed with `MONGO_PRESET_`. The wrapper auto-sources `database/restore/.env` (gitignored) at startup, so the simplest setup is:

```bash
cp database/restore/.env.example database/restore/.env
# edit database/restore/.env and uncomment / fill in the URIs you need
```

Example entries:

```bash
export MONGO_PRESET_LOCAL='mongodb://root:password@localhost:27017/vechain?authSource=admin'
export MONGO_PRESET_GREEN_MAINNET='mongodb+srv://<user>@prod-green-mainnet.example.net/vechain?retryWrites=true&w=majority'
```

URIs that omit the password (`mongodb://user@host/db`) trigger a single hidden prompt. URIs that include the password are used as-is. Either way, the wrapper passes credentials to the underlying script via environment variables — never on the command line, and never written to disk.

If you'd rather keep presets outside the repo entirely, exporting `MONGO_PRESET_*` from your shell profile also works — the wrapper just looks at the environment.

## Non-interactive use

```bash
database/restore/restore.sh \
  --source-preset LOCAL \
  --destination-preset GREEN_MAINNET \
  --collections transfer_events,transactions
```

Add `--non-interactive` to fail rather than prompt for any missing input. Passwords still prompt unless they're embedded in the URI.

Other flags:

- `--source-uri URI` / `--destination-uri URI` — bypass presets.
- `--with-backup` — dump the destination collections before restoring (creates rollback material).
- `--no-stream` — fall back to the dump-then-restore disk path.
- `--run-dir DIR` — override the run directory (default: `database/restore/runs/restore-<timestamp>`).

## What gets written to disk

Per run, under the run directory (default: `database/restore/runs/restore-<timestamp>/`):

- `manifest.txt` — masked URIs, doc counts before/after, collection list.
- `source-dump/vechain/<collection>.bson` — BSON dump from the source. **Only in `--no-stream` mode.**
- `destination-backup/vechain/<collection>.bson` — only if `--with-backup`.
- `logs/*.log` — stdout/stderr of each Mongo subprocess (named `stream-<collection>.log` or `dump-<collection>.log` / `restore-<collection>.log` depending on mode).

`database/restore/runs/` is gitignored. Delete old runs you don't need; move ones you want to keep elsewhere.

## Safety

- Both URIs must point to a database literally named `vechain`. The script refuses any other name.
- Restore (and backup-destination) require the destination host to exactly match a `--confirm-target` string. The wrapper supplies this from the chosen URI.
- The `--drop` flag is used during restore. The destination collection is dropped before the dump is loaded — pass `--with-backup` if you need a rollback path.
- Restore counts are compared against source counts; a mismatch fails the run.

## Direct script use

If you need to invoke the underlying script directly:

```bash
database/restore/restore_local_dump.sh --help
```

Each subcommand requires `--collections` explicitly. Destructive subcommands (`backup-destination`, `restore`) also require `--yes` and `--confirm-target <expected-host>`.
