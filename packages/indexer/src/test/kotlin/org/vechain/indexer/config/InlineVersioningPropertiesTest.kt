package org.vechain.indexer.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class InlineVersioningPropertiesTest {

    @Test
    fun `defaults pass validation`() {
        assertDoesNotThrow { InlineVersioningProperties().validate() }
    }

    @Test
    fun `blockWindow of 0 is valid`() {
        val props = InlineVersioningProperties().apply { blockWindow = 0 }
        assertDoesNotThrow { props.validate() }
    }

    @Test
    fun `negative blockWindow is rejected`() {
        val props = InlineVersioningProperties().apply { blockWindow = -1 }
        assertThrows<IllegalArgumentException> { props.validate() }
    }

    @Test
    fun `maxVersions of 1 is valid`() {
        val props =
            InlineVersioningProperties().apply {
                maxVersions = 1
                minVersions = 1
            }
        assertDoesNotThrow { props.validate() }
    }

    @Test
    fun `maxVersions of 0 is rejected`() {
        val props = InlineVersioningProperties().apply { maxVersions = 0 }
        assertThrows<IllegalArgumentException> { props.validate() }
    }

    @Test
    fun `negative maxVersions is rejected`() {
        val props = InlineVersioningProperties().apply { maxVersions = -5 }
        assertThrows<IllegalArgumentException> { props.validate() }
    }

    @Test
    fun `minVersions of 1 is valid`() {
        val props = InlineVersioningProperties().apply { minVersions = 1 }
        assertDoesNotThrow { props.validate() }
    }

    @Test
    fun `minVersions of 0 is rejected`() {
        val props = InlineVersioningProperties().apply { minVersions = 0 }
        assertThrows<IllegalArgumentException> { props.validate() }
    }

    @Test
    fun `negative minVersions is rejected`() {
        val props = InlineVersioningProperties().apply { minVersions = -1 }
        assertThrows<IllegalArgumentException> { props.validate() }
    }

    @Test
    fun `minVersions greater than maxVersions is rejected`() {
        val props =
            InlineVersioningProperties().apply {
                maxVersions = 10
                minVersions = 11
            }
        assertThrows<IllegalArgumentException> { props.validate() }
    }

    @Test
    fun `minVersions equal to maxVersions is valid`() {
        val props =
            InlineVersioningProperties().apply {
                maxVersions = 5
                minVersions = 5
            }
        assertDoesNotThrow { props.validate() }
    }
}
