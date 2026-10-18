package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.lazy.LazyListItemInfo
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.arch.wrapImmutable
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadReadingPositionTest {
    @Test
    fun resolvesExactPostAndScansViewportOnlyOnce() {
        fun post(id: Long) = PostItemData(Post(id = id).wrapImmutable(), blocked = false,
            contentRenders = persistentListOf(), subPosts = persistentListOf())
        val firstPost = Post(id = 1).wrapImmutable()
        var keyReads = 0
        fun visible(storedKey: Any) = object : LazyListItemInfo {
            override val index = 0
            override val offset = 0
            override val size = 100
            override val key: Any get() { keyReads++; return storedKey }
        }
        val data = listOf(post(12), post(312))
        assertEquals(312L, findLastVisibleThreadPost(data, listOf(visible("Post_312")), firstPost)?.get()?.id)
        assertSame(firstPost, findLastVisibleThreadPost(data, listOf(visible("LatestPost_312")), firstPost))
        assertSame(firstPost, findLastVisibleThreadPost(emptyList(), listOf(visible("Post_312")), firstPost))
        assertSame(firstPost, findLastVisibleThreadPost(data, listOf(visible("Post_unknown")), firstPost))
        val appended = data + post(999)
        assertEquals(999L, findLastVisibleThreadPost(appended, listOf(visible("Post_999")), firstPost)?.get()?.id)
        val loaded = List(2000) { post(10_000_000_000L + it) }
        val viewport = listOf(visible("Header"), visible("Post_${loaded.last().post.get().id}"), visible("Footer"))
        keyReads = 0
        assertSame(loaded.last().post, findLastVisibleThreadPost(loaded, viewport, firstPost))
        assertTrue("Viewport key reads must not scale with loaded replies: $keyReads", keyReads <= viewport.size * 3 + 1)
    }
}
