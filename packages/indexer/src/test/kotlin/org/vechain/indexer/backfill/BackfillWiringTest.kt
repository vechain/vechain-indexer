package org.vechain.indexer.backfill

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.config.postgres.PostgresProperties
import org.vechain.indexer.history.HistoryProcessor
import org.vechain.indexer.history.HistoryService
import org.vechain.indexer.history.HistoryWriteRepository
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.thor.client.ThorClient

/** The processor takes its coordinator from the container, not from a default argument. */
class BackfillWiringTest {

    private val runner =
        ApplicationContextRunner()
            .withConfiguration(
                org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration::class.java
                )
            )
            .withUserConfiguration()
            .withBean(BackfillProperties::class.java)
            .withBean(BackfillState::class.java)
            .withBean(ChainHead::class.java, { ChainHead(mockk<ThorClient>()) })
            .withBean(BackfillCoordinatorFactory::class.java)
            .withBean(
                PostgresProperties::class.java,
                { PostgresProperties("jdbc:postgresql://localhost:5432/none", "u", "p") },
            )
            .withBean(HistoryService::class.java, { mockk<HistoryService>(relaxed = true) })
            .withBean(
                HistoryWriteRepository::class.java,
                { mockk<HistoryWriteRepository>(relaxed = true) },
            )
            .withBean(
                IndexerStateRepository::class.java,
                { mockk<IndexerStateRepository>(relaxed = true) },
            )
            .withBean(CheckpointProperties::class.java)
            .withBean(InlineVersioningProperties::class.java)
            .withBean(ProcessorMetrics::class.java, { ProcessorMetrics(SimpleMeterRegistry()) })
            .withBean(HistoryProcessor::class.java)

    // The field is private and optional: unresolved, Spring leaves it null and nothing else says
    // so.
    private fun coordinatorOf(processor: HistoryProcessor) =
        PostgresProcessor::class
            .java
            .getDeclaredField("backfill")
            .apply { isAccessible = true }
            .get(processor)

    @Test
    fun `the history processor is built with a coordinator and the bound threshold`() {
        runner
            .withPropertyValues(
                "spring.profiles.active=history",
                "indexer.backfill.enter-behind-blocks=123456",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(BackfillProperties::class.java).enterBehindBlocks)
                    .isEqualTo(123_456L)
                assertThat(coordinatorOf(context.getBean(HistoryProcessor::class.java))).isNotNull()
            }
    }
}
