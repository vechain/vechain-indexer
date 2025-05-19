package org.vechain.indexer.contracts.specifications

data class VIP181Contract(
    override val functions: List<String> =
        listOf(
            Signatures.Common.NAME_FUNCTION,
            Signatures.Common.SYMBOL_FUNCTION,
            Signatures.Common.TOTAL_SUPPLY_FUNCTION,
            Signatures.Common.BALANCE_OF_FUNCTION,
            Signatures.NFT.OWNER_OF_FUNCTION,
            Signatures.Common.TRANSFER_FROM_FUNCTION,
            Signatures.Common.APPROVE_FUNCTION,
            Signatures.NFT.GET_APPROVED_FUNCTION,
            Signatures.Common.SET_APPROVAL_FOR_ALL_FUNCTION,
            Signatures.Common.IS_APPROVED_FOR_ALL_FUNCTION,
        ),
    override val events: List<String> =
        listOf(
            Signatures.Common.TRANSFER_EVENT,
            Signatures.Common.APPROVAL_EVENT,
            Signatures.Common.APPROVAL_FOR_ALL_EVENT,
        ),
) : ContractSpecification
