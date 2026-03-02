package org.vechain.indexer

import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.thor.model.BlockIdentifier

abstract class BaseProcessor(
    private val repository: BaseIndexedRepository<*, *>,
    private val indexerName: String,
    protected val checkpointService: CheckpointService,
    protected val collectionName: String,
    private val processorMetrics: ProcessorMetrics,
) : IndexerProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private var lastProcessedBlock: Long = -1L
    private var lastIterationEnd: TimeMark? = null

    abstract suspend fun processEntry(entry: IndexingResult)

    protected fun resetProcessingState() {
        lastProcessedBlock = -1L
        lastIterationEnd = null
    }

    override suspend fun process(entry: IndexingResult) {
        val start = TimeSource.Monotonic.markNow()
        try {
            processEntry(entry)
            checkpointService.trySaveCheckpoint(collectionName, entry.latestBlockNumber())
            processorMetrics.incrementEventsCounter(indexerName, entry.events().size.toDouble())
        } finally {
            val duration = start.elapsedNow()
            val currentBlock = entry.latestBlockNumber()
            val blocksInEntry =
                when (entry) {
                    is IndexingResult.BlockResult -> 1
                    is IndexingResult.LogResult -> {
                        if (lastProcessedBlock >= 0 && currentBlock > lastProcessedBlock) {
                            (currentBlock - lastProcessedBlock).toInt()
                        } else {
                            1
                        }
                    }
                }.coerceAtLeast(1)
            lastProcessedBlock = currentBlock

            val perBlockDuration = duration / blocksInEntry
            repeat(blocksInEntry) {
                processorMetrics.observeProcessingDuration(indexerName, perBlockDuration)
            }

            lastIterationEnd?.let { previousEnd ->
                val cycleTime = previousEnd.elapsedNow()
                val perBlockCycleTime = cycleTime / blocksInEntry
                repeat(blocksInEntry) {
                    processorMetrics.observeBlockCycleTime(indexerName, perBlockCycleTime)
                }
            }
            lastIterationEnd = TimeSource.Monotonic.markNow()
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        val checkpoint = checkpointService.getCheckpoint(collectionName)
        val latestRecord =
            try {
                repository.getLatestRecord()?.let {
                    BlockIdentifier(number = it.blockNumber, id = it.blockId)
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to get latest record for {} (collection: {})",
                    indexerName,
                    collectionName,
                    e,
                )
                throw e
            }
        return listOfNotNull(latestRecord, checkpoint).maxByOrNull { it.number }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        resetProcessingState()
        checkpointService.saveCheckpoint(collectionName, blockNumber - 1)
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
    }
}
