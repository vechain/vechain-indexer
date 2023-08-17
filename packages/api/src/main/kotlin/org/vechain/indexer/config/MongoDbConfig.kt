package org.vechain.indexer.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories(basePackages = ["org.vechain.indexer.repository"])
open class MongoDbConfig(
    @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
    @Value("\${spring.data.mongodb.username}") private val mongoUser: String,
    @Value("\${spring.data.mongodb.password}") private val mongoPassword: String,
    @Value("\${spring.data.mongodb.authentication-database}") private val authDb: String
) {

    private val connectionString: ConnectionString = ConnectionString(mongoUri)

    @Bean
    @Primary
    open fun mongoClient(): MongoClient {

        val settings =
            MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .credential(
                    MongoCredential.createCredential(mongoUser, authDb, mongoPassword.toCharArray())
                )
                .build()
        return MongoClients.create(settings)
    }

    @Bean
    @Primary
    open fun mongoTemplate(mongoClient: MongoClient): MongoTemplate {

        val database =
            connectionString.database
                ?: throw IllegalArgumentException("MongoDB URI must contain database name")

        return MongoTemplate(mongoClient, database)
    }
}
