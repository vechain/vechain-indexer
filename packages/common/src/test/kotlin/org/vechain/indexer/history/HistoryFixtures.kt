package org.vechain.indexer.history

import java.math.BigInteger
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.ProofV2
import org.vechain.indexer.b3tr.action.SustainabilityProofV2
import org.vechain.indexer.b3tr.voting.AppVote
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.validator.Status

object HistoryFixtures {
    fun address(n: Int) = "0x" + n.toString(16).padStart(40, '0')

    fun hash(n: Int) = "0x" + n.toString(16).padStart(64, '0')

    fun sha1(n: Int) = n.toString(16).padStart(40, '0')

    val uint256Max: String = (BigInteger.TWO.pow(256) - BigInteger.ONE).toString()

    /** Every field set, so a round trip exercises each encoder once. */
    val FULL =
        IndexedHistoryEvent(
            id = sha1(1),
            blockId = hash(100),
            blockNumber = 100,
            blockTimestamp = 1_700_000_000,
            txId = hash(2),
            origin = address(10),
            gasPayer = address(11),
            reverted = false,
            contractAddress = address(20),
            tokenId = uint256Max,
            eventName = HistoryEventName.B3TR_ACTION,
            to = address(12),
            from = address(13),
            value = "1000000000000000000",
            appId = hash(30),
            proof =
                SustainabilityProofV2(
                    version = 2,
                    description = "walked",
                    proof = ProofV2(image = "ipfs://img", link = null, text = null, video = null),
                    impact = Impact(carbon = 12, water = 3),
                ),
            roundId = "42",
            appVotes = listOf(AppVote(hash(31), "7"), AppVote(hash(32), "8")),
            support = Support.FOR,
            votePower = "99",
            voteWeight = "98",
            reason = "because",
            proposalId = uint256Max,
            oldLevel = "1",
            newLevel = "2",
            inputToken = address(14),
            outputToken = address(15),
            inputValue = "5",
            outputValue = "6",
            levelId = "3",
            owner = address(16),
            vetGeneratedVthoRewards = "70",
            delegationRewards = "71",
            migrated = true,
            autorenew = false,
            tokenIds = listOf("1", "2", uint256Max),
            validator = address(17),
            delegationId = "123456789012345678901234567890",
            periodClaimed = 9,
            boostedBlocks = "10",
            delegationLifecycleStatus = Status.EXITING,
            delegationLifecycleNextCycle = 200,
            delegationLifecycleCycleLength = 50,
            delegationLifecycleForceExit = true,
            delegationLifecycleOrder = 1001,
        )

    fun event(
        n: Int,
        block: Long,
        name: HistoryEventName = HistoryEventName.TRANSFER_VET,
        origin: String? = address(10),
        gasPayer: String? = null,
        to: String? = null,
        from: String? = null,
        owner: String? = null,
        contractAddress: String? = null,
        tokenId: String? = null,
        appId: String? = null,
    ) =
        IndexedHistoryEvent(
            id = sha1(n),
            blockId = hash(block.toInt()),
            blockNumber = block,
            blockTimestamp = block * 10,
            txId = hash(1000 + n),
            eventName = name,
            origin = origin,
            gasPayer = gasPayer,
            to = to,
            from = from,
            owner = owner,
            contractAddress = contractAddress,
            tokenId = tokenId,
            appId = appId,
        )
}
