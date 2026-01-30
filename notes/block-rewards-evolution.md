# VeChain Block Rewards: Calculation Methodology

Rather than implementing era-specific reward calculation logic, we use a universal methodology that works across all eras (pre-Galactica, post-Galactica, and post-Hayabusa).

## Block Reward Calculation

1. **Settle VTHO balance** for the beneficiary to get the true balance at block n-1
   - `Btrue` = settled VTHO balance at block n-1

2. **Calculate transfer delta** for any VTHO transfers involving the beneficiary in block n
   - `Tdelta` = VTHO transferred in - VTHO transferred out

3. **Calculate adjusted balance** at block n
   - `Badj` = (VTHO balance at block n) - Tdelta

4. **Block reward** for the beneficiary
   - `R` = Badj - Btrue

This approach works across all eras without needing to know specific reward formulas or fee mechanisms.
