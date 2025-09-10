package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.response.UserOverview
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils

@Profile("b3tr", "b3tr-actions")
@Service
open class ActionService(
    private val userAllTimeRepo: UserAllTimeActionSummaryRepository,
    private val userDailyRepo: UserDailyActionSummaryRepository,
    private val userRoundRepo: UserRoundActionSummaryRepository,
    private val appAllTimeRepo: AppAllTimeActionSummaryRepository,
    private val appDailyRepo: AppDailyActionSummaryRepository,
    private val appRoundRepo: AppRoundActionSummaryRepository,
) {
    open fun getAllTimeWalletOverview(wallet: Address): UserOverview {
        // Normalize the entity
        val normalizedEntity = HexUtils.normalise(wallet.value)

        // Retrieve the overview from the repository
        val overview = userAllTimeRepo.findByEntity(normalizedEntity)

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userAllTimeRepo.countByTotalRewardAmountGreaterThanAndEntityType(
                    overview.totalRewardAmount,
                    EntityType.USER,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userAllTimeRepo.countByActionsRewardedGreaterThanAndEntityType(
                    overview.actionsRewarded,
                    EntityType.USER,
                ) + 1
        }

        // Fetch uniqueInteractions if the required profiles are active
        val uniqueInteractions: List<String> =
            appAllTimeRepo.findAppIdsByUser(normalizedEntity).map { it.appId }

        // Return UserOverview with the calculated position fields
        return UserOverview(
            wallet = normalizedEntity,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            uniqueXAppInteractions = uniqueInteractions,
            roundId = null,
            date = null,
        )
    }

    open fun getDailyWalletOverview(wallet: Address, date: String): UserOverview {
        // Normalize the entity
        val normalizedEntity = HexUtils.normalise(wallet.value)

        // Retrieve the overview from the repository
        val overview = userDailyRepo.findByEntityAndDate(normalizedEntity, date)
        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userDailyRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
                    overview.totalRewardAmount,
                    EntityType.USER,
                    date,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userDailyRepo.countByActionsRewardedGreaterThanAndEntityTypeAndDate(
                    overview.actionsRewarded,
                    EntityType.USER,
                    date,
                ) + 1
        }

        // Fetch uniqueInteractions if the required profiles are active
        val uniqueInteractions: List<String> =
            appDailyRepo.findAppIdsByUserAndDate(normalizedEntity, date).map { it.appId }

        // Return UserOverview with the calculated position fields
        return UserOverview(
            wallet = normalizedEntity,
            date = date,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            uniqueXAppInteractions = uniqueInteractions,
            roundId = null,
        )
    }

    open fun getRoundWalletOverview(wallet: Address, roundId: Int): UserOverview {
        // Normalize the entity
        val normalizedEntity = HexUtils.normalise(wallet.value)

        // Retrieve the overview from the repository
        val overview = userRoundRepo.findByEntityAndRoundId(normalizedEntity, roundId)

        var rankByActionsRewarded: Long? = null
        var rankByReward: Long? = null

        if (overview != null) {
            // Calculate the position by totalRewardAmount
            rankByReward =
                userRoundRepo.countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
                    overview.totalRewardAmount,
                    EntityType.USER,
                    roundId,
                ) + 1

            // Calculate the position by actionsRewarded
            rankByActionsRewarded =
                userRoundRepo.countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
                    overview.actionsRewarded,
                    EntityType.USER,
                    roundId,
                ) + 1
        }

        // Fetch uniqueInteractions if the required profiles are active
        val uniqueInteractions: List<String> =
            appRoundRepo.findAppIdsByUserAndRoundId(normalizedEntity, roundId).map { it.appId }

        // Return UserOverview with the calculated position fields
        return UserOverview(
            wallet = normalizedEntity,
            roundId = roundId,
            totalRewardAmount = overview?.totalRewardAmount?.toDouble() ?: 0.0,
            actionsRewarded = overview?.actionsRewarded ?: 0,
            totalImpact = overview?.totalImpact,
            rankByReward = rankByReward,
            rankByActionsRewarded = rankByActionsRewarded,
            uniqueXAppInteractions = uniqueInteractions,
            date = null,
        )
    }
}
