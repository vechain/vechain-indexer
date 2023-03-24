package org.vechain.indexer.specifications

data class ERC20Contract(

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
         * Functions:
         *
         * Encoded functions' signatures are obtained like this:
         * keccak256(canonicalName).slice(0, 4).toString('hex')
         * With canonical name being the function's signature stripped off params and whitespaces.
         */

        /**
         * function name() public view returns (string)
         */
        const val NAME_FUNCTION_SIGNATURE = "06fdde03"

        /**
         * function symbol() public view returns (string)
         */
        const val SYMBOL_FUNCTION_SIGNATURE = "95d89b41"

        /**
         * function decimals() public view returns (uint8)
         */
        const val DECIMALS_FUNCTION_SIGNATURE = "313ce567"

        /**
         * function totalSupply() public view returns (uint256)
         */
        const val TOTAL_SUPPLY_FUNCTION_SIGNATURE = "18160ddd"

        /**
         * function balanceOf(address _owner) public view returns (uint256 balance)
         */
        const val BALANCE_OF_FUNCTION_SIGNATURE = "70a08231"

        /**
         * function transfer(address _to, uint256 _value) public returns (bool success)
         */
        const val TRANSFER_FUNCTION_SIGNATURE = "a9059cbb"

        /**
         * function transferFrom(address _from, address _to, uint256 _value) public returns (bool success)
         */
        const val TRANSFER_FROM_FUNCTION_SIGNATURE = "23b872dd"

        /**
         * function approve(address _spender, uint256 _value) public returns (bool success)
         */
        const val APPROVE_FUNCTION_SIGNATURE = "095ea7b3"

        /**
         * function allowance(address _owner, address _spender) public view returns (uint256 remaining)
         */
        const val ALLOWANCE_FUNCTION_SIGNATURE = "dd62ed3e"


        /**
         * Events:
         *
         * Encoded events' signatures are obtained like this:
         * keccak256(canonicalName).toString('hex')
         * With canonical name being the event's signature stripped off params and whitespaces.
         */

        /**
         * event Transfer(address indexed _from, address indexed _to, uint256 _value)
         */
        const val TRANSFER_EVENT_SIGNATURE = "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

        /**
         * event Approval(address indexed _owner, address indexed _spender, uint256 _value)
         */
        const val APPROVAL_EVENT_SIGNATURE = "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"
    }

}


