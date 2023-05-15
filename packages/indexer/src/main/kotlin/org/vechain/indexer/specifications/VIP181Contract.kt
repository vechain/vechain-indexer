package org.vechain.indexer.specifications

import org.vechain.indexer.utils.ContractUtils

data class VIP181Contract(

    override val functions: List<String> = listOf(
        NAME_FUNCTION_SIGNATURE,
        SYMBOL_FUNCTION_SIGNATURE,
        TOTAL_SUPPLY_FUNCTION_SIGNATURE,
        BALANCE_OF_FUNCTION_SIGNATURE,
        OWNER_OF_FUNCTION_SIGNATURE,
        TRANSFER_FROM_FUNCTION_SIGNATURE,
        APPROVE_FUNCTION_SIGNATURE,
        GET_APPROVED_FUNCTION_SIGNATURE,
        SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE,
        IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE
    ),

    override val events: List<String> = listOf(
        TRANSFER_EVENT_SIGNATURE,
        APPROVAL_EVENT_SIGNATURE,
        APPROVAL_FOR_ALL_EVENT_SIGNATURE
    )

) : ContractSpecification {

    companion object {

        /**
         * function name() public view returns (string)
         */
        val NAME_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("name()")

        /**
         * function symbol() public view returns (string)
         */
        val SYMBOL_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("symbol()")

        /**
         * function totalSupply() public view returns (uint256)
         */
        val TOTAL_SUPPLY_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("totalSupply()")

        /**
         * function balanceOf(address _owner) public view returns (uint256 balance)
         */
        val BALANCE_OF_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("balanceOf(address)")

        /**
         * function ownerOf(uint256 _tokenId) public view returns(address)
         */
        val OWNER_OF_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("ownerOf(uint256)")

        /**
         * function transferFrom(address _from, address _to, uint256 _tokenId) public returns (bool success)
         */
        val TRANSFER_FROM_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("transferFrom(address,address,uint256)")

        /**
         * function approve(address _spender, uint256 _tokenId) public returns (bool success)
         */
        val APPROVE_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("approve(address,uint256)")

        /**
         * function getApproved(uint256 _tokenId) public view returns(address)
         */
        val GET_APPROVED_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("getApproved(uint256)")

        /**
         * function setApprovalForAll(address _operator, bool _approved) public
         */
        val SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("setApprovalForAll(address,bool)")

        /**
         * function isApprovedForAll(address _owner, address _operator) public view returns(bool)
         */
        val IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("isApprovedForAll(address,address)")

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