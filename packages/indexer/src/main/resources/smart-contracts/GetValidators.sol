// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import { IProtocolStaker } from "./interfaces/IProtocolStaker.sol";

interface Energy {
  function totalSupply() external view returns (uint256);

  function totalBurned() external view returns (uint256);
}

interface PriceFeedOracle {
  function getLatestValue(bytes32 id) external view returns (uint128 value, uint128 updatedAt);
}

contract GetValidators {
  IProtocolStaker private constant STAKER = IProtocolStaker(payable(0x00000000000000000000000000005374616B6572));
  Energy private constant ENERGY = Energy(0x0000000000000000000000000000456E65726779);
  PriceFeedOracle private constant PRICE_FEED = PriceFeedOracle(0xB15b03E136726FF63890745C8494638da774c768); // Update per network

  bytes32 internal constant VET_ID = 0x7665742d75736400000000000000000000000000000000000000000000000000;
  bytes32 internal constant VTHO_ID = 0x7674686f2d757364000000000000000000000000000000000000000000000000;

  // staker stats
  function stakerBalance() public view returns (uint256) {
    return getBalance(address(STAKER));
  }

  function totalStake() public view returns (uint256, uint256) {
    return STAKER.totalStake();
  }

  function queuedStake() public view returns (uint256) {
    return STAKER.queuedStake();
  }

  function getBalance(address account) private view returns (uint256) {
    return account.balance;
  }

  // VTHO Stats
  function vthoTotalSupply() public view returns (uint256) {
    return ENERGY.totalSupply();
  }

  function totalBurned() public view returns (uint256) {
    return ENERGY.totalBurned();
  }

  function getVetPriceUsd() public view returns (uint128) {
    (uint128 vetPriceUsd, ) = PRICE_FEED.getLatestValue(VET_ID);
    return vetPriceUsd;
  }

    function getVthoPriceUsd() public view returns (uint128) {
        (uint128 vthoPriceUsd, ) = PRICE_FEED.getLatestValue(VTHO_ID);
        return vthoPriceUsd;
    }

  function getValidators()
    public
    view
    returns (
      address[] memory masters, // masters
      address[] memory endorsors, // endorsors
      uint8[] memory statuses, // statuses
      bool[] memory onlines, // onlines
      uint32[] memory offlineBlocks, // offlineBlocks
      uint32[] memory stakingPeriodLengths, // stakingPeriodLengths
      uint32[] memory startBlocks, // startBlocks
      uint32[] memory exitBlocks, // exitBlocks
      uint32[] memory completedPeriods, // completedPeriods
      uint256[] memory validatorLockedStakes, // validatorLockedStakes
      uint256[] memory validatorLockedWeights, // validatorLockedWeights
      uint256[] memory delegatorsStake, // delegatorsStake
      uint256[] memory validatorQueuedStakes, // validatorQueuedStakes
      uint256[] memory totalQueuedStakes, // totalQueuedStakes
      uint256[] memory totalExitingStakes, // totalExitingStakes
      uint256[] memory totalNextPeriodWeights // totalNextPeriodWeights
    )
  {
    address[1000] memory idBuffer;
    uint count = 0;

    // populate active
    address first = STAKER.firstActive();
    while (first != address(0)) {
      idBuffer[count] = first;
      first = STAKER.next(first);
      count++;
    }

    // populate queued
    address next = STAKER.firstQueued();
    while (next != address(0)) {
      idBuffer[count] = next;
      next = STAKER.next(next);
      count++;
    }

    // Allocate output arrays
    address[] memory masters = new address[](count);
    address[] memory endorsors = new address[](count);
    uint8[] memory statuses = new uint8[](count);
    bool[] memory onlines = new bool[](count);
    uint32[] memory offlineBlocks = new uint32[](count);
    uint32[] memory stakingPeriodLengths = new uint32[](count);
    uint32[] memory startBlocks = new uint32[](count);
    uint32[] memory exitBlocks = new uint32[](count);
    uint32[] memory completedPeriods = new uint32[](count);

    uint256[] memory validatorLockedStakes = new uint256[](count);
    uint256[] memory validatorLockedWeights = new uint256[](count);
    uint256[] memory delegatorsStake = new uint256[](count);

    uint256[] memory validatorQueuedStakes = new uint256[](count);
    uint256[] memory totalQueuedStakes = new uint256[](count);

    uint256[] memory totalExitingStakes = new uint256[](count);
    uint256[] memory totalNextPeriodWeights = new uint256[](count);

    for (uint i = 0; i < count; i++) {
      address validatorId = idBuffer[i];

      masters[i] = validatorId;

      (
        address endorsor,
        uint256 validatorStake,
        uint256 combinedWeight,
        uint256 queuedStakeAmount,
        uint8 status,
        uint32 offlineBlock
      ) = STAKER.getValidation(validatorId);
      endorsors[i] = endorsor;
      validatorLockedStakes[i] = validatorStake;
      validatorLockedWeights[i] = combinedWeight;
      validatorQueuedStakes[i] = queuedStakeAmount;
      statuses[i] = status;
      offlineBlocks[i] = offlineBlock;
      onlines[i] = offlineBlock == type(uint32).max;

      (uint32 period, uint32 start, uint32 exit, uint32 compPeriods) = STAKER.getValidationPeriodDetails(validatorId);
      stakingPeriodLengths[i] = period;
      startBlocks[i] = start;
      exitBlocks[i] = exit;
      completedPeriods[i] = compPeriods;

      (uint256 lockedStake, , uint256 totalQueuedStake, uint256 exitingStake, uint256 nextPeriodWeight) = STAKER
        .getValidationTotals(validatorId);
      delegatorsStake[i] = lockedStake - validatorStake;
      totalQueuedStakes[i] = totalQueuedStake;
      totalExitingStakes[i] = exitingStake;
      totalNextPeriodWeights[i] = nextPeriodWeight;
    }

    return (
      masters,
      endorsors,
      statuses,
      onlines,
      offlineBlocks,
      stakingPeriodLengths,
      startBlocks,
      exitBlocks,
      completedPeriods,
      validatorLockedStakes,
      validatorLockedWeights,
      delegatorsStake,
      validatorQueuedStakes,
      totalQueuedStakes,
      totalExitingStakes,
      totalNextPeriodWeights
    );
  }
}
