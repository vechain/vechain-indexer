module github.com/vechain/veworld-indexer/tools/thor-scheduler

go 1.26.1

require (
	github.com/ethereum/go-ethereum v1.8.14
	github.com/vechain/thor/v2 v2.4.3
)

require (
	github.com/ProjectZKM/Ziren/crates/go-runtime/zkvm_runtime v0.0.0-20260311194731-d5b7577c683d // indirect
	github.com/beorn7/perks v1.0.1 // indirect
	github.com/bits-and-blooms/bitset v1.20.0 // indirect
	github.com/cespare/xxhash/v2 v2.2.0 // indirect
	github.com/consensys/gnark-crypto v0.18.1 // indirect
	github.com/decred/dcrd/dcrec/secp256k1/v4 v4.4.0 // indirect
	github.com/go-stack/stack v1.7.0 // indirect
	github.com/golang/snappy v0.0.4 // indirect
	github.com/hashicorp/golang-lru v0.0.0-20160813221303-0a025b7e63ad // indirect
	github.com/holiman/uint256 v1.2.4 // indirect
	github.com/mattn/go-isatty v0.0.3 // indirect
	github.com/matttproud/golang_protobuf_extensions/v2 v2.0.0 // indirect
	github.com/pkg/errors v0.8.1-0.20171216070316-e881fd58d78e // indirect
	github.com/prometheus/client_golang v1.18.0 // indirect
	github.com/prometheus/client_model v0.5.0 // indirect
	github.com/prometheus/common v0.45.0 // indirect
	github.com/prometheus/procfs v0.12.0 // indirect
	github.com/qianbin/directcache v0.9.7 // indirect
	github.com/qianbin/drlp v0.0.0-20240102101024-e0e02518b5f9 // indirect
	github.com/syndtr/goleveldb v1.0.1-0.20220614013038-64ee5596c38a // indirect
	github.com/vechain/go-ecvrf v0.0.0-20251211112124-5d5a3ef70fc9 // indirect
	golang.org/x/crypto v0.49.0 // indirect
	golang.org/x/sys v0.42.0 // indirect
	google.golang.org/protobuf v1.33.0 // indirect
	gopkg.in/karalabe/cookiejar.v2 v2.0.0-20150724131613-8dcd6a7f4951 // indirect
)

replace (
	github.com/ethereum/go-ethereum => github.com/vechain/go-ethereum v1.8.15-0.20260324060835-4fc778eca93e
	github.com/syndtr/goleveldb => github.com/vechain/goleveldb v1.0.1-0.20220809091043-51eb019c8655
)
