# VeChain Block Rewards: Calculation Methodology

Rather than implementing era-specific reward calculation logic, we use a universal methodology that works across all eras (pre-Galactica, post-Galactica, and post-Hayabusa).

## Understanding the VTHO Balance Equation

The VTHO balance at block `n` is affected by several factors:

```
vthoAtN = vthoAtNMinus1 + passiveVtho + R + VTHO_in - VTHO_out - vthoUsed
```

Where (all values relate to the beneficiary `B`):
- `vthoAtNMinus1` = settled VTHO balance at block n-1
- `passiveVtho` = passive VTHO generation (pre-Hayabusa only)
- `R` = block reward (what we're solving for)
- `VTHO_in` = VTHO received via transfers in block n
- `VTHO_out` = VTHO sent via transfers in block n
- `vthoUsed` = VTHO paid as gas in block n

## Block Reward Calculation

To calculate the block reward received by the beneficiary `B` of a block at height `n`:

1. **Get VTHO balance at block `n-1`** \
   Call `/accounts/{B.address}` on Thorest with revision `n-1` to get the settled balance.
   - `vthoAtNMinus1 = settled VTHO balance at block n-1`

2. **Get VTHO balance at block `n`**
   - `vthoAtN = settled VTHO balance at block n`

3. **Calculate transfer delta** \
   Sum of VTHO transfers involving `B` in block `n`.
   - `vthoTransferDelta = VTHO_in - VTHO_out`
   - Positive if net inflow, negative if net outflow

4. **Calculate VTHO used as gas** \
   Sum `tx.paid` for all transactions where `B` is the `gasPayer`.
   - `vthoUsed = sum of tx.paid where gasPayer == B`

5. **Calculate block reward** \
   Rearranging the balance equation to solve for R:
   - `R = vthoAtN - vthoAtNMinus1 - passiveVtho - vthoTransferDelta + vthoUsed`

   Or using `btrue = vthoAtNMinus1 + passiveVtho`:
   - `R = (vthoAtN - vthoTransferDelta + vthoUsed) - btrue`

## Why We ADD vthoUsed

The beneficiary paying gas *reduces* their balance. To isolate the block reward, we must reverse this effect by adding it back.

**Example:**
- Balance at n-1: 1000
- Balance at n: 1500
- Beneficiary paid 100 VTHO as gas
- No transfers, no passive generation

The beneficiary: started with 1000, paid 100 gas (leaving 900), received reward R, ended at 1500.
```
1000 - 100 + R = 1500
R = 600
```

Using the formula:
```
R = (1500 - 0 + 100) - 1000 = 600
```

This approach works across all eras without needing to know specific reward formulas or fee mechanisms.
