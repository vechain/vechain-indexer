package org.vechain.indexer.contracts.specifications

import org.vechain.indexer.utils.ContractUtils

object Signatures {

    object Common {
        /**
         * event Transfer(address indexed _from, address indexed _to, uint256 _value)
         */
        val TRANSFER_EVENT = ContractUtils.getEventSignature("Transfer(address,address,uint256)")

        /**
         * event Approval(address indexed _owner, address indexed _spender, uint256 _value)
         */
        val APPROVAL_EVENT = ContractUtils.getEventSignature("Approval(address,address,uint256)")

        /**
         * event ApprovalForAll(address indexed _owner, address indexed _operator, bool _approved)
         */
        val APPROVAL_FOR_ALL_EVENT = ContractUtils.getEventSignature("ApprovalForAll(address,address,bool)")

        /**
         * function name() public view returns (string)
         */
        val NAME_FUNCTION = ContractUtils.getFunctionSignature("name()")

        /**
         * function symbol() public view returns (string)
         */
        val SYMBOL_FUNCTION = ContractUtils.getFunctionSignature("symbol()")

        /**
         * function totalSupply() public view returns (uint256)
         */
        val TOTAL_SUPPLY_FUNCTION = ContractUtils.getFunctionSignature("totalSupply()")

        /**
         * function balanceOf(address _owner) external view returns (uint256);
         */
        val BALANCE_OF_FUNCTION = ContractUtils.getFunctionSignature("balanceOf(address)")

        /**
         * function transferFrom(address _from, address _to, uint256 _value) external payable;
         */
        val TRANSFER_FROM_FUNCTION =
            ContractUtils.getFunctionSignature("transferFrom(address,address,uint256)")

        /**
         * function approve(address _approved, uint256 _value) external payable;
         */
        val APPROVE_FUNCTION = ContractUtils.getFunctionSignature("approve(address,uint256)")

        /**
         * function setApprovalForAll(address _operator, bool _approved) external;
         */
        val SET_APPROVAL_FOR_ALL_FUNCTION =
            ContractUtils.getFunctionSignature("setApprovalForAll(address,bool)")

        /**
         * function isApprovedForAll(address _owner, address _operator) external view returns (bool);
         */
        val IS_APPROVED_FOR_ALL_FUNCTION =
            ContractUtils.getFunctionSignature("isApprovedForAll(address,address)")


    }

    object NFT {
        /**
         * function ownerOf(uint256 _tokenId) external view returns (address);
         */
        val OWNER_OF_FUNCTION = ContractUtils.getFunctionSignature("ownerOf(uint256)")


        /**
         * function safeTransferFrom(address _from, address _to, uint256 _tokenId, bytes data) external payable;
         */
        val SAFE_TRANSFER_FROM_1_FUNCTION =
            ContractUtils.getFunctionSignature("safeTransferFrom(address,address,uint256,bytes)")

        /**
         * function safeTransferFrom(address _from, address _to, uint256 _tokenId) external payable;
         */
        val SAFE_TRANSFER_FROM_2_FUNCTION =
            ContractUtils.getFunctionSignature("safeTransferFrom(address,address,uint256)")


        /**
         * function getApproved(uint256 _tokenId) external view returns (address);
         */
        val GET_APPROVED_FUNCTION = ContractUtils.getFunctionSignature("getApproved(uint256)")
    }


    object ERC165 {
        /**
         * function supportsInterface(bytes4 interfaceId) external view returns (bool);
         */
        val SUPPORTS_INTERFACE_FUNCTION =
            ContractUtils.getFunctionSignature("supportsInterface(bytes4)")
    }

    object Fungible {
        /**
         * function decimals() public view returns (uint8)
         */
        val DECIMALS_FUNCTION = ContractUtils.getFunctionSignature("decimals()")

        /**
         * function transfer(address _to, uint256 _value) public returns (bool success)
         */
        val TRANSFER_FUNCTION = ContractUtils.getFunctionSignature("transfer(address,uint256)")

        /**
         * function allowance(address _owner, address _spender) public view returns (uint256 remaining)
         */
        val ALLOWANCE_FUNCTION = ContractUtils.getFunctionSignature("allowance(address,address)")
    }

    object SemiFungible {
        
        /**
         * event URI(string _value, uint256 indexed _id);
         */
        val URI_EVENT = ContractUtils.getEventSignature("URI(string,uint256)")

        /**
         * function balanceOf(address _owner, uint256 _id) external view returns (uint256);
         */
        val BALANCE_OF_FUNCTION =
            ContractUtils.getFunctionSignature("balanceOf(address,uint256)")

        /**
         * function balanceOfBatch(address[] _owners, uint256[] _ids) external view returns (uint256[] memory);
         */
        val BALANCE_OF_BATCH_FUNCTION =
            ContractUtils.getFunctionSignature("balanceOfBatch(address[],uint256[])")

        /**
         * function uri(uint256 _id) external view returns (string memory)
         */
        val URI_FUNCTION = ContractUtils.getFunctionSignature("uri(uint256)")

    }

}