package org.vechain.indexer.controller

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.NFTS_PATH
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedNft
import org.vechain.indexer.model.rest.PAGE_SIZE_LIMIT
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.utils.HexUtils
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

class NFTControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = NFTS_PATH
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Nested
    inner class GetOwnedNFTs {
        @Test
        fun `get owned nfts for bad address should return BAD_REQUEST`() {
            mockMvc.get("$baseEndpoint?address=badAddress").andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get owned nfts with over the limit page size should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc.get("$baseEndpoint?address=$address&size=$size").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get owned nfts with the zero address should return BAD REQUEST`() {
            val address = Address.ZERO_ADDRESS

            mockMvc.get("$baseEndpoint?address=$address").andExpect { status { isBadRequest() } }
        }

        @Test
        fun `valid address should return OKAY`() {
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa&size=$PAGE_SIZE_LIMIT"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(102)
        }

        @Test
        fun `uppercase address should be valid`() {
            val res =
                mockMvc
                    .get("$baseEndpoint?address=0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(20)
        }

        @Test
        fun `mixed case should be valid`() {
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa&size=$PAGE_SIZE_LIMIT"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(102)
        }

        @Test
        fun `no prefix should be valid`() {
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=f077b491b355E64048cE21E3A6Fc4751eEeA77fa&size=$PAGE_SIZE_LIMIT"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(102)
        }

        @Test
        fun `get owned NFTs - hasNext true when remaining results`() {
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=f077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                            "&page=1" +
                            "&size=50"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(50)
            expectThat(nfts.pagination.hasNext).isTrue()
        }

        @Test
        fun `get owned NFTs - hasNext false when no remaining results`() {
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=f077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                            "&page=2" +
                            "&size=50"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(2)
            expectThat(nfts.pagination.hasNext).isFalse()
        }

        @Test
        fun `uppercase contract address should be valid`() {
            val owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val contractAddress = "0x" + "08f30373569af024d15eb47fd477a35db929eaac".uppercase()

            val res =
                mockMvc
                    .get("$baseEndpoint?address=$owner&contractAddress=$contractAddress")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(20)
        }

        @Test
        fun `mixed case contract address should be valid`() {
            val owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val contractAddress = "0x08f30373569AF024d15eb47FD477a35db929eaAc"

            val res =
                mockMvc
                    .get("$baseEndpoint?address=$owner&contractAddress=$contractAddress")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(20)
        }

        @Test
        fun `no prefix contract address should be valid`() {
            val owner = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val contractAddress = "08f30373569af024d15eb47fd477a35db929eaac"

            val res =
                mockMvc
                    .get("$baseEndpoint?address=$owner&contractAddress=$contractAddress")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(20)
        }

        @Test
        fun `get filtered NFTs should return less NFTs`() {
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                            "&contractAddress=0xb44111d908ad0af0949a20a130429f92a4cc0dbf&size=$PAGE_SIZE_LIMIT"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(51)
        }

        @Test
        fun `get filtered by contract address - no pagination search`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=$owner&contractAddress=$contractAddress&size=$PAGE_SIZE_LIMIT"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(51)
        }

        @Test
        fun `get filtered by contract address - paginated search by size`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val size = 1

            val res =
                mockMvc
                    .get("$baseEndpoint?address=$owner&contractAddress=$contractAddress&size=$size")
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expect {
                that(nfts.data).hasSize(size)
                that(nfts.data.first().owner).isEqualTo(HexUtils.normalise(owner))
                that(nfts.data.first().contractAddress).isEqualTo(contractAddress)
            }
        }

        @Test
        fun `get filtered by contract address - paginated search by page & size`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val page = 0
            val size = 1

            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=$owner&contractAddress=$contractAddress&page=$page&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expect {
                that(nfts.data).hasSize(size)
                that(nfts.data.first().owner).isEqualTo(HexUtils.normalise(owner))
                that(nfts.data.first().contractAddress).isEqualTo(contractAddress)
            }
        }

        @Test
        fun `get filtered by all contract addresses - sorted by blockNumber & txId & id`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val page = 0
            val size = PAGE_SIZE_LIMIT

            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=$owner&contractAddress=$contractAddress&page=$page&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data)
                .hasSize(51)
                .isSorted(
                    compareByDescending<IndexedNft> { it.blockNumber }
                        .then(
                            compareByDescending<IndexedNft> { it.txId }
                                .then(compareByDescending { it.id })
                        )
                )
        }

        @Test
        fun `get filtered by empty contract address`() {
            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa&size=$PAGE_SIZE_LIMIT&contractAddress="
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(102)
        }

        @Test
        fun `get filtered by all contract addresses - with pagination & pagination detail & sorted by blockNumber & txId & id`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val page = 0
            val size = 20

            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=$owner&contractAddress=$contractAddress&page=$page&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expect {
                that(nfts.data)
                    .hasSize(20)
                    .isSorted(
                        compareByDescending<IndexedNft> { it.blockNumber }
                            .then(
                                compareByDescending<IndexedNft> { it.txId }
                                    .then(compareByDescending { it.id })
                            )
                    )
                that(nfts.pagination.hasNext).isTrue()
            }
        }

        @Test
        fun `Invalid contract address`() {
            mockMvc
                .get(
                    "$baseEndpoint?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa&contractAddress=address1"
                )
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get owned NFT by contract addresses & tokenId - found`() {
            val owner = "0x0f872421dc479f3c11edd89512731814d0598db5"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val tokenId = "5"

            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=$owner&contractAddress=$contractAddress&tokenId=$tokenId"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(1)
            val nft = nfts.data.first()

            expect {
                that(nft.id).isEqualTo("d0e5a87a3a0cf30069547c8cfa5178c01c223273")
                that(nft.tokenId).isEqualTo(tokenId)
                that(nft.contractAddress).isEqualTo(contractAddress)
                that(nft.owner).isEqualTo(owner)
                that(nft.txId)
                    .isEqualTo("0x7656cbb0874648e3ccc81126c967455623c570e19827875b8b1455862faf1178")
                that(nft.blockNumber).isEqualTo(17)
                that(nft.blockId)
                    .isEqualTo("0x00000011b347fd82354d66867d0e9a9a207eb2fbac21cbe85388878193935984")
            }
        }

        @Test
        fun `get owned NFT by contract addresses & tokenId - not found`() {
            val owner = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val tokenId = "5"

            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=$owner&contractAddress=$contractAddress&tokenId=$tokenId"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).isEmpty()
        }

        @Test
        fun `get owned NFTs by contract addresses & tokenId - empty tokenId not taken into account`() {
            val owner = "0xf370940abdbd2583bc80bfc19d19bc216c88ccf0"
            val contractAddress = "0xb44111d908ad0af0949a20a130429f92a4cc0dbf"
            val tokenId = ""

            val res =
                mockMvc
                    .get(
                        "$baseEndpoint?address=$owner&contractAddress=$contractAddress&tokenId=$tokenId"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()
            val nfts = objectMapper.readValue(res.response.contentAsString, PAGINATED_NFTS_TYPES)

            expectThat(nfts.data).hasSize(2).map(IndexedNft::owner).all { isEqualTo(owner) }
        }
    }

    @Nested
    inner class GetContractsByNftOwner {

        @Test
        fun `get contracts by NFT owner - bad owner address`() {
            mockMvc.get("$baseEndpoint/contracts?owner=badAddress").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get contracts with over the limit page size should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc.get("$baseEndpoint/contracts?owner=$address&size=$size").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get contracts with the zero address should return BAD REQUEST`() {
            val address = Address.ZERO_ADDRESS

            mockMvc.get("$baseEndpoint/contracts?owner=$address").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get contracts by NFT owner - empty owner address`() {
            mockMvc.get("$baseEndpoint/contracts?owner=").andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get contracts by NFT owner - valid owner address with NFTs owned`() {
            val res =
                mockMvc
                    .get("$baseEndpoint/contracts?owner=0x0f872421dc479f3c11edd89512731814d0598db5")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_CONTRACTS_TYPE)

            expect {
                that(contracts.data)
                    .hasSize(2)
                    .containsExactly(
                        "0x08f30373569af024d15eb47fd477a35db929eaac",
                        "0xb44111d908ad0af0949a20a130429f92a4cc0dbf",
                    )
            }
        }

        @Test
        fun `get contracts by NFT owner - with pagination & pagination detail`() {
            val owner = "0x0f872421dc479f3c11edd89512731814d0598db5"
            val page = 0
            val size = 20
            val res =
                mockMvc
                    .get("$baseEndpoint/contracts?owner=$owner&page=$page&size=$size")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_CONTRACTS_TYPE)

            expect {
                that(contracts.data)
                    .hasSize(2)
                    .containsExactly(
                        "0x08f30373569af024d15eb47fd477a35db929eaac",
                        "0xb44111d908ad0af0949a20a130429f92a4cc0dbf",
                    )
            }
        }

        @Test
        fun `get contracts for NFT owner - valid owner address with multiple NFTS - ensure no duplicate contract addresses`() {

            val res =
                mockMvc
                    .get("$baseEndpoint/contracts?owner=0xf370940abdbd2583bc80bfc19d19bc216c88ccf0")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_CONTRACTS_TYPE)

            expect {
                that(contracts.data)
                    .hasSize(2)
                    .containsExactly(
                        "0x08f30373569af024d15eb47fd477a35db929eaac",
                        "0xb44111d908ad0af0949a20a130429f92a4cc0dbf",
                    )
            }
        }

        @Test
        fun `get contracts by NFT owner - with pagination & pagination detail - distinct partial results`() {
            val owner = "0xf370940abdbd2583bc80bfc19d19bc216c88ccf0"
            val page1 = 0
            val page2 = 1
            val size = 1

            val res1 =
                mockMvc
                    .get("$baseEndpoint/contracts?owner=$owner&page=$page1&size=$size")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val res2 =
                mockMvc
                    .get("$baseEndpoint/contracts?owner=$owner&page=$page2&size=$size")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts1 =
                objectMapper.readValue(res1.response.contentAsString, PAGINATED_NFT_CONTRACTS_TYPE)

            val contracts2 =
                objectMapper.readValue(
                    res2.response.contentAsString,
                    object : TypeReference<PaginatedResponse<String>>() {},
                )

            expect {
                // page 0 results
                that(contracts1.data)
                    .hasSize(size)
                    .containsExactly("0x08f30373569af024d15eb47fd477a35db929eaac")
                that(contracts1.pagination.hasNext).isTrue()

                // page 1 results
                that(contracts2.data)
                    .hasSize(size)
                    .containsExactly("0xb44111d908ad0af0949a20a130429f92a4cc0dbf")
                that(contracts2.pagination.hasNext).isFalse()
            }
        }

        @Test
        fun `get contracts by NFT owner - valid owner address with no NFTs owned`() {
            val res =
                mockMvc
                    .get("$baseEndpoint/contracts?owner=0x0f872421dc479f3c11edd89512731814d0598db4")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(res.response.contentAsString, PAGINATED_NFT_CONTRACTS_TYPE)

            expect {
                that(contracts.data).isEmpty()
                that(contracts.pagination.hasNext).isFalse()
            }
        }
    }
}
