package org.vechain.indexer.contracts.specifications

import org.vechain.indexer.utils.ContractUtils

data class VIP210Contract(
    override val functions: List<String> =
        listOf(
            Signatures.SemiFungible.BALANCE_OF_FUNCTION,
            Signatures.SemiFungible.BALANCE_OF_BATCH_FUNCTION,
            Signatures.Common.IS_APPROVED_FOR_ALL_FUNCTION,
            SAFE_BATCH_TRANSFER_FROM_FUNCTION,
            SAFE_TRANSFER_FROM_FUNCTION,
            Signatures.Common.SET_APPROVAL_FOR_ALL_FUNCTION,
            Signatures.SemiFungible.URI_FUNCTION,
        ),
    override val events: List<String> =
        listOf(
            TRANSFER_SINGLE_EVENT,
            TRANSFER_BATCH_EVENT,
            SAFE_TRANSFER_FROM_FUNCTION,
            // TODO: Fails to identify contract if we include URI event - even though I confirmed
            // this
            // value gets emitted in transactions
            // Signatures.SemiFungible.URI_EVENT
        ),
) : ContractSpecification {
    companion object {
        /**
         * event TransferSingle(address indexed _operator, address indexed _from, address indexed
         * _to, uint256 _id, uint256 _value, string _data)
         */
        val TRANSFER_SINGLE_EVENT =
            ContractUtils.getEventSignature(
                "TransferSingle(address,address,address,uint256,uint256,string)"
            )

        /**
         * event TransferBatch(address indexed _operator, address indexed _from, address indexed
         * _to, uint256[] _ids, uint256[] _values, string _data)
         */
        val TRANSFER_BATCH_EVENT =
            ContractUtils.getEventSignature(
                "TransferBatch(address,address,address,uint256[],uint256[],string)"
            )

        /**
         * function safeTransferFrom(address _from, address _to, uint256 _id, uint256 _value, string
         * _data) external;
         */
        val SAFE_TRANSFER_FROM_FUNCTION =
            ContractUtils.getFunctionSignature(
                "safeTransferFrom(address,address,uint256,uint256,string)"
            )

        /**
         * function safeBatchTransferFrom(address _from, address _to, uint256[] _ids, uint256[]
         * _values, string _data) external;
         */
        val SAFE_BATCH_TRANSFER_FROM_FUNCTION =
            ContractUtils.getFunctionSignature(
                "safeBatchTransferFrom(address,address,uint256[],uint256[],string)"
            )
    }
}
