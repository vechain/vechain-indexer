package org.vechain.indexer.controller

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.CONTRACTS_PATH
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.rest.COUNT_LIMIT
import org.vechain.indexer.model.rest.PAGE_SIZE_LIMIT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*

internal class ContractControllerTest : AbstractIntegrationTest() {

    companion object {
        const val BASE_ENDPOINT = CONTRACTS_PATH
        const val TOTAL_CONTRACTS_NUMBER = 18
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Nested
    inner class ContractIdQueries {
        @Test
        fun `get transactions for bad address should return BAD_REQUEST`() {
            mockMvc.get("$BASE_ENDPOINT/badAddress")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `valid address should return OKAY`() {
            val contractAddress = "0x7bfe63ac68e3c6fed9d1006953ee140f29e084c1"
            val result = mockMvc.get("$BASE_ENDPOINT/$contractAddress")
                .andExpect { status { isOk() } }
                .andReturn()

            val contract = objectMapper.readValue(result.response.contentAsString, CONTRACT_TYPE)

            expectThat(contract.address).isEqualTo(contractAddress.lowercase(Locale.getDefault()))
        }

        @Test
        fun `valid address UPPERCASE`() {
            val contractAddress = "0x7BFE63AC68E3C6FED9D1006953EE140F29E084C1"
            val result = mockMvc.get("$BASE_ENDPOINT/$contractAddress")
                .andExpect { status { isOk() } }
                .andReturn()

            val contract = objectMapper.readValue(result.response.contentAsString, CONTRACT_TYPE)

            expectThat(contract.address).isEqualTo(contractAddress.lowercase(Locale.getDefault()))
        }

        @Test
        fun `mixed case`() {
            val contractAddress = "0x7bFe63ac68e3c6Fed9d1006953Ee140f29e084c1"
            val result = mockMvc.get("$BASE_ENDPOINT/$contractAddress")
                .andExpect { status { isOk() } }
                .andReturn()

            val contract = objectMapper.readValue(result.response.contentAsString, CONTRACT_TYPE)

            expectThat(contract.address).isEqualTo(contractAddress.lowercase(Locale.getDefault()))
        }

        @Test
        fun `no prefix hex`() {
            val contractAddress = "7bfe63ac68e3c6fed9d1006953ee140f29e084c1"
            val result = mockMvc.get("$BASE_ENDPOINT/$contractAddress")
                .andExpect { status { isOk() } }
                .andReturn()

            val contract = objectMapper.readValue(result.response.contentAsString, CONTRACT_TYPE)

            expectThat(contract.address).isEqualTo("0x" + contractAddress.lowercase(Locale.getDefault()))
        }
    }

    @Nested
    inner class ContractCreatorQueries {

        @Test
        fun `get contracts with over the limit page size should return BAD REQUEST`() {
            val creatorAddress = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc.get("$BASE_ENDPOINT?address=$creatorAddress&size=$size")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `fetch contracts by origin - no pagination`() {
            val creatorAddress = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"

            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data).hasSize(TOTAL_CONTRACTS_NUMBER)
        }

        @Test
        fun `fetch contracts by origin - paginated search - page only with results`() {
            val creatorAddress = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data).hasSize(size)
        }

        @Test
        fun `fetch contracts by origin - paginated search - page only without results`() {
            val creatorAddress = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&page=$page"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data).isEmpty()
        }

        @Test
        fun `fetch contracts by origin - paginated search - size only`() {
            val creatorAddress = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = ""
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data).hasSize(size)
        }

        @Test
        fun `fetch contracts by origin - paginated search - page & size`() {
            val creatorAddress = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data).hasSize(TOTAL_CONTRACTS_NUMBER - (page * size))
        }

        @Test
        fun `fetch contracts by origin - paginated search - sorted by blockNumber & txId & address`() {
            val creatorAddress = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data)
                .hasSize(TOTAL_CONTRACTS_NUMBER - (page * size))
                .isSorted(
                    compareByDescending<IndexedContract> { it.blockNumber }
                        .then(compareByDescending<IndexedContract> { it.txId }
                            .then(compareByDescending { it.address })
                        )
                )
        }

        @Test
        fun `fetch contracts by origin - paginated search - sorted by blockNumber & txId & address - pagination detail`() {
            val creatorAddress = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)
            val paginationDetail = contracts.pagination

            expect {
                that(contracts.data)
                    .hasSize(size)
                    .isSorted(
                        compareByDescending<IndexedContract> { it.blockNumber }
                            .then(compareByDescending<IndexedContract> { it.txId }
                                .then(compareByDescending { it.address })
                            )
                    )

                that(paginationDetail.isExactCount).isTrue()
                that(paginationDetail.countLimit).isEqualTo(COUNT_LIMIT)
                that(paginationDetail.totalPages).isEqualTo(2)
                that(paginationDetail.totalElements).isEqualTo(18)
                that(paginationDetail.hasNext).isTrue()
            }
        }

        @Test
        fun `fetch contracts by origin and contract type - invalid type returns BAD REQUEST`() {
            val creatorAddress = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val invalidType = "vip199"

            mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&type=$invalidType"
            )
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `fetch contracts by origin and contract type - without pagination`() {
            val creatorAddress = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val type = "vip180"

            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&type=$type"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data)
                .hasSize(2)
                .map(IndexedContract::isVip180).all { isTrue() }
        }

        @Test
        fun `fetch contracts by origin and contract type - with pagination`() {
            val creatorAddress = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val type = "vip180"
            val page = 0
            val size = 10

            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&type=$type" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data)
                .hasSize(2)
                .isSorted(
                    compareByDescending<IndexedContract> { it.blockNumber }
                        .then(compareByDescending<IndexedContract> { it.txId }
                            .then(compareByDescending { it.address })
                        )
                )
                .map(IndexedContract::isVip180).all { isTrue() }
        }

        @Test
        fun `fetch contracts by origin and contract type - with pagination sorting & pagination detail`() {
            val creatorAddress = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val type = "vip180"
            val page = 1
            val size = 1

            val result = mockMvc.get(
                "$BASE_ENDPOINT?address=$creatorAddress" +
                        "&type=$type" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)
            val paginationDetail = contracts.pagination

            expect {
                that(contracts.data)
                    .hasSize(size)
                    .isSorted(
                        compareByDescending<IndexedContract> { it.blockNumber }
                            .then(compareByDescending<IndexedContract> { it.txId }
                                .then(compareByDescending { it.address })
                            )
                    )
                    .map(IndexedContract::isVip180).all { isTrue() }

                that(paginationDetail.isExactCount).isTrue()
                that(paginationDetail.countLimit).isEqualTo(COUNT_LIMIT)
                that(paginationDetail.totalPages).isEqualTo(2)
                that(paginationDetail.totalElements).isEqualTo(2)
                that(paginationDetail.hasNext).isFalse()
            }
        }

        @Test
        fun `fetch contracts by contract type only - with pagination`() {
            val type = "vip180"
            val page = 0
            val size = 10

            val result = mockMvc.get(
                "$BASE_ENDPOINT?type=$type" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data)
                .hasSize(2)
                .isSorted(
                    compareByDescending<IndexedContract> { it.blockNumber }
                        .then(compareByDescending<IndexedContract> { it.txId }
                            .then(compareByDescending { it.address })
                        )
                )
                .map(IndexedContract::isVip180).all { isTrue() }
        }

        @Test
        fun `fetch contracts with no address nor type params - with pagination`() {
            val page = 0
            val size = 10

            val result = mockMvc.get(
                "$BASE_ENDPOINT?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, PAGINATED_CONTRACTS_TYPE)

            expectThat(contracts.data)
                .hasSize(size)
                .isSorted(
                    compareByDescending<IndexedContract> { it.blockNumber }
                        .then(compareByDescending<IndexedContract> { it.txId }
                            .then(compareByDescending { it.address })
                        )
                )
        }
    }
}