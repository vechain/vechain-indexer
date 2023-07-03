package org.vechain.indexer.service

import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.MockBeans
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.mock.mockito.SpyBeans
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.vechain.indexer.NFTEventIndexer
import org.vechain.indexer.config.MongoDbConfig
import org.vechain.indexer.repository.ArchiveRepository
import org.vechain.indexer.repository.NFTRepository

@RunWith(SpringRunner::class)
@SpringBootTest
@ContextConfiguration(
    classes = [NFTEventIndexer::class, NFTRepository::class, MongoDbConfig::class]
)
@MockBeans(MockBean(ArchiveRepository::class))
@SpyBeans(SpyBean(NFTEventIndexer::class))
class ArchiveServiceIntegrationTest {
    @Autowired lateinit var nftEventIndexer: NFTEventIndexer
    @Autowired lateinit var archiveRepository: ArchiveRepository

    @Test fun `can index NFT events`() {}
}
