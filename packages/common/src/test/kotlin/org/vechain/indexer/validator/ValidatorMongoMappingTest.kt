package org.vechain.indexer.validator

import java.math.BigDecimal
import org.bson.Document
import org.bson.types.Decimal128
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.FilteringMongoTemplate
import org.vechain.indexer.config.FilteringMongoTemplateTestConfig
import org.vechain.indexer.config.TestApplication
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.token.TokenLevelDecimalValues
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@DataMongoTest
@ActiveProfiles("test")
@ContextConfiguration(classes = [TestApplication::class, FilteringMongoTemplateTestConfig::class])
internal class ValidatorMongoMappingTest {

    @Autowired private lateinit var template: FilteringMongoTemplate

    @BeforeEach
    fun setUp() {
        template.dropCollection(IndexerNames.VALIDATOR.COLLECTION)
    }

    @Test
    fun `validator nft yield maps are stored as Decimal128`() {
        val validator =
            Validator(
                id = "0xvalidator",
                blockId = "0xblock",
                blockNumber = 1L,
                blockTimestamp = 1_000L,
                nftYieldsIfDelegatedNextCycle =
                    TokenLevelDecimalValues.fromMap(
                        mapOf(
                            TokenLevel.Dawn to BigDecimal("1.25"),
                            TokenLevel.Strength to BigDecimal("2.50"),
                        )
                    ),
                nftYields =
                    TokenLevelDecimalValues.fromMap(
                        mapOf(
                            TokenLevel.Lightning to BigDecimal("1.75"),
                            TokenLevel.VeThorX to BigDecimal("3.50"),
                        )
                    ),
                version = 1,
            )

        template.insert(validator)

        val stored =
            template
                .getCollection(IndexerNames.VALIDATOR.COLLECTION)
                .find(Document("_id", validator.id))
                .first()

        expectThat(stored).isNotNull()

        val projectedYields = stored!!.get("nftYieldsIfDelegatedNextCycle", Document::class.java)
        expectThat(projectedYields).isNotNull()
        expectThat(projectedYields!!.get("Dawn")).isA<Decimal128>()
        expectThat(projectedYields.get("Strength")).isA<Decimal128>()

        val currentYields = stored.get("nftYields", Document::class.java)
        expectThat(currentYields).isNotNull()
        expectThat(currentYields!!.get("Lightning")).isA<Decimal128>()
        expectThat(currentYields.get("VeThorX")).isA<Decimal128>()

        val reloaded = template.findById(validator.id, Validator::class.java)
        expectThat(reloaded).isNotNull()
        expectThat(reloaded!!.nftYieldsIfDelegatedNextCycle?.get(TokenLevel.Dawn))
            .isEqualTo(BigDecimal("1.25"))
        expectThat(reloaded.nftYieldsIfDelegatedNextCycle?.get(TokenLevel.Strength))
            .isEqualTo(BigDecimal("2.50"))
        expectThat(reloaded.nftYields?.get(TokenLevel.Lightning)).isEqualTo(BigDecimal("1.75"))
        expectThat(reloaded.nftYields?.get(TokenLevel.VeThorX)).isEqualTo(BigDecimal("3.50"))
    }
}
