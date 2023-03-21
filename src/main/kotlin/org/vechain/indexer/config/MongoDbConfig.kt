package org.vechain.indexer.config

//import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
//import com.mongodb.MongoCredential
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
//import com.mongodb.connection.SslSettings
//import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
//import org.bouncycastle.cert.X509CertificateHolder
//import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
//import org.bouncycastle.openssl.PEMParser
//import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
//import java.io.File
//import java.io.StringReader
//import java.math.BigInteger
//import java.security.KeyStore
//import java.security.SecureRandom
//import javax.net.ssl.KeyManagerFactory
//import javax.net.ssl.SSLContext

@Configuration
@EnableMongoRepositories(basePackages = ["org.vechain.indexer.repos"])
class MongoDbConfig(
        @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
        @Value("\${spring.data.mongodb.database}") private val mongoDbName: String,
//        @Value("#{'\${MONGODB_CLIENT_CERT}'}") private val clientCertificateEnv: String,
//        @Value("#{'\${MONGODB_CLIENT_CERT_KEY}'}") private val clientCertKeyEnv: String,
//        @Value("#{'\${MONGODB_CA_CERT}'}") private val mongoCaCerts: String,
//        @Value("#{'\${MONGODB_CLIENT_COMMON_NAME}'}") private val username: String,
        @Value("\${server.ssl.trusted-certificates}") private val trustedCerts: String,
        private val env: Environment
) {

    @Bean
    fun mongoClient(): MongoClient {
        return when {
//            env.acceptsProfiles(Profiles.of("mongo-auth-x509")) -> MongoClients.create(this.getTlsSettings())
            env.acceptsProfiles(Profiles.of("mongo-auth-password")) -> MongoClients.create(mongoUri)
            else -> throw RuntimeException("A spring profile of either 'mongo-auth-x509' or 'mongo-auth-password' must be set")
        }
    }

    @Bean
    fun mongoDbFactory(mongoClient: MongoClient): MongoDatabaseFactory {
        return SimpleMongoClientDatabaseFactory(mongoClient, mongoDbName)
    }

    @Bean
    fun mongoTemplate(mongoDbFactory: MongoDatabaseFactory): MongoTemplate {
        return MongoTemplate(mongoDbFactory)
    }

    @Bean
    fun transactionManager(mongoDbFactory: MongoDatabaseFactory): MongoTransactionManager {
        return MongoTransactionManager(mongoDbFactory)
    }

    @Bean("codecRegistry")
    fun codecRegistry(): CodecRegistry {
        val pojoCodecRegistry: CodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build())
        return fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry)
    }

//    fun getTlsSettings(): MongoClientSettings {
//
//        File(trustedCerts).appendText(mongoCaCerts)
//
//        val mongoSslContext: SSLContext = getMongoSslContext()
//        val credential = MongoCredential.createMongoX509Credential(username)
//
//        return MongoClientSettings.builder()
//                .applyConnectionString(ConnectionString(mongoUri))
//                .applyToSslSettings { builder: SslSettings.Builder -> builder.context(mongoSslContext) }
//                .credential(credential)
//                .build()
//    }

//    fun getMongoSslContext(): SSLContext {
//
//        //Create X509Certificate
//        val certPemParser = PEMParser(StringReader(clientCertificateEnv))
//        val certificateHolder = certPemParser.readObject() as X509CertificateHolder
//        val certificate = JcaX509CertificateConverter().getCertificate(certificateHolder)
//
//        //Create client PrivateKey
//        val keyPemParser = PEMParser(StringReader(clientCertKeyEnv))
//        val privateKeyInfo = keyPemParser.readObject() as PrivateKeyInfo
//        val key = JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
//
//        //Generate random Password
//        val password = BigInteger(130, SecureRandom()).toString(32)
//
//        //Create the KeyStore
//        val keyStore = KeyStore.getInstance("JKS")
//        keyStore.load(null)
//        keyStore.setCertificateEntry("vechainUser-cert", certificate)
//        keyStore.setKeyEntry("vechainUser-key", key, password.toCharArray(), arrayOf(certificate))
//
//        val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
//        keyManagerFactory.init(keyStore, password.toCharArray())
//
//        val sslContext = SSLContext.getInstance("TLSv1.2")
//        sslContext.init(keyManagerFactory.keyManagers, null, null)
//
//        return sslContext
//    }
}