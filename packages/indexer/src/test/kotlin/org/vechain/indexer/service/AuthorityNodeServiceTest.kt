package org.vechain.indexer.service

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.model.AuthorityNode
import org.vechain.indexer.model.rest.ExecuteCodeResponse
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils

class AuthorityNodeServiceTest {
    @MockK lateinit var authorityNodeRepository: AuthorityNodeRepository
    @MockK lateinit var thorService: ThorService
    private lateinit var authorityNodeService: AuthorityNodeService

    private fun createDummyAddress(suffix: String) = "0x${"1".repeat(39)}$suffix"

    private fun createDummyResponse() =
        "0x${"0".repeat(63)}1${"0".repeat(24)}${endorser.substring(2)}${"0".repeat(63)}123${"0".repeat(63)}1"

    private val node1 = createDummyAddress("1")
    private val node2 = createDummyAddress("2")
    private val endorser = createDummyAddress("3")
    private val successfulResponse = createDummyResponse()

    fun dummyHexData(prefix: String = "0x", length: Int = 64): String {
        val hexChars = "0123456789abcdef"
        val randomHex = (1..length).map { hexChars.random() }.joinToString("")
        return prefix + randomHex
    }

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(ContractUtils) // Mock the ContractUtils object

        authorityNodeService =
            AuthorityNodeService(
                authorityNodeRepository,
                thorService,
                "0x0000000000000000000000417574686f72697479",
            )
    }

    @Test
    fun `syncEndorsersForAllNodes should not run when no nodes are in the database`() {
        every { authorityNodeRepository.findAll() } returns emptyList()

        authorityNodeService.syncEndorsersForAllNodes()

        verify(exactly = 0) { thorService.executeReadOnlyCode(any()) }
        verify(exactly = 0) { authorityNodeRepository.saveAll(any<List<AuthorityNode>>()) }
    }

    @Test
    fun `syncEndorsersForAllNodes should update nodes with contract data`() {
        // Arrange
        val node1 = AuthorityNode(node1, 1, "block1", 100)
        val node2 = AuthorityNode(node2, 2, "block2", 200)

        every { authorityNodeRepository.findAll() } returns listOf(node1, node2)

        // Mock ContractUtils.createClause to return fake clauses
        every { ContractUtils.createClause(any(), any(), any()) } returns
            Clause(
                to = "0x0000000000000000000000417574686f72697479",
                data = dummyHexData(),
                value = "0x0",
            )

        val successResponse =
            ExecuteCodeResponse(
                data = successfulResponse,
                events = emptyList(),
                transfers = emptyList(),
                gasUsed = 0,
                reverted = false,
                vmError = "",
            )

        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(successResponse, successResponse)
        every { authorityNodeRepository.saveAll(any<List<AuthorityNode>>()) } returns listOf()

        // Act
        authorityNodeService.syncEndorsersForAllNodes()

        // Assert
        verify { thorService.executeReadOnlyCode(any()) }
        verify { authorityNodeRepository.saveAll(any<List<AuthorityNode>>()) }
    }
}
