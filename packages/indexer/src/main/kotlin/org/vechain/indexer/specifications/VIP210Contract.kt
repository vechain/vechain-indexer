package org.vechain.indexer.specifications

import org.vechain.indexer.utils.ContractUtils

data class VIP210Contract(
    override val functions: List<String> = listOf(
        BALANCE_OF_FUNCTION_SIGNATURE,
        BALANCE_OF_BATCH_FUNCTION_SIGNATURE,
        IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE,
        SAFE_TRANSFER_FROM_FUNCTION_SIGNATURE,
        SAFE_BATCH_TRANSFER_FROM_FUNCTION_SIGNATURE,
        SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE,
        URI_FUNCTION_SIGNATURE
    ),

    override val events: List<String> = listOf(
        TRANSFER_SINGLE_EVENT_SIGNATURE,
        TRANSFER_BATCH_EVENT_SIGNATURE,
        APPROVAL_FOR_ALL_EVENT_SIGNATURE,
        //TODO: Fails to identify contract if we include URI event - even though I confirmed this value gets emitted in transactions
        //URI_EVENT_SIGNATURE
    )
) : ContractSpecification {
    companion object {
        /**
         * event TransferSingle(address indexed _operator, address indexed _from, address indexed _to, uint256 _id, uint256 _value, string _data)
         */
        val TRANSFER_SINGLE_EVENT_SIGNATURE =
            ContractUtils.getEventSignature("TransferSingle(address,address,address,uint256,uint256,string)")

        /**
         * event TransferBatch(address indexed _operator, address indexed _from, address indexed _to, uint256[] _ids, uint256[] _values, string _data)
         */
        val TRANSFER_BATCH_EVENT_SIGNATURE =
            ContractUtils.getEventSignature("TransferBatch(address,address,address,uint256[],uint256[],string)")

        /**
         * event ApprovalForAll(address indexed _owner, address indexed _operator, bool _approved)
         */
        val APPROVAL_FOR_ALL_EVENT_SIGNATURE =
            ContractUtils.getEventSignature("ApprovalForAll(address,address,bool)")

        /**
         * event URI(string _value, uint256 indexed _id)
         */
        val URI_EVENT_SIGNATURE = ContractUtils.getEventSignature("URI(string,uint256)")

        /**
         * function safeTransferFrom(address _from, address _to, uint256 _id, uint256 _value, string calldata _data) external
         */
        val SAFE_TRANSFER_FROM_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("safeTransferFrom(address,address,uint256,uint256,string)")

        /**
         * function safeBatchTransferFrom(address _from, address _to, uint256[] calldata _ids, uint256[] calldata _values, string calldata _data) external
         */
        val SAFE_BATCH_TRANSFER_FROM_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("safeBatchTransferFrom(address,address,uint256[],uint256[],string)")

        /**
         * function balanceOf(address _owner, uint256 _id) external view returns (uint256)
         */
        val BALANCE_OF_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("balanceOf(address,uint256)")

        /**
         * function balanceOfBatch(address[] calldata _owners, uint256[] calldata _ids) external view returns (uint256[] memory)
         */
        val BALANCE_OF_BATCH_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("balanceOfBatch(address[],uint256[])")

        /**
         * function setApprovalForAll(address _operator, bool _approved) external
         */
        val SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("setApprovalForAll(address,bool)")

        /**
         * function isApprovedForAll(address _owner, address _operator) external view returns (bool)
         */
        val IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("isApprovedForAll(address,address)")

        /**
         * function uri(uint256 _id) external view returns (string memory)
         */
        val URI_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("uri(uint256)")
    }
}