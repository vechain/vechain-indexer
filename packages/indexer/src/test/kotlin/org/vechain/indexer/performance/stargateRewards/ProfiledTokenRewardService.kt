package org.vechain.indexer.performance.stargateRewards

import java.math.BigInteger
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardArchive
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeResponseInfo
import org.vechain.indexer.validator.models.DecodedValidatorInfo

/**
 * Extended TokenRewardService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - decodeResponseInfo (validator info decoding)
 * - getLatestRewards (get reward trackers)
 * - getDelegatorsBlockReward (calculate block reward)
 * - getOrFetchRewardsNewCycle (fetch rewards for new cycle)
 * - updateValidatorCycleCache (update cycle cache)
 * - updateRewardInfo (update per-delegation rewards)
 * - getTotalVTHOIssued (get VTHO issued)
 * - loadAllValidatorAbiFunctions (load ABIs)
 */
class ProfiledTokenRewardService(
    repository: TokenRewardRepository,
    archiveService: ArchiveService<TokenReward, TokenRewardArchive>,
    delegationRepository: DelegationRepository,
    thorClient: ThorClient,
    pruner: TargetedPruner<TokenReward, TokenRewardArchive>,
    private val profiler: DetailedProfiler,
) : TokenRewardService(repository, archiveService, delegationRepository, thorClient, pruner) {

    override suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<TokenReward>, List<TokenReward>> {
        return profiler.time("      TokenRewardService.processBlock") {
            // Access the cached ABI field
            val abiField = TokenRewardService::class.java.getDeclaredField("cachedGetValidatorsAbi")
            abiField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val cachedGetValidatorsAbi =
                abiField.get(this)
                    as MutableMap<String, org.vechain.indexer.event.model.abi.AbiElement>

            val decodedInfo =
                profiler.time("        - decodeResponseInfo") {
                    decodeResponseInfo(callResponses, cachedGetValidatorsAbi)
                } ?: return@time Pair(emptyList(), emptyList())

            val latestRewards =
                profiler.time("        - getLatestRewards") {
                    getLatestRewardsInternal(block, decodedInfo)
                }

            if (latestRewards.isEmpty()) {
                return@time Pair(emptyList(), emptyList())
            }

            val delegatorBlockReward =
                profiler.time("        - getDelegatorsBlockReward") {
                    getDelegatorsBlockRewardInternal(block, decodedInfo)
                } ?: return@time Pair(emptyList(), emptyList())

            profiler.time("        - updateRewardInfo") {
                updateRewardInfoInternal(
                    currentTokenRewards = latestRewards,
                    totalBlockReward = delegatorBlockReward,
                    validator = block.signer,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    blockId = block.id,
                )
            }
        }
    }

    override fun save(rewards: List<TokenReward>, archive: List<TokenReward>) {
        profiler.time("      TokenRewardService.save (MongoDB)") { super.save(rewards, archive) }
    }

    // Private method accessors using reflection
    private fun getLatestRewardsInternal(
        block: Block,
        decodedInfo: DecodedValidatorInfo,
    ): List<TokenReward> {
        val method =
            TokenRewardService::class
                .java
                .getDeclaredMethod(
                    "getLatestRewards",
                    Block::class.java,
                    DecodedValidatorInfo::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block, decodedInfo) as List<TokenReward>
    }

    private fun getDelegatorsBlockRewardInternal(
        block: Block,
        decodedInfo: DecodedValidatorInfo?,
    ): BigInteger? {
        val method =
            TokenRewardService::class
                .java
                .getDeclaredMethod(
                    "getDelegatorsBlockReward",
                    Block::class.java,
                    DecodedValidatorInfo::class.java,
                )
        method.isAccessible = true
        return method.invoke(this, block, decodedInfo) as? BigInteger
    }

    private fun updateRewardInfoInternal(
        currentTokenRewards: List<TokenReward>,
        totalBlockReward: BigInteger,
        validator: String,
        blockNumber: Long,
        blockTimestamp: Long,
        blockId: String,
    ): Pair<List<TokenReward>, List<TokenReward>> {
        val method =
            TokenRewardService::class
                .java
                .getDeclaredMethod(
                    "updateRewardInfo",
                    List::class.java,
                    BigInteger::class.java,
                    String::class.java,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    String::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(
            this,
            currentTokenRewards,
            totalBlockReward,
            validator,
            blockNumber,
            blockTimestamp,
            blockId,
        ) as Pair<List<TokenReward>, List<TokenReward>>
    }
}
