// thor-scheduler is a long-running JSON-line co-process used by the ValidatorV2 indexer.
//
// Two ops:
//
//	{"id":N,"op":"beta","rawHeader":"0x..."}
//	  -> {"id":N,"beta":"0x..."} or {"id":N,"beta":""} for pre-VIP-193 blocks
//
//	{"id":N,"op":"schedule","seed":"0x...","parentBlockNumber":12345,
//	  "proposers":[{"address":"0x...","weight":100,"active":true},...]}
//	  -> {"id":N,"schedule":["0x...","0x...",...]}  (active validators ordered by slot)
//
// On any failure: {"id":N,"error":"..."}
//
// Reads one JSON object per line on stdin, writes one JSON object per line on stdout.
// Diagnostic logging goes to stderr. ID is echoed back so the caller can correlate.
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"log"
	"os"
)

type Proposer struct {
	Address string `json:"address"`
	Weight  uint64 `json:"weight"`
	Active  bool   `json:"active"`
}

type Request struct {
	ID                uint64     `json:"id"`
	Op                string     `json:"op"`
	RawHeader         string     `json:"rawHeader,omitempty"`
	Seed              string     `json:"seed,omitempty"`
	ParentBlockNumber uint32     `json:"parentBlockNumber,omitempty"`
	Proposers         []Proposer `json:"proposers,omitempty"`
}

type Response struct {
	ID       uint64   `json:"id"`
	Beta     *string  `json:"beta,omitempty"`
	Schedule []string `json:"schedule,omitempty"`
	Error    string   `json:"error,omitempty"`
}

func main() {
	log.SetOutput(os.Stderr)
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	in := bufio.NewScanner(os.Stdin)
	// Allow large requests (long proposer lists).
	in.Buffer(make([]byte, 64*1024), 10*1024*1024)

	out := bufio.NewWriter(os.Stdout)
	enc := json.NewEncoder(out)

	for in.Scan() {
		line := in.Bytes()
		if len(line) == 0 {
			continue
		}
		var req Request
		if err := json.Unmarshal(line, &req); err != nil {
			// Best-effort: pull just the id so the caller can still correlate.
			var idOnly struct {
				ID uint64 `json:"id"`
			}
			_ = json.Unmarshal(line, &idOnly)
			writeResp(out, enc, Response{ID: idOnly.ID, Error: fmt.Sprintf("invalid request: %v", err)})
			continue
		}
		resp := handle(req)
		writeResp(out, enc, resp)
	}
	if err := in.Err(); err != nil {
		log.Fatalf("stdin scan failed: %v", err)
	}
}

func writeResp(out *bufio.Writer, enc *json.Encoder, resp Response) {
	if err := enc.Encode(resp); err != nil {
		log.Printf("encode failed: %v", err)
	}
	_ = out.Flush()
}

func handle(req Request) Response {
	resp := Response{ID: req.ID}
	switch req.Op {
	case "beta":
		beta, err := computeBeta(req.RawHeader)
		if err != nil {
			resp.Error = err.Error()
			return resp
		}
		resp.Beta = &beta
	case "schedule":
		schedule, err := computeSchedule(req.Seed, req.ParentBlockNumber, req.Proposers)
		if err != nil {
			resp.Error = err.Error()
			return resp
		}
		resp.Schedule = schedule
	default:
		resp.Error = "unknown op: " + req.Op
	}
	return resp
}
