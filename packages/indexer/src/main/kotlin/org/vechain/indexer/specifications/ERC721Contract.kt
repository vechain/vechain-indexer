package org.vechain.indexer.specifications

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
        const val BALANCE_OF_FUNCTION_SIGNATURE = "70a08231"

        /**
         * function ownerOf(uint256 _tokenId) external view returns (address);
         */
        const val OWNER_OF_FUNCTION_SIGNATURE = "6352211e"

        /**
         * function safeTransferFrom(address _from, address _to, uint256 _tokenId, bytes data) external payable;
         */
        const val SAFE_TRANSFER_FROM_1_FUNCTION_SIGNATURE = "b88d4fde"

        /**
         * function safeTransferFrom(address _from, address _to, uint256 _tokenId) external payable;
         */
        const val SAFE_TRANSFER_FROM_2_FUNCTION_SIGNATURE = "42842e0e"

        /**
         * function transferFrom(address _from, address _to, uint256 _tokenId) external payable;
         */
        const val TRANSFER_FROM_FUNCTION_SIGNATURE = "23b872dd"

        /**
         * function approve(address _approved, uint256 _tokenId) external payable;
         */
        const val APPROVE_FUNCTION_SIGNATURE = "095ea7b3"

        /**
         * function setApprovalForAll(address _operator, bool _approved) external;
         */
        const val SET_APPROVAL_FOR_ALL_FUNCTION_SIGNATURE = "a22cb465"

        /**
         * function getApproved(uint256 _tokenId) external view returns (address);
         */
        const val GET_APPROVED_FUNCTION_SIGNATURE = "081812fc"

        /**
         * function isApprovedForAll(address _owner, address _operator) external view returns (bool);
         */
        const val IS_APPROVED_FOR_ALL_FUNCTION_SIGNATURE = "e985e9c5"

        /**
         * This function is defined by the ERC165 interface
         * Every ERC-721 compliant contract must implement the NFT and ERC165 interfaces
         *
         * function supportsInterface(bytes4 interfaceID) external view returns (bool);
         */
        const val SUPPORTS_INTERFACE_FUNCTION_SIGNATURE = "01ffc9a7"


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