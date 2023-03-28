package org.vechain.indexer.specifications

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
        const val NAME_FUNCTION_SIGNATURE = "06fdde03"

        /**
         * function symbol() public view returns (string)
         */
        const val SYMBOL_FUNCTION_SIGNATURE = "95d89b41"

        /**
         * function totalSupply() public view returns (uint256)
         */
        const val TOTAL_SUPPLY_FUNCTION_SIGNATURE = "18160ddd"

        /**
         * function balanceOf(address _owner) public view returns (uint256 balance)
         */
        const val BALANCE_OF_FUNCTION_SIGNATURE = "70a08231"

        /**
         * function ownerOf(uint256 _tokenId) public view returns(address)
         */
        const val OWNER_OF_FUNCTION_SIGNATURE = "6352211e"

        /**
         * function transferFrom(address _from, address _to, uint256 _tokenId) public returns (bool success)
         */
        const val TRANSFER_FROM_FUNCTION_SIGNATURE = "23b872dd"

        /**
         * function approve(address _spender, uint256 _tokenId) public returns (bool success)
         */
        const val APPROVE_FUNCTION_SIGNATURE = "095ea7b3"

        /**
         * function getApproved(uint256 _tokenId) public view returns(address)
         */
        const val GET_APPROVED_FUNCTION_SIGNATURE = "081812fc"

        /**
         * function setApprovalForAll(address _operator, bool _approved) public
         */
        const val SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE = "a22cb465"

        /**
         * function isApprovedForAll(address _owner, address _operator) public view returns(bool)
         */
        const val IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE = "e985e9c5"

        /**
         * event Transfer(address indexed _from, address indexed _to, uint256 _tokenId)
         */
        const val TRANSFER_EVENT_SIGNATURE = "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

        /**
         * event Approval(address indexed _owner, address indexed _spender, uint256 _tokenId)
         */
        const val APPROVAL_EVENT_SIGNATURE = "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"

        /**
         * event ApprovalForAll(address indexed _owner, address indexed _operator, bool _approved)
         */
        const val APPROVAL_FOR_ALL_EVENT_SIGNATURE = "17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31"
    }

}