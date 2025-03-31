package org.vechain.indexer.services

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive
import org.vechain.indexer.repository.NFTBlacklistRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTBlacklistService

@ExtendWith(MockKExtension::class)
internal class NFTBlacklistServiceTest {

    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var repository: NFTBlacklistRepository
    @MockK
    lateinit var nftBlacklistArchiveService: ArchiveService<NFTBlacklist, NFTBlacklistArchive>

    private lateinit var nftBlacklistService: NFTBlacklistService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        nftBlacklistService =
            NFTBlacklistService(
                mongoTemplate = mongoTemplate,
                repository = repository,
                nftBlacklistArchiveService = nftBlacklistArchiveService,
            )
    }

    @Test
    fun `update - should save updated and existing records`() {
        val updated = listOf(NFTBlacklist("contract1", 1, true, "block1", 1, 1))
        val existing = listOf(NFTBlacklist("contract2", 1, false, "block2", 2, 2))

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 1) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if updated is empty`() {
        val updated = emptyList<NFTBlacklist>()
        val existing = listOf(NFTBlacklist("contract2", 1, false, "block2", 2, 2))

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

        verify(exactly = 0) { repository.saveAll(updated) }
        verify(exactly = 1) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if existing is empty`() {
        val updated = listOf(NFTBlacklist("contract1", 1, true, "block1", 1, 1))
        val existing = emptyList<NFTBlacklist>()

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 0) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - should call saveAll with empty lists`() {
        val updated = emptyList<NFTBlacklist>()
        val existing = emptyList<NFTBlacklist>()

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

        verify(exactly = 0) { repository.saveAll(updated) }
        verify(exactly = 0) { nftBlacklistArchiveService.saveAll(existing) }
    }
}
