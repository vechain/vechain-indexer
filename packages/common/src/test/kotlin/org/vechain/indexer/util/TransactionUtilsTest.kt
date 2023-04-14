package org.vechain.indexer.util

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.rest.ExecuteCodeResponse
import org.vechain.indexer.utils.TransactionUtils
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class TransactionUtilsTest {

    @Test
    fun `response has vm error`() {
        val data = ExecuteCodeResponse(
            vmError = "internal error",
            data = "0x",
            reverted = false,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0
        )

        val isSuccess = TransactionUtils.isSuccessWithData(data)

        expectThat(isSuccess).isEqualTo(false)
    }

    @Test
    fun `response is reverted`() {
        val data = ExecuteCodeResponse(
            vmError = null,
            data = "0x",
            reverted = true,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0
        )

        val isSuccess = TransactionUtils.isSuccessWithData(data)

        expectThat(isSuccess).isEqualTo(false)
    }

    @Test
    fun `response has no data`() {
        val data = ExecuteCodeResponse(
            vmError = null,
            data = "0x",
            reverted = false,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0
        )

        val isSuccess = TransactionUtils.isSuccessWithData(data)

        expectThat(isSuccess).isEqualTo(false)
    }

    @Test
    fun `res has healthy data`() {
        val data = ExecuteCodeResponse(
            vmError = null,
            data = "0x123",
            reverted = false,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0
        )

        val isSuccess = TransactionUtils.isSuccessWithData(data)

        expectThat(isSuccess).isEqualTo(true)
    }


}