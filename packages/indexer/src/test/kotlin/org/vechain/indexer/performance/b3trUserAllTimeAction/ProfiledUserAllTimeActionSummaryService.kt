package org.vechain.indexer.performance.b3trUserAllTimeAction

import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionImpactConfig
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummaryService
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.EventUtils

/**
 * Extended UserAllTimeActionSummaryService that profiles EVERY internal method call Tracks
 * performance of:
 * - processEvents (main processing)
 * - save (MongoDB writes)
 * - groupByBlock (group events by block)
 * - groupByReceiver (group by user)
 * - groupByAppId (group by app)
 * - createOrUpdateExisting (create/update records)
 * - resolveExisting (resolve from cache or DB)
 */
class ProfiledUserAllTimeActionSummaryService(
    repository: UserAllTimeActionSummaryRepository,
    archiveService: ArchiveService<UserAllTimeActionSummary>,
    pruner: TargetedPruner<UserAllTimeActionSummary>,
    impactConfig: ActionImpactConfig,
    private val profiler: DetailedProfiler,
) : UserAllTimeActionSummaryService(repository, archiveService, pruner, impactConfig) {

    override fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<UserAllTimeActionSummary>, List<UserAllTimeActionSummary>> {
        return profiler.time("      UserAllTimeActionSummaryService.processEvents") {
            profiler.time("        - assertEventTypes") {
                org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes(
                    events,
                    "B3TR_ActionReward",
                )
            }

            val updatedResult = mutableMapOf<String, UserAllTimeActionSummary>()
            val archiveResult = mutableListOf<UserAllTimeActionSummary>()

            val blockGroups =
                profiler.time("        - groupByBlock") { EventUtils.groupByBlock(events) }

            profiler.time("        - process block groups") {
                blockGroups.forEach { (blockDetails, blockEvents) ->
                    // Process Users
                    val userGroups =
                        profiler.time("          - groupByReceiver") {
                            org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver(
                                blockEvents
                            )
                        }

                    profiler.time("          - process users") {
                        userGroups.forEach { (userId, eventsPerReceiver) ->
                            val recordId = org.vechain.indexer.utils.IdUtils.generateId(userId)
                            val existing =
                                profiler.time("            - resolveExisting") {
                                    resolveExisting(recordId, updatedResult)
                                }
                            val updated =
                                profiler.time("            - createOrUpdateExisting") {
                                    createOrUpdateExisting(
                                        userId,
                                        org.vechain.indexer.b3tr.shared.EntityType.USER,
                                        eventsPerReceiver,
                                        blockDetails,
                                        existing,
                                    )
                                }
                            existing?.let { archiveResult.add(it) }
                            updatedResult[recordId] = updated
                        }
                    }

                    // Process Apps
                    val appGroups =
                        profiler.time("          - groupByAppId") {
                            org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId(
                                blockEvents
                            )
                        }

                    profiler.time("          - process apps") {
                        appGroups.forEach { (appId, eventsPerApp) ->
                            val recordId = org.vechain.indexer.utils.IdUtils.generateId(appId)
                            val existing =
                                profiler.time("            - resolveExisting") {
                                    resolveExisting(recordId, updatedResult)
                                }
                            val updated =
                                profiler.time("            - createOrUpdateExisting") {
                                    createOrUpdateExisting(
                                        appId,
                                        org.vechain.indexer.b3tr.shared.EntityType.APP,
                                        eventsPerApp,
                                        blockDetails,
                                        existing,
                                    )
                                }
                            existing?.let { archiveResult.add(it) }
                            updatedResult[recordId] = updated
                        }
                    }

                    // Process Global
                    val globalId =
                        org.vechain.indexer.utils.IdUtils.generateId(
                            org.vechain.indexer.b3tr.shared.EntityType.GLOBAL.name
                        )
                    val existing =
                        profiler.time("          - resolveExisting (global)") {
                            resolveExisting(globalId, updatedResult)
                        }
                    val updated =
                        profiler.time("          - createOrUpdateExisting (global)") {
                            createOrUpdateExisting(
                                org.vechain.indexer.b3tr.shared.EntityType.GLOBAL.name,
                                org.vechain.indexer.b3tr.shared.EntityType.GLOBAL,
                                blockEvents,
                                blockDetails,
                                existing,
                            )
                        }
                    existing?.let { archiveResult.add(it) }
                    updatedResult[globalId] = updated
                }
            }

            updatedResult.values.toList() to archiveResult
        }
    }

    override fun save(
        updated: List<UserAllTimeActionSummary>,
        existing: List<UserAllTimeActionSummary>,
    ) {
        profiler.time("      UserAllTimeActionSummaryService.save (MongoDB)") {
            super.save(updated, existing)
        }
    }
}
