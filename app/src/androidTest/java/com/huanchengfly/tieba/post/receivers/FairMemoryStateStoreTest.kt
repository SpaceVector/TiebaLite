package com.huanchengfly.tieba.post.receivers

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FairMemoryStateStoreTest {
    @Test
    fun navigationStateIsConsumedOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FairMemoryStateStore.clear(context)
        try {
            val state = Bundle().apply { putString("route", "thread/123") }
            assertTrue(FairMemoryStateStore.save(context, state))
            assertEquals("thread/123", FairMemoryStateStore.consume(context)?.getString("route"))
            assertNull(FairMemoryStateStore.consume(context))
        } finally {
            FairMemoryStateStore.clear(context)
        }
    }
}
