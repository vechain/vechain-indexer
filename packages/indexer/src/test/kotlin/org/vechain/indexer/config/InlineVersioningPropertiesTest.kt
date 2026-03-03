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
        val props = InlineVersioningProperties().apply { maxVersions = 1 }
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
}
