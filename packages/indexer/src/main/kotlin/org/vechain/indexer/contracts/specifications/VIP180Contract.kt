package org.vechain.indexer.contracts.specifications

data class VIP180Contract(
  override val functions: List<String> =
    listOf(
      Signatures.Common.NAME_FUNCTION,
      Signatures.Common.SYMBOL_FUNCTION,
      Signatures.Fungible.DECIMALS_FUNCTION,
      Signatures.Common.TOTAL_SUPPLY_FUNCTION,
      Signatures.Common.BALANCE_OF_FUNCTION,
      Signatures.Fungible.TRANSFER_FUNCTION,
      Signatures.Common.TRANSFER_FROM_FUNCTION,
      Signatures.Common.APPROVE_FUNCTION,
      Signatures.Fungible.ALLOWANCE_FUNCTION
    ),
  override val events: List<String> =
    listOf(Signatures.Common.TRANSFER_EVENT, Signatures.Common.APPROVAL_EVENT)
) : ContractSpecification
