package org.vechain.indexer.specifications

import org.vechain.indexer.utils.ContractUtils

data class VIP180Contract(

    override val functions: List<String> = listOf(
        NAME_FUNCTION_SIGNATURE,
        SYMBOL_FUNCTION_SIGNATURE,
        DECIMALS_FUNCTION_SIGNATURE,
        TOTAL_SUPPLY_FUNCTION_SIGNATURE,
        BALANCE_OF_FUNCTION_SIGNATURE,
        TRANSFER_FUNCTION_SIGNATURE,
        TRANSFER_FROM_FUNCTION_SIGNATURE,
        APPROVE_FUNCTION_SIGNATURE,
        ALLOWANCE_FUNCTION_SIGNATURE
    ),

    override val events: List<String> = listOf(
        TRANSFER_EVENT_SIGNATURE,
        APPROVAL_EVENT_SIGNATURE
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
         * function decimals() public view returns (uint8)
         */
        val DECIMALS_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("decimals()")

        /**
         * function totalSupply() public view returns (uint256)
         */
        val TOTAL_SUPPLY_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("totalSupply()")

        /**
         * function balanceOf(address _owner) public view returns (uint256 balance)
         */
        val BALANCE_OF_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("balanceOf(address)")

        /**
         * function transfer(address _to, uint256 _value) public returns (bool success)
         */
        val TRANSFER_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("transfer(address,uint256)")

        /**
         * function transferFrom(address _from, address _to, uint256 _value) public returns (bool success)
         */
        val TRANSFER_FROM_FUNCTION_SIGNATURE =
            ContractUtils.getFunctionSignature("transferFrom(address,address,uint256)")

        /**
         * function approve(address _spender, uint256 _value) public returns (bool success)
         */
        val APPROVE_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("approve(address,uint256)")

        /**
         * function allowance(address _owner, address _spender) public view returns (uint256 remaining)
         */
        val ALLOWANCE_FUNCTION_SIGNATURE = ContractUtils.getFunctionSignature("allowance(address,address)")

        /**
         * event Transfer(address indexed _from, address indexed _to, uint256 _value)
         */
        val TRANSFER_EVENT_SIGNATURE = ContractUtils.getEventSignature("Transfer(address,address,uint256)")

        /**
         * event Approval(address indexed _owner, address indexed _spender, uint256 _value)
         */
        val APPROVAL_EVENT_SIGNATURE = ContractUtils.getEventSignature("Approval(address,address,uint256)")
    }

}