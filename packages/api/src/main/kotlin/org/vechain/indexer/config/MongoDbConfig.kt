package org.vechain.indexer.config

import java.math.BigDecimal
import org.bson.types.Decimal128
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@ReadingConverter
class StringToDecimal128Converter : Converter<String, Decimal128> {
    override fun convert(source: String): Decimal128 =
        try {
            Decimal128(BigDecimal(source))
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Cannot convert String [$source] to Decimal128: malformed numeric value",
                e,
            )
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException(
                "Cannot convert String [$source] to Decimal128: value out of range",
                e,
            )
        }
}

@Configuration
@EnableMongoRepositories(basePackages = ["org.vechain.indexer"])
open class MongoDbConfig {

    @Bean
    open fun customConversions(): MongoCustomConversions =
        MongoCustomConversions(listOf(StringToDecimal128Converter()))

    @Bean
    open fun mongoTemplate(
        dbFactory: MongoDatabaseFactory,
        converter: MappingMongoConverter,
    ): MongoTemplate = FilteringMongoTemplate(dbFactory, converter)
}
