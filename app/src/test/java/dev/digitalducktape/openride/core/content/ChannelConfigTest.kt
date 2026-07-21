package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelConfigTest {

    @Test
    fun `seed catalog contains all twelve curated channels`() {
        assertEquals(12, ChannelConfig.ALL.size)
    }

    @Test
    fun `every seed channel has a resolved UC channel id and a handle`() {
        ChannelConfig.ALL.forEach { channel ->
            assertTrue("${channel.displayName} id", channel.id.startsWith("UC"))
            assertTrue("${channel.displayName} handle", channel.handle.isNotBlank())
        }
    }

    @Test
    fun `channel ids are unique`() {
        assertEquals(ChannelConfig.ALL.size, ChannelConfig.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `scenic and workout are the only categories in use`() {
        assertEquals(3, ChannelConfig.ALL.count { it.category == ContentCategory.Scenic })
        assertEquals(9, ChannelConfig.ALL.count { it.category == ContentCategory.Workout })
    }
}
