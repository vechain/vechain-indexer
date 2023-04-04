package org.vechain.indexer.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest

class NFTControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = "/api/v1/nfts/"
    }

    @Autowired
    lateinit var mockMvc: MockMvc


    @Test
    fun `get transactions for bad address should return BAD_REQUEST`() {
        mockMvc.get("$baseEndpoint/badAddress")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `valid address should return OKAY`() {
        val res = mockMvc.get("$baseEndpoint/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
            .andExpect { status { isOk() } }
            .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 2)
    }

    @Test
    fun `uppercase address should be valid`() {
        val res = mockMvc.get("$baseEndpoint/0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa")
            .andExpect { status { isOk() } }
            .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 2)
    }

    @Test
    fun `mixed case should be valid`() {
        val res = mockMvc.get("$baseEndpoint/0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa")
            .andExpect { status { isOk() } }
            .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 2)
    }

    @Test
    fun `no prefix should be valid`() {
        val res = mockMvc.get("$baseEndpoint/f077b491b355E64048cE21E3A6Fc4751eEeA77fa")
            .andExpect { status { isOk() } }
            .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 2)
    }

    @Test
    fun `get filtered NFTs should return 1 less`() {
        val res =
            mockMvc.get("$baseEndpoint/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?contractAddresses=0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4")
                .andExpect { status { isOk() } }
                .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 1)
    }

    @Test
    fun `get filtered by all contract addresses`() {
        val res =
            mockMvc.get("$baseEndpoint/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?contractAddresses=0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4,0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14")
                .andExpect { status { isOk() } }
                .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 2)
    }


    @Test
    fun `get filtered by empty contract addresses`() {
        val res =
            mockMvc.get("$baseEndpoint/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?contractAddresses=")
                .andExpect { status { isOk() } }
                .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 2)
    }
}