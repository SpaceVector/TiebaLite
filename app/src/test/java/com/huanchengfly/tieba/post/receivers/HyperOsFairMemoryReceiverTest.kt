package com.huanchengfly.tieba.post.receivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HyperOsFairMemoryReceiverTest {
    @Test
    fun mapsOfficialBroadcastActions() {
        assertEquals(FairMemoryAction.TRIM, fairMemoryActionOf("itgsa.intent.action.TRIM"))
        assertEquals(FairMemoryAction.KILL, fairMemoryActionOf("itgsa.intent.action.KILL"))
        assertNull(fairMemoryActionOf("android.intent.action.TRIM_MEMORY"))
    }
}
