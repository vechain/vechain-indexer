package main

import (
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/vechain/thor/v2/scheduler"
	"github.com/vechain/thor/v2/thor"
)

// computeSchedule returns the ordered list of validator addresses scheduled to propose blocks,
// starting from slot 0 (i.e. parent.timestamp + BlockInterval).
//
// Inactive validators are filtered out — they're never assigned slots, matching
// scheduler/pos.go::NewPoSScheduler's behavior. The order is deterministic given (seed,
// parentBlockNumber, proposers) so two callers with the same inputs produce identical output.
//
// Implementation: thor's NewPoSScheduler is the source of truth, but it requires picking a single
// `addr` from the proposer set and only surfaces that proposer's view via Schedule()/IsScheduled().
// To get the full schedule we probe each slot k in [0, len(activeProposers)) by asking which
// proposer is scheduled at parent.timestamp + (k+1)*BlockInterval. The scheduler's internal
// shuffle is cached per instance so the probe is cheap.
func computeSchedule(seedHex string, parentBlockNumber uint32, proposers []Proposer) ([]string, error) {
	if len(proposers) == 0 {
		return []string{}, nil
	}
	if seedHex == "" {
		return nil, errors.New("seed is required")
	}
	seed, err := hex.DecodeString(strings.TrimPrefix(seedHex, "0x"))
	if err != nil {
		return nil, fmt.Errorf("decode seed: %w", err)
	}

	// Build scheduler.Proposer list and total weight.
	thorProposers := make([]scheduler.Proposer, len(proposers))
	var totalWeight uint64
	var firstAddr thor.Address
	var firstSet bool
	activeCount := 0
	for i, p := range proposers {
		addr, err := parseAddress(p.Address)
		if err != nil {
			return nil, fmt.Errorf("proposer[%d].address: %w", i, err)
		}
		thorProposers[i] = scheduler.Proposer{Address: addr, Active: p.Active, Weight: p.Weight}
		totalWeight += p.Weight
		if p.Active {
			activeCount++
			if !firstSet {
				firstAddr = addr
				firstSet = true
			}
		}
	}
	if activeCount == 0 {
		return []string{}, nil
	}

	// NewPoSScheduler requires `addr` to be in the proposer set AND active — an inactive `addr`
	// gets included in the shuffled sequence (pos.go: `p.Active || p.Address == addr`), which
	// would inflate len(sequence) past activeCount and break the slot probe below.
	sched, err := scheduler.NewPoSScheduler(
		firstAddr,
		thorProposers,
		parentBlockNumber,
		0, // parentBlockTime is only used to compute schedule times; for our probe we pick
		// fixed slot times relative to a zero parent, which is fine because IsScheduled only
		// looks at the slot index modulo len(sequence).
		seed,
		totalWeight,
	)
	if err != nil {
		return nil, fmt.Errorf("build scheduler: %w", err)
	}

	blockInterval := thor.BlockInterval()

	result := make([]string, 0, activeCount)
	for k := 0; k < activeCount; k++ {
		slotTime := uint64(k+1) * blockInterval
		found := false
		for _, p := range thorProposers {
			if !p.Active {
				continue
			}
			if sched.IsScheduled(slotTime, p.Address) {
				result = append(result, "0x"+hex.EncodeToString(p.Address[:]))
				found = true
				break
			}
		}
		if !found {
			return nil, fmt.Errorf("no scheduled proposer for slot %d", k)
		}
	}
	return result, nil
}

func parseAddress(s string) (thor.Address, error) {
	raw, err := hex.DecodeString(strings.TrimPrefix(s, "0x"))
	if err != nil {
		return thor.Address{}, err
	}
	if len(raw) != 20 {
		return thor.Address{}, fmt.Errorf("expected 20 bytes, got %d", len(raw))
	}
	var addr thor.Address
	copy(addr[:], raw)
	return addr, nil
}
