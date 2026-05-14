# thor-scheduler

A small co-process invoked by the ValidatorV2 indexer to compute the deterministic VeChainThor PoS
proposer schedule and to extract `Beta` from raw block headers (VRF Verify).

The indexer spawns it once at startup and communicates over stdin/stdout via line-delimited JSON.

## Build

```
make build
```

This produces a `thor-scheduler` binary in this directory. The Kotlin side reads the binary path
from the `validator-v2.scheduler-binary` Spring property (default `./tools/thor-scheduler/thor-scheduler`).

## Protocol

One JSON object per line on stdin, one response per line on stdout. The `id` field is echoed back
so callers can correlate concurrent requests.

### `schedule`

```
> {"id":1,"op":"schedule","seed":"0x...","parentBlockNumber":12345,
   "proposers":[{"address":"0x...","weight":100,"active":true},...]}
< {"id":1,"schedule":["0x...","0x...",...]}
```

Returns the active proposers ordered by slot. The validator at index `k` of the returned list is
scheduled to propose the block at `parent.timestamp + (k+1) * BlockInterval`.

### `beta`

```
> {"id":2,"op":"beta","rawHeader":"0x..."}
< {"id":2,"beta":"0x..."}        # or {"beta":""} for pre-VIP-193 / genesis
```

Takes the raw RLP-encoded thor block header (as returned by `GET /blocks/{id}?raw=true`) and
returns the verified VRF output (Beta) used as the epoch seed source.

### Errors

```
< {"id":N,"error":"..."}
```
