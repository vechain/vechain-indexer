package org.vechain.indexer.controller

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.NFTS_PATH
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.utils.HexUtil
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

class NFTControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = NFTS_PATH
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Nested
    inner class GetOwnedNFTs {
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

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!).hasSize(2)
        }

        @Test
        fun `uppercase address should be valid`() {
            val res = mockMvc.get("$baseEndpoint/0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!).hasSize(2)
        }

        @Test
        fun `mixed case should be valid`() {
            val res = mockMvc.get("$baseEndpoint/0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!).hasSize(2)
        }

        @Test
        fun `no prefix should be valid`() {
            val res = mockMvc.get("$baseEndpoint/f077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!).hasSize(2)
        }

        @Test
        fun `get filtered NFTs should return 1 less`() {
            val res =
                mockMvc.get("$baseEndpoint/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?contractAddresses=0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!).hasSize(1)
        }

        @Test
        fun `get filtered by all contract addresses - no pagination search`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress1 = "0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4"
            val contractAddress2 = "0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14"
            val res =
                mockMvc.get(
                    "$baseEndpoint/$owner" +
                            "?contractAddresses=$contractAddress1,$contractAddress2"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!).hasSize(2)
        }

        @Test
        fun `get filtered by all contract addresses - paginated search by size`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress1 = "0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4"
            val contractAddress2 = "0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14"
            val size = 1

            val res =
                mockMvc.get(
                    "$baseEndpoint/$owner" +
                            "?contractAddresses=$contractAddress1,$contractAddress2" +
                            "&size=$size"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expect {
                that(nfts.data!!).hasSize(size)
                that(nfts.data!!.first().owner).isEqualTo(HexUtil.normalise(owner))
                that(nfts.data!!.first().contractAddress).isContainedIn(listOf(contractAddress1, contractAddress2))
            }
        }

        @Test
        fun `get filtered by all contract addresses - paginated search by page & size`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress1 = "0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4"
            val contractAddress2 = "0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14"
            val page = 1
            val size = 1

            val res =
                mockMvc.get(
                    "$baseEndpoint/$owner" +
                            "?contractAddresses=$contractAddress1,$contractAddress2" +
                            "&page=$page" +
                            "&size=$size"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expect {
                that(nfts.data!!).hasSize(size)
                that(nfts.data!!.first().owner).isEqualTo(HexUtil.normalise(owner))
                that(nfts.data!!.first().contractAddress).isContainedIn(listOf(contractAddress1, contractAddress2))
            }
        }

        @Test
        fun `get filtered by all contract addresses - sorted by blockNumber & txId & id`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress1 = "0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4"
            val contractAddress2 = "0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14"
            val page = 0
            val size = Int.MAX_VALUE

            val res =
                mockMvc.get(
                    "$baseEndpoint/$owner" +
                            "?contractAddresses=$contractAddress1,$contractAddress2" +
                            "&page=$page" +
                            "&size=$size"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!)
                .hasSize(2)
                .isSorted(
                    compareByDescending<NFT> { it.blockNumber }
                        .then(compareByDescending<NFT> { it.txId }
                            .then(compareByDescending { it.id })
                        )
                )
        }

        @Test
        fun `get filtered by empty contract addresses`() {
            val res =
                mockMvc.get("$baseEndpoint/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?contractAddresses=")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_TYPE)

            expectThat(nfts.data!!).hasSize(2)
        }

        @Test
        fun `filtered addresses, which are not addresses`() {
            mockMvc.get("$baseEndpoint/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?contractAddresses=address1,address2")
                .andExpect { status { isBadRequest() } }
        }
    }

    @Nested
    inner class GetContractsByNFTOwner {

        @Test
        fun `get contracts by NFT owner - bad owner address`() {
            mockMvc.get("$baseEndpoint/contracts?owner=badAddress")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get contracts by NFT owner - empty owner address`() {
            mockMvc.get("$baseEndpoint/contracts?owner=")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get contracts by NFT owner - valid owner address with NFTs owned`() {
            val res =
                mockMvc.get("$baseEndpoint/contracts?owner=0x0f872421dc479f3c11edd89512731814d0598db5")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(
                    res.response.contentAsString,
                    object : TypeReference<PaginatedResponse<List<String>>>() {})

            expect {
                that(contracts.data!!).hasSize(2)
                that(contracts.data!!).containsExactlyInAnyOrder(
                    "0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14",
                    "0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4"
                )
            }
        }

        @Test
        fun `get contracts by NFT owner - valid owner address with NFTs owned - paginated search with size`() {
            val owner = "0x0f872421dc479f3c11edd89512731814d0598db5"
            val size = 1

            val res =
                mockMvc.get(
                    "$baseEndpoint/contracts" +
                            "?owner=$owner" +
                            "&size=$size"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val contracts =
                objectMapper.readValue(
                    res.response.contentAsString,
                    object : TypeReference<PaginatedResponse<List<String>>>() {})

            expect {
                that(contracts.data!!).hasSize(size)
                that(contracts.data!!.first()).isContainedIn(
                    listOf(
                        "0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14",
                        "0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4"
                    )
                )
            }
        }

        @Test
        fun `get contracts by NFT owner - valid owner address with NFTs owned - paginated search with page & size`() {
            val owner = "0x0f872421dc479f3c11edd89512731814d0598db5"
            val page = 1
            val size = 1

            val res =
                mockMvc.get(
                    "$baseEndpoint/contracts" +
                            "?owner=$owner" +
                            "&page=$page" +
                            "&size=$size"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val contracts =
                objectMapper.readValue(
                    res.response.contentAsString,
                    object : TypeReference<PaginatedResponse<List<String>>>() {})

            expect {
                that(contracts.data!!).hasSize(size)
                that(contracts.data!!.first()).isContainedIn(
                    listOf(
                        "0x91f4afa1cd72ee671ad2bf87ea0c69e464726b14",
                        "0xc2a77d2ad3cdbc62f9af2462ab8fa8534f5997b4"
                    )
                )
            }
        }

        @Test
        fun `get contracts by NFT owner - valid owner address with no NFTs owned`() {
            val res =
                mockMvc.get("$baseEndpoint/contracts?owner=0x0f872421dc479f3c11edd89512731814d0598db4")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(
                    res.response.contentAsString,
                    object : TypeReference<PaginatedResponse<List<String>>>() {})

            expectThat(contracts.data).isNotNull().and { isEmpty() }
        }
    }

}