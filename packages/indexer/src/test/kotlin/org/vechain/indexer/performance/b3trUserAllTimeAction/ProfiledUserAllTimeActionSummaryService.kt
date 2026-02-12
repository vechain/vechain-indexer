package org.vechain.indexer.performance.b3trUserAllTimeAction

import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionImpactConfig
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummaryArchive
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
 * - accumulator.resolve (resolve from cache or DB)
 */
class ProfiledUserAllTimeActionSummaryService(
    private val repository: UserAllTimeActionSummaryRepository,
    archiveService: ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
    pruner: TargetedPruner<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
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

            val accumulator =
                VersionedDocumentAccumulator<UserAllTimeActionSummary>(repository::findByIdOrNull)

            val blockGroups =
                profiler.time("        - groupByBlock") { EventUtils.groupByBlock(events) }

            profiler.time("        - process block groups") {
                blockGroups.forEach { (blockDetails, blockEvents) ->
                    accumulator.startBlock()
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
                            val (existing, nextVersion) =
                                profiler.time("            - accumulator.resolve") {
                                    accumulator.resolve(recordId)
                                }
                            val updated =
                                profiler.time("            - createOrUpdateExisting") {
                                    createOrUpdateExisting(
                                        userId,
                                        org.vechain.indexer.b3tr.shared.EntityType.USER,
                                        eventsPerReceiver,
                                        blockDetails,
                                        existing,
                                        version = nextVersion,
                                    )
                                }
                            accumulator.put(recordId, existing, updated)
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
                            val (existing, nextVersion) =
                                profiler.time("            - accumulator.resolve") {
                                    accumulator.resolve(recordId)
                                }
                            val updated =
                                profiler.time("            - createOrUpdateExisting") {
                                    createOrUpdateExisting(
                                        appId,
                                        org.vechain.indexer.b3tr.shared.EntityType.APP,
                                        eventsPerApp,
                                        blockDetails,
                                        existing,
                                        version = nextVersion,
                                    )
                                }
                            accumulator.put(recordId, existing, updated)
                        }
                    }

                    // Process Global
                    val globalId =
                        org.vechain.indexer.utils.IdUtils.generateId(
                            org.vechain.indexer.b3tr.shared.EntityType.GLOBAL.name
                        )
                    val (existing, nextVersion) =
                        profiler.time("          - accumulator.resolve (global)") {
                            accumulator.resolve(globalId)
                        }
                    val updated =
                        profiler.time("          - createOrUpdateExisting (global)") {
                            createOrUpdateExisting(
                                org.vechain.indexer.b3tr.shared.EntityType.GLOBAL.name,
                                org.vechain.indexer.b3tr.shared.EntityType.GLOBAL,
                                blockEvents,
                                blockDetails,
                                existing,
                                version = nextVersion,
                            )
                        }
                    accumulator.put(globalId, existing, updated)
                }
            }

            accumulator.results()
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
