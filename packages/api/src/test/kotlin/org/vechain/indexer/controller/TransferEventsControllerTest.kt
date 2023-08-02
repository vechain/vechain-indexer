package org.vechain.indexer.controller

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.TRANSFER_EVENTS_PATH
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.rest.COUNT_LIMIT
import org.vechain.indexer.model.rest.PAGE_SIZE_LIMIT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

internal class TransferEventsControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = TRANSFER_EVENTS_PATH
        const val VTHO_CONTRACT_ADDRESS = "0x0000000000000000000000000000456e65726779"
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `get transfer events with path param should return NOT_FOUND`() {
        mockMvc.get("$baseEndpoint/pathParam").andExpect { status { isNotFound() } }
    }

    @Test
    fun `get transfer events with over the limit page size should return BAD REQUEST`() {
        val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
        val size = PAGE_SIZE_LIMIT + 1

        mockMvc.get("$baseEndpoint?address=$address&size=$size").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `get transfer events with the zero address should return BAD REQUEST`() {
        val address = Address.ZERO_ADDRESS

        mockMvc.get("$baseEndpoint?address=$address").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `get transfer events with valid address should return OK`() {
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data).hasSize(12)
    }

    @Test
    fun `get transfer events address with no hex prefix should return OK`() {
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?address=0f872421dc479f3c11edd89512731814d0598db5" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data).hasSize(12)
    }

    @Test
    fun `get transfer events address uppercase should return OK`() {
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?address=0x0F872421dc479f3c11edd89512731814D0598db5" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data).hasSize(12)
    }

    @Test
    fun `get transfer events with paginated result`() {
        val page = 1
        val size = 5
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data).hasSize(size)
    }

    @Test
    fun `check transfer events is sorted by block number & txId & id`() {
        val page = 0
        val size = 3
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data)
            .hasSize(size)
            .isSorted(
                compareByDescending<IndexedTransferEvent> { it.blockNumber }
                    .then(
                        compareByDescending<IndexedTransferEvent> { it.txId }
                            .then(compareByDescending { it.id })
                    )
            )
    }

    @Test
    fun `get transfer events for contract`() {
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data).hasSize(70)
    }

    @Test
    fun `get transfer events for contract no hex prefix`() {
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?tokenAddress=08f30373569af024d15eb47fd477a35db929eaac" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data).hasSize(70)
    }

    @Test
    fun `get transfer events for contract upper case`() {
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?tokenAddress=0x08F30373569AF024D15EB47FD477A35DB929EAAC" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expectThat(transferEvents.data).hasSize(70)
    }

    @Test
    fun `get transfer events with pagination & sorting & pagination detail`() {
        val page = 0
        val size = 3
        val result =
            mockMvc
                .get(
                    "$baseEndpoint?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                        "&page=$page" +
                        "&size=$size"
                )
                .andExpect { status { isOk() } }
                .andReturn()

        val transferEvents =
            objectMapper.readValue(result.response.contentAsString, PAGINATED_TRANSFER_EVENTS_TYPE)

        expect {
            that(transferEvents.data)
                .hasSize(size)
                .isSorted(
                    compareByDescending<IndexedTransferEvent> { it.blockNumber }
                        .then(
                            compareByDescending<IndexedTransferEvent> { it.txId }
                                .then(compareByDescending { it.id })
                        )
                )

            that(transferEvents.pagination.hasCount).isTrue()
            that(transferEvents.pagination.countLimit).isEqualTo(COUNT_LIMIT)
            that(transferEvents.pagination.totalPages).isEqualTo(4)
            that(transferEvents.pagination.totalElements).isEqualTo(12)
            that(transferEvents.pagination.hasNext).isTrue()
        }
    }

    @Nested
    inner class FromTransferEvents {

        @Test
        fun `get from transfer events with over the limit page size should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc.get("$baseEndpoint/from?address=$address&size=$size").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get from transfer events with the zero address should return BAD REQUEST`() {
            val address = Address.ZERO_ADDRESS

            mockMvc.get("$baseEndpoint/from?address=$address").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get transfer events for from address`() {
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result =
                mockMvc
                    .get(
                        "$baseEndpoint/from?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                            "&page=$page" +
                            "&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transferEvents =
                objectMapper.readValue(
                    result.response.contentAsString,
                    PAGINATED_TRANSFER_EVENTS_TYPE
                )

            expectThat(transferEvents.data).hasSize(4)
        }

        @Test
        fun `get transfer events with contract address`() {
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result =
                mockMvc
                    .get(
                        "$baseEndpoint/from?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                            "&tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                            "&page=$page" +
                            "&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transferEvents =
                objectMapper.readValue(
                    result.response.contentAsString,
                    PAGINATED_TRANSFER_EVENTS_TYPE
                )

            expectThat(transferEvents.data).hasSize(1)
        }

        @Test
        fun `get from transfer events with pagination & sorting & pagination detail`() {
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result =
                mockMvc
                    .get(
                        "$baseEndpoint/from?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                            "&tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                            "&page=$page" +
                            "&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transferEvents =
                objectMapper.readValue(
                    result.response.contentAsString,
                    PAGINATED_TRANSFER_EVENTS_TYPE
                )

            expect {
                that(transferEvents.data)
                    .hasSize(1)
                    .isSorted(
                        compareByDescending<IndexedTransferEvent> { it.blockNumber }
                            .then(
                                compareByDescending<IndexedTransferEvent> { it.txId }
                                    .then(compareByDescending { it.id })
                            )
                    )

                that(transferEvents.pagination.hasCount).isTrue()
                that(transferEvents.pagination.countLimit).isEqualTo(COUNT_LIMIT)
                that(transferEvents.pagination.totalPages).isEqualTo(1)
                that(transferEvents.pagination.totalElements).isEqualTo(1)
                that(transferEvents.pagination.hasNext).isFalse()
            }
        }
    }

    @Nested
    inner class ToTransferEvents {

        @Test
        fun `get to transfer events with over the limit page size should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc.get("$baseEndpoint/to?address=$address&size=$size").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get to transfer events with the zero address should return BAD REQUEST`() {
            val address = Address.ZERO_ADDRESS

            mockMvc.get("$baseEndpoint/to?address=$address").andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get transfer events for to address`() {
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result =
                mockMvc
                    .get(
                        "$baseEndpoint/to?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                            "&page=$page" +
                            "&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transferEvents =
                objectMapper.readValue(
                    result.response.contentAsString,
                    PAGINATED_TRANSFER_EVENTS_TYPE
                )

            expectThat(transferEvents.data).hasSize(8)
        }

        @Test
        fun `get transfer events with to and contract address`() {
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result =
                mockMvc
                    .get(
                        "$baseEndpoint/to?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                            "&tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                            "&page=$page" +
                            "&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transferEvents =
                objectMapper.readValue(
                    result.response.contentAsString,
                    PAGINATED_TRANSFER_EVENTS_TYPE
                )

            expectThat(transferEvents.data).hasSize(2)
        }

        @Test
        fun `get from transfer events with pagination & sorting & pagination detail`() {
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result =
                mockMvc
                    .get(
                        "$baseEndpoint/to?address=0x0f872421dc479f3c11edd89512731814d0598db5" +
                            "&tokenAddress=0x08f30373569af024d15eb47fd477a35db929eaac" +
                            "&page=$page" +
                            "&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transferEvents =
                objectMapper.readValue(
                    result.response.contentAsString,
                    PAGINATED_TRANSFER_EVENTS_TYPE
                )

            expect {
                that(transferEvents.data)
                    .hasSize(2)
                    .isSorted(
                        compareByDescending<IndexedTransferEvent> { it.blockNumber }
                            .then(
                                compareByDescending<IndexedTransferEvent> { it.txId }
                                    .then(compareByDescending { it.id })
                            )
                    )

                that(transferEvents.pagination.hasCount).isTrue()
                that(transferEvents.pagination.countLimit).isEqualTo(COUNT_LIMIT)
                that(transferEvents.pagination.totalPages).isEqualTo(1)
                that(transferEvents.pagination.totalElements).isEqualTo(2)
                that(transferEvents.pagination.hasNext).isFalse()
            }
        }
    }

    @Nested
    inner class FungibleTokensContracts {

        @Test
        fun `get fungible tokens contracts with no address param should return BAD REQUEST`() {
            mockMvc.get("$baseEndpoint/fungible-tokens-contracts").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get fungible tokens contracts with invalid address should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea"

            mockMvc.get("$baseEndpoint/fungible-tokens-contracts?address=$address").andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `get fungible tokens contracts with over the limit page size should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc
                .get("$baseEndpoint/fungible-tokens-contracts?address=$address&size=$size")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get fungible tokens contracts with the zero address should return BAD REQUEST`() {
            val address = Address.ZERO_ADDRESS

            mockMvc.get("$baseEndpoint/fungible-tokens-contracts?address=$address").andExpect {
                status { isBadRequest() }
            }
        }

        @Disabled
        @Test
        fun `get fungible tokens contracts should return distinct token contracts in descending order`() {
            /**
             * In the test fixture, this address is the origin/destination of several fungible token
             * transfer events, but only with two contracts:
             * ["0x7333f3f9fc145cb887e7d809b485c2f24aa3cdb8", "0xf7cd0c570a1007dd356fb705332bace650dfa9fb"]
             * Most recent transfer first.
             */
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"

            val res =
                mockMvc
                    .get("$baseEndpoint/fungible-tokens-contracts?address=$address")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(
                    res.response.contentAsString,
                    PAGINATED_FUNGIBLE_TOKENS_CONTRACTS_TYPE
                )

            expect {
                that(contracts.data)
                    .hasSize(2)
                    .containsExactly(
                        "0x7333f3f9fc145cb887e7d809b485c2f24aa3cdb8",
                        "0xf7cd0c570a1007dd356fb705332bace650dfa9fb",
                    )
                that(contracts.pagination)
                that(contracts.pagination.totalElements).isEqualTo(2)
            }
        }

        @Disabled
        @Test
        fun `get fungible tokens contracts should return paginated token contracts`() {
            /**
             * In the test fixture, this address is the origin/destination of several fungible token
             * transfer events, but only with two contracts:
             * ["0x7333f3f9fc145cb887e7d809b485c2f24aa3cdb8", "0xf7cd0c570a1007dd356fb705332bace650dfa9fb"]
             * Most recent transfer first.
             */
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"

            val page1 = 0
            val page2 = 1
            val size = 1

            val res1 =
                mockMvc
                    .get(
                        "$baseEndpoint/fungible-tokens-contracts?address=$address&page=$page1&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val res2 =
                mockMvc
                    .get(
                        "$baseEndpoint/fungible-tokens-contracts?address=$address&page=$page2&size=$size"
                    )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts1 =
                objectMapper.readValue(
                    res1.response.contentAsString,
                    PAGINATED_FUNGIBLE_TOKENS_CONTRACTS_TYPE
                )

            val contracts2 =
                objectMapper.readValue(
                    res2.response.contentAsString,
                    PAGINATED_FUNGIBLE_TOKENS_CONTRACTS_TYPE
                )

            expect {
                // page 0 results
                that(contracts1.data)
                    .hasSize(size)
                    .containsExactly("0x7333f3f9fc145cb887e7d809b485c2f24aa3cdb8")
                that(contracts1.pagination.hasCount).isTrue()
                that(contracts1.pagination.countLimit).isEqualTo(COUNT_LIMIT)
                that(contracts1.pagination.totalPages).isEqualTo(2)
                that(contracts1.pagination.totalElements).isEqualTo(2)
                that(contracts1.pagination.hasNext).isTrue()

                // page 1 results
                that(contracts2.data)
                    .hasSize(size)
                    .containsExactly("0xf7cd0c570a1007dd356fb705332bace650dfa9fb")
                that(contracts2.pagination.hasCount).isTrue()
                that(contracts2.pagination.countLimit).isEqualTo(COUNT_LIMIT)
                that(contracts2.pagination.totalPages).isEqualTo(2)
                that(contracts2.pagination.totalElements).isEqualTo(2)
                that(contracts2.pagination.hasNext).isFalse()
            }
        }

        @Disabled
        @Test
        fun `get fungible tokens contracts should return no results for address with no fungible transfers`() {
            /** In the test fixture, this address doesn't have any from/to transfers. */
            val address = "0x0f872421dc479f3c11edd89512731814d0598db4"

            val res =
                mockMvc
                    .get("$baseEndpoint/fungible-tokens-contracts?address=$address")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(
                    res.response.contentAsString,
                    PAGINATED_FUNGIBLE_TOKENS_CONTRACTS_TYPE
                )

            expect {
                that(contracts.data).isEmpty()
                that(contracts.pagination)
                that(contracts.pagination.totalElements).isEqualTo(0)
            }
        }

        @Test
        fun `get fungible tokens contracts should not return VTHO transfers`() {
            /**
             * In the test fixture, this address receives some VTHO transfer. It should not should
             * show up in the fungible tokens contracts as we exclude the VTHO contract from them.
             */
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"

            val res =
                mockMvc
                    .get("$baseEndpoint/fungible-tokens-contracts?address=$address")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val contracts =
                objectMapper.readValue(
                    res.response.contentAsString,
                    PAGINATED_FUNGIBLE_TOKENS_CONTRACTS_TYPE
                )

            expect { that(contracts.data).isNotEmpty().all { isNotEqualTo(VTHO_CONTRACT_ADDRESS) } }
        }
    }
}
