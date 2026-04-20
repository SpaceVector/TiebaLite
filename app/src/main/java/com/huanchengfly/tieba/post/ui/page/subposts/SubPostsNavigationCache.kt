package com.huanchengfly.tieba.post.ui.page.subposts

import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SubPostList

object SubPostsNavigationCache {
    private const val MAX_CACHE_SIZE = 16

    private data class Key(
        val threadId: Long,
        val postId: Long,
    )

    data class CachedData(
        val postIpAddress: String?,
        val subPostIpAddressMap: Map<Long, String>,
        val previewPost: Post? = null,
        val previewSubPosts: List<SubPostList> = emptyList(),
        val previewTotalCount: Int = previewSubPosts.size,
    )

    private val cache = object : LinkedHashMap<Key, CachedData>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CachedData>?): Boolean =
            size > MAX_CACHE_SIZE
    }

    @Synchronized
    fun put(
        threadId: Long,
        postId: Long,
        data: CachedData,
    ) {
        cache[Key(threadId, postId)] = data
    }

    @Synchronized
    fun get(
        threadId: Long,
        postId: Long,
    ): CachedData? = cache[Key(threadId, postId)]

    @Synchronized
    fun consumePreview(
        threadId: Long,
        postId: Long,
    ): CachedData? {
        val key = Key(threadId, postId)
        val data = cache[key] ?: return null
        if (data.previewPost != null || data.previewSubPosts.isNotEmpty()) {
            cache[key] = data.copy(
                previewPost = null,
                previewSubPosts = emptyList(),
                previewTotalCount = 0,
            )
        }
        return data
    }
}
