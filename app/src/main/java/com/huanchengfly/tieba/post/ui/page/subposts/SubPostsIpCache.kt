package com.huanchengfly.tieba.post.ui.page.subposts

object SubPostsIpCache {
    private const val MAX_CACHE_SIZE = 128

    private val userIpAddressMap = object : LinkedHashMap<Long, String>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean =
            size > MAX_CACHE_SIZE
    }

    @Synchronized
    fun getUserIpAddress(userId: Long): String? = userIpAddressMap[userId]

    @Synchronized
    fun putUserIpAddress(userId: Long, ipAddress: String) {
        if (userId != 0L && ipAddress.isNotBlank()) {
            userIpAddressMap[userId] = ipAddress
        }
    }
}
