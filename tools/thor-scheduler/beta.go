package main

import (
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/ethereum/go-ethereum/rlp"
	"github.com/vechain/thor/v2/block"
)

// computeBeta returns the hex-encoded Beta value from a raw RLP-encoded thor block header.
//
// Returns an empty string (not an error) for pre-VIP-193 blocks (signature length 65) and for
// the genesis block — both legitimately have no VRF Beta.
func computeBeta(rawHeaderHex string) (string, error) {
	if rawHeaderHex == "" {
		return "", errors.New("rawHeader is required")
	}
	raw, err := hex.DecodeString(strings.TrimPrefix(rawHeaderHex, "0x"))
	if err != nil {
		return "", fmt.Errorf("decode hex: %w", err)
	}
	var hdr block.Header
	if err := rlp.DecodeBytes(raw, &hdr); err != nil {
		return "", fmt.Errorf("rlp decode header: %w", err)
	}
	beta, err := hdr.Beta()
	if err != nil {
		return "", fmt.Errorf("compute beta: %w", err)
	}
	if beta == nil {
		return "", nil
	}
	return "0x" + hex.EncodeToString(beta), nil
}
