package org.vechain.indexer.model

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.vechain.indexer.utils.JsonUtils
import java.io.File


class ContractModelTest {

    @Test
    fun `default contracts should be up to date`() {
        val projectDir = System.getProperty("user.dir")
        val rootDir = projectDir.replace("packages/common", "")
        val contractsPath = "${rootDir}database/scripts/built-in-contracts.json"
        
        val defaultContractsFile = File(contractsPath)
        val contractsJson = defaultContractsFile.readText(Charsets.UTF_8)

        val cleansedContracts = contractsJson.replace("_id", "address")

        assertDoesNotThrow {
            JsonUtils.mapper.readValue(cleansedContracts, object : TypeReference<List<Contract>>() {})
        }
    }

}