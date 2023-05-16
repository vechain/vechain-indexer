package org.vechain.indexer.specifications

import org.vechain.indexer.utils.ContractUtils

data class ERC721Contract(

    override val functions: List<String> = listOf(
        BALANCE_OF_FUNCTION_SIGNATURE,
        OWNER_OF_FUNCTION_SIGNATURE,
        SAFE_TRANSFER_FROM_1_FUNCTION_SIGNATURE,
        SAFE_TRANSFER_FROM_2_FUNCTION_SIGNATURE,
        TRANSFER_FROM_FUNCTION_SIGNATURE,
        APPROVE_FUNCTION_SIGNATURE,
        GET_APPROVED_FUNCTION_SIGNATURE,
        SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE,
        IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE,
        SUPPORTS_INTERFACE_FUNCTION_SIGNATURE,
    ),

    override val events: List<String> = listOf(
        TRANSFER_EVENT_SIGNATURE,
        APPROVAL_EVENT_SIGNATURE,
        APPROVAL_FOR_ALL_EVENT_SIGNATURE
    )

) : ContractSpecification {

    companion object {

        /**
         * function balanceOf(address _owner) external view returns (uint256);
         */
        val BALANCE_OF_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("balanceOf(address)")

        /**
         * function ownerOf(uint256 _tokenId) external view returns (address);
         */
        val OWNER_OF_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("ownerOf(uint256)")

        /**
         * function safeTransferFrom(address _from, address _to, uint256 _tokenId, bytes data) external payable;
         */
        val SAFE_TRANSFER_FROM_1_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("safeTransferFrom(address,address,uint256,bytes)")

        /**
         * function safeTransferFrom(address _from, address _to, uint256 _tokenId) external payable;
         */
        val SAFE_TRANSFER_FROM_2_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("safeTransferFrom(address,address,uint256)")

        /**
         * function transferFrom(address _from, address _to, uint256 _tokenId) external payable;
         */
        val TRANSFER_FROM_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("transferFrom(address,address,uint256)")

        /**
         * function approve(address _approved, uint256 _tokenId) external payable;
         */
        val APPROVE_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("approve(address,uint256)")

        /**
         * function setApprovalForAll(address _operator, bool _approved) external;
         */
        val SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("setApprovalForAll(address,bool)")

        /**
         * function getApproved(uint256 _tokenId) external view returns (address);
         */
        val GET_APPROVED_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("getApproved(uint256)")

        /**
         * function isApprovedForAll(address _owner, address _operator) external view returns (bool);
         */
        val IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("isApprovedForAll(address,address)")

        /**
         * This function is defined by the ERC165 interface
         * Every ERC-721 compliant contract must implement the ERC721 and ERC165 interfaces
         *
         * function supportsInterface(bytes4 interfaceID) external view returns (bool);
         */
        val SUPPORTS_INTERFACE_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("supportsInterface(bytes4)")

        /**
         * event Transfer(address indexed _from, address indexed _to, uint256 _tokenId)
         */
        val TRANSFER_EVENT_SIGNATURE = ContractUtils.getEventSignature("Transfer(address,address,uint256)")

        /**
         * event Approval(address indexed _owner, address indexed _spender, uint256 _tokenId)
         */
        val APPROVAL_EVENT_SIGNATURE = ContractUtils.getEventSignature("Approval(address,address,uint256)")

        /**
         * event ApprovalForAll(address indexed _owner, address indexed _operator, bool _approved)
         */
        val APPROVAL_FOR_ALL_EVENT_SIGNATURE = ContractUtils.getEventSignature("ApprovalForAll(address,address,bool)")
    }

}