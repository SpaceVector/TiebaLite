package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.huanchengfly.tieba.post.api.TiebaApi
import com.huanchengfly.tieba.post.api.models.AgreeBean
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.protos.Anti
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.contentRenders
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.renders
import com.huanchengfly.tieba.post.api.models.protos.updateAgreeStatus
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorCode
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.ImmutableHolder
import com.huanchengfly.tieba.post.arch.PartialChange
import com.huanchengfly.tieba.post.arch.PartialChangeProducer
import com.huanchengfly.tieba.post.arch.UiEvent
import com.huanchengfly.tieba.post.arch.UiIntent
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.ui.common.PbContentRender
import com.huanchengfly.tieba.post.utils.BlockManager.shouldBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@Stable
@HiltViewModel
class SubPostsViewModel @Inject constructor() :
    BaseViewModel<SubPostsUiIntent, SubPostsPartialChange, SubPostsUiState, SubPostsUiEvent>() {
    override fun createInitialState() = SubPostsUiState()

    override fun createPartialChangeProducer() = SubPostsPartialChangeProducer

    override fun dispatchEvent(partialChange: SubPostsPartialChange): UiEvent? =
        when (partialChange) {
            is SubPostsPartialChange.Load.Success -> SubPostsUiEvent.ScrollToSubPosts
            else -> null
        }

    object SubPostsPartialChangeProducer :
        PartialChangeProducer<SubPostsUiIntent, SubPostsPartialChange, SubPostsUiState> {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun toPartialChangeFlow(intentFlow: Flow<SubPostsUiIntent>): Flow<SubPostsPartialChange> =
            merge(
                intentFlow.filterIsInstance<SubPostsUiIntent.Load>()
                    .flatMapConcat { it.producePartialChange() },
                intentFlow.filterIsInstance<SubPostsUiIntent.LoadMore>()
                    .flatMapConcat { it.producePartialChange() },
                intentFlow.filterIsInstance<SubPostsUiIntent.Agree>()
                    .flatMapConcat { it.producePartialChange() },
                intentFlow.filterIsInstance<SubPostsUiIntent.DeletePost>()
                    .flatMapConcat { it.producePartialChange() },
            )

        private fun SubPostsUiIntent.Load.producePartialChange(): Flow<SubPostsPartialChange> = flow {
            emit(SubPostsPartialChange.Load.Start)
            val previewData = SubPostsNavigationCache.consumePreview(threadId, postId)
            buildPrefillPartialChange(previewData)?.let { emit(it) }
            val response = TiebaApi.getInstance()
                .pbFloorFlow(threadId, postId, forumId, page, subPostId)
                .first()
            val cachedData = SubPostsNavigationCache.get(threadId, postId)
            val post = checkNotNull(response.data_?.post)
            val pageInfo = checkNotNull(response.data_?.page)
            val forum = checkNotNull(response.data_?.forum)
            val thread = checkNotNull(response.data_?.thread)
            val anti = checkNotNull(response.data_?.anti)
            val renderedSubPosts = response.data_?.subpost_list.orEmpty()
            val authorIds = collectAuthorIds(renderedSubPosts, post)
            val cachedProfileIpAddressMap = buildCachedProfileIpAddressMap(authorIds)
            val seedIpAddressMap = buildSeedIpAddressMap(
                subPosts = renderedSubPosts,
                navigationCache = cachedData,
            )
            val initialSubPosts = buildSubPosts(
                subPosts = renderedSubPosts,
                seedIpAddressMap = seedIpAddressMap,
                profileIpAddressMap = cachedProfileIpAddressMap,
            )
            val initialPost = applyPostIpAddress(
                post = post,
                ipAddress = post.author?.ip_address?.takeIf { it.isNotBlank() }
                    ?: cachedData?.postIpAddress?.takeIf { it.isNotBlank() }
                    ?: post.author?.id?.let(cachedProfileIpAddressMap::get),
            )
            emit(
                SubPostsPartialChange.Load.Success(
                    anti.wrapImmutable(),
                    forum.wrapImmutable(),
                    thread.wrapImmutable(),
                    initialPost.wrapImmutable(),
                    post.contentRenders,
                    initialSubPosts,
                    pageInfo.current_page < pageInfo.total_page,
                    pageInfo.current_page,
                    pageInfo.total_page,
                    pageInfo.total_count
                )
            )

            val priorityAuthorIds = collectPriorityMissingAuthorIds(
                subPosts = renderedSubPosts,
                post = post,
                seedIpAddressMap = seedIpAddressMap,
                cachedProfileIpAddressMap = cachedProfileIpAddressMap,
                cachedPostIpAddress = cachedData?.postIpAddress,
            )
            val priorityProfileIpAddressMap = loadProfileIpAddressMap(
                authorIds = priorityAuthorIds,
                concurrency = PRIORITY_PROFILE_LOOKUP_CONCURRENCY,
            )
            if (priorityProfileIpAddressMap.isNotEmpty()) {
                val resolvedSubPostIpAddressMap = buildResolvedSubPostIpAddressMap(
                    subPosts = renderedSubPosts,
                    profileIpAddressMap = priorityProfileIpAddressMap,
                )
                val resolvedPostIpAddress = post.author?.id?.let(priorityProfileIpAddressMap::get)
                updateNavigationCache(
                    threadId = threadId,
                    postId = postId,
                    currentData = cachedData,
                    postIpAddress = resolvedPostIpAddress,
                    subPostIpAddressMap = resolvedSubPostIpAddressMap,
                )
                emit(
                    SubPostsPartialChange.ResolveIp.Success(
                        postIpAddress = resolvedPostIpAddress,
                        subPostIpAddressMap = resolvedSubPostIpAddressMap,
                    )
                )
            }

            val remainingAuthorIds = collectRemainingMissingAuthorIds(
                subPosts = renderedSubPosts,
                seedIpAddressMap = seedIpAddressMap,
                cachedProfileIpAddressMap = cachedProfileIpAddressMap + priorityProfileIpAddressMap,
                cachedPostIpAddress = cachedData?.postIpAddress ?: post.author?.id?.let(priorityProfileIpAddressMap::get),
            )
            val remainingProfileIpAddressMap = loadProfileIpAddressMap(
                authorIds = remainingAuthorIds,
                concurrency = BACKGROUND_PROFILE_LOOKUP_CONCURRENCY,
            )
            if (remainingProfileIpAddressMap.isNotEmpty()) {
                val resolvedSubPostIpAddressMap = buildResolvedSubPostIpAddressMap(
                    subPosts = renderedSubPosts,
                    profileIpAddressMap = remainingProfileIpAddressMap,
                )
                val resolvedPostIpAddress = post.author?.id?.let(remainingProfileIpAddressMap::get)
                updateNavigationCache(
                    threadId = threadId,
                    postId = postId,
                    currentData = SubPostsNavigationCache.get(threadId, postId),
                    postIpAddress = resolvedPostIpAddress,
                    subPostIpAddressMap = resolvedSubPostIpAddressMap,
                )
                emit(
                    SubPostsPartialChange.ResolveIp.Success(
                        postIpAddress = resolvedPostIpAddress,
                        subPostIpAddressMap = resolvedSubPostIpAddressMap,
                    )
                )
            }
        }.catch { emit(SubPostsPartialChange.Load.Failure(it)) }

        private fun SubPostsUiIntent.LoadMore.producePartialChange(): Flow<SubPostsPartialChange> = flow {
            emit(SubPostsPartialChange.LoadMore.Start)
            val response = TiebaApi.getInstance()
                .pbFloorFlow(threadId, postId, forumId, page, subPostId)
                .first()
            val cachedData = SubPostsNavigationCache.get(threadId, postId)
            val pageInfo = checkNotNull(response.data_?.page)
            val renderedSubPosts = response.data_?.subpost_list.orEmpty()
            val authorIds = collectAuthorIds(renderedSubPosts)
            val cachedProfileIpAddressMap = buildCachedProfileIpAddressMap(authorIds)
            val seedIpAddressMap = buildSeedIpAddressMap(
                subPosts = renderedSubPosts,
                navigationCache = cachedData,
            )
            val initialSubPosts = buildSubPosts(
                subPosts = renderedSubPosts,
                seedIpAddressMap = seedIpAddressMap,
                profileIpAddressMap = cachedProfileIpAddressMap,
            )
            emit(
                SubPostsPartialChange.LoadMore.Success(
                    initialSubPosts,
                    pageInfo.current_page < pageInfo.total_page,
                    pageInfo.current_page,
                    pageInfo.total_page,
                    pageInfo.total_count,
                )
            )

            val priorityAuthorIds = collectPriorityMissingAuthorIds(
                subPosts = renderedSubPosts,
                seedIpAddressMap = seedIpAddressMap,
                cachedProfileIpAddressMap = cachedProfileIpAddressMap,
            )
            val priorityProfileIpAddressMap = loadProfileIpAddressMap(
                authorIds = priorityAuthorIds,
                concurrency = PRIORITY_PROFILE_LOOKUP_CONCURRENCY,
            )
            if (priorityProfileIpAddressMap.isNotEmpty()) {
                val resolvedSubPostIpAddressMap = buildResolvedSubPostIpAddressMap(
                    subPosts = renderedSubPosts,
                    profileIpAddressMap = priorityProfileIpAddressMap,
                )
                updateNavigationCache(
                    threadId = threadId,
                    postId = postId,
                    currentData = cachedData,
                    subPostIpAddressMap = resolvedSubPostIpAddressMap,
                )
                emit(
                    SubPostsPartialChange.ResolveIp.Success(
                        subPostIpAddressMap = resolvedSubPostIpAddressMap,
                    )
                )
            }

            val remainingAuthorIds = collectRemainingMissingAuthorIds(
                subPosts = renderedSubPosts,
                seedIpAddressMap = seedIpAddressMap,
                cachedProfileIpAddressMap = cachedProfileIpAddressMap + priorityProfileIpAddressMap,
            )
            val remainingProfileIpAddressMap = loadProfileIpAddressMap(
                authorIds = remainingAuthorIds,
                concurrency = BACKGROUND_PROFILE_LOOKUP_CONCURRENCY,
            )
            if (remainingProfileIpAddressMap.isNotEmpty()) {
                val resolvedSubPostIpAddressMap = buildResolvedSubPostIpAddressMap(
                    subPosts = renderedSubPosts,
                    profileIpAddressMap = remainingProfileIpAddressMap,
                )
                updateNavigationCache(
                    threadId = threadId,
                    postId = postId,
                    currentData = SubPostsNavigationCache.get(threadId, postId),
                    subPostIpAddressMap = resolvedSubPostIpAddressMap,
                )
                emit(
                    SubPostsPartialChange.ResolveIp.Success(
                        subPostIpAddressMap = resolvedSubPostIpAddressMap,
                    )
                )
            }
        }.catch { emit(SubPostsPartialChange.LoadMore.Failure(it)) }

        private fun SubPostsUiIntent.Agree.producePartialChange(): Flow<SubPostsPartialChange.Agree> =
            TiebaApi.getInstance()
                .opAgreeFlow(
                    threadId.toString(),
                    (subPostId ?: postId).toString(),
                    if (agree) 0 else 1,
                    objType = if (subPostId == null) 1 else 2
                )
                .map<AgreeBean, SubPostsPartialChange.Agree> {
                    SubPostsPartialChange.Agree.Success(subPostId, agree)
                }
                .onStart { emit(SubPostsPartialChange.Agree.Start(subPostId, agree)) }
                .catch { emit(SubPostsPartialChange.Agree.Failure(subPostId, !agree, it)) }

        fun SubPostsUiIntent.DeletePost.producePartialChange(): Flow<SubPostsPartialChange.DeletePost> =
            TiebaApi.getInstance()
                .delPostFlow(
                    forumId,
                    forumName,
                    threadId,
                    subPostId ?: postId,
                    tbs,
                    false,
                    deleteMyPost
                )
                .map<CommonResponse, SubPostsPartialChange.DeletePost> {
                    SubPostsPartialChange.DeletePost.Success(postId, subPostId)
                }
                .catch {
                    emit(
                        SubPostsPartialChange.DeletePost.Failure(
                            it.getErrorCode(),
                            it.getErrorMessage()
                        )
                    )
                }

        private fun buildPrefillPartialChange(
            cachedData: SubPostsNavigationCache.CachedData?
        ): SubPostsPartialChange.Load.Prefill? {
            val previewPost = cachedData?.previewPost ?: return null
            val previewSubPosts = cachedData.previewSubPosts
            val authorIds = collectAuthorIds(previewSubPosts, previewPost)
            val cachedProfileIpAddressMap = buildCachedProfileIpAddressMap(authorIds)
            val seedIpAddressMap = buildSeedIpAddressMap(
                subPosts = previewSubPosts,
                navigationCache = cachedData,
            )
            return SubPostsPartialChange.Load.Prefill(
                post = applyPostIpAddress(
                    post = previewPost,
                    ipAddress = previewPost.author?.ip_address?.takeIf { it.isNotBlank() }
                        ?: cachedData.postIpAddress?.takeIf { it.isNotBlank() }
                        ?: previewPost.author?.id?.let(cachedProfileIpAddressMap::get),
                ).wrapImmutable(),
                postContentRenders = previewPost.contentRenders,
                subPosts = buildSubPosts(
                    subPosts = previewSubPosts,
                    seedIpAddressMap = seedIpAddressMap,
                    profileIpAddressMap = cachedProfileIpAddressMap,
                ),
                hasMore = cachedData.previewTotalCount > previewSubPosts.size,
                totalCount = cachedData.previewTotalCount,
            )
        }

        private fun collectAuthorIds(
            subPosts: List<SubPostList>,
            post: Post? = null,
        ): Set<Long> = buildSet {
            subPosts.forEach { subPost ->
                (subPost.author?.id?.takeIf { it != 0L }
                    ?: subPost.author_id.takeIf { it != 0L })?.let(::add)
            }
            post?.author?.id?.takeIf { it != 0L }?.let(::add)
        }

        private fun buildCachedProfileIpAddressMap(
            authorIds: Collection<Long>
        ): Map<Long, String> = authorIds.mapNotNull { authorId ->
            SubPostsIpCache.getUserIpAddress(authorId)?.let { authorId to it }
        }.toMap()

        private fun buildSeedIpAddressMap(
            subPosts: List<SubPostList>,
            navigationCache: SubPostsNavigationCache.CachedData?,
        ): Map<Long, String> = buildMap {
            putAll(navigationCache?.subPostIpAddressMap.orEmpty())
            putAll(
                subPosts.mapNotNull { subPost ->
                    val ipAddress = subPost.author?.ip.takeIf { !it.isNullOrBlank() }
                        ?: subPost.author?.ip_address.takeIf { !it.isNullOrBlank() }
                        ?: subPost.location?.name.takeIf { !it.isNullOrBlank() }
                        ?: return@mapNotNull null
                    subPost.id to ipAddress
                }.toMap()
            )
        }

        private fun buildSubPosts(
            subPosts: List<SubPostList>,
            seedIpAddressMap: Map<Long, String>,
            profileIpAddressMap: Map<Long, String>,
        ): ImmutableList<SubPostItemData> = subPosts.map { subPost ->
            SubPostItemData(
                subPost.wrapImmutable(),
                subPost.content.renders.toImmutableList(),
                ipAddress = seedIpAddressMap[subPost.id]
                    ?: subPost.author?.id?.let(profileIpAddressMap::get)
                    ?: subPost.author_id.takeIf { it != 0L }?.let(profileIpAddressMap::get)
            )
        }.toImmutableList()

        private fun applyPostIpAddress(
            post: Post,
            ipAddress: String?
        ): Post {
            if (ipAddress.isNullOrBlank() || post.author?.ip_address == ipAddress) return post
            return post.copy(author = post.author?.copy(ip_address = ipAddress))
        }

        private fun collectPriorityMissingAuthorIds(
            subPosts: List<SubPostList>,
            post: Post? = null,
            seedIpAddressMap: Map<Long, String>,
            cachedProfileIpAddressMap: Map<Long, String>,
            cachedPostIpAddress: String? = null,
        ): List<Long> {
            val prioritySubPosts = subPosts.take(PRIORITY_SUB_POST_COUNT)
            return collectMissingAuthorIds(
                subPosts = prioritySubPosts,
                post = post,
                seedIpAddressMap = seedIpAddressMap,
                cachedProfileIpAddressMap = cachedProfileIpAddressMap,
                cachedPostIpAddress = cachedPostIpAddress,
            )
        }

        private fun collectRemainingMissingAuthorIds(
            subPosts: List<SubPostList>,
            seedIpAddressMap: Map<Long, String>,
            cachedProfileIpAddressMap: Map<Long, String>,
            cachedPostIpAddress: String? = null,
        ): List<Long> {
            val prioritySubPostIds = subPosts
                .take(PRIORITY_SUB_POST_COUNT)
                .map { it.id }
                .toSet()
            return collectMissingAuthorIds(
                subPosts = subPosts.filterNot { it.id in prioritySubPostIds },
                post = null,
                seedIpAddressMap = seedIpAddressMap,
                cachedProfileIpAddressMap = cachedProfileIpAddressMap,
                cachedPostIpAddress = cachedPostIpAddress,
            )
        }

        private fun collectMissingAuthorIds(
            subPosts: List<SubPostList>,
            post: Post? = null,
            seedIpAddressMap: Map<Long, String>,
            cachedProfileIpAddressMap: Map<Long, String>,
            cachedPostIpAddress: String? = null,
        ): List<Long> {
            val missingAuthorIds = linkedSetOf<Long>()
            subPosts.forEach { subPost ->
                if (!hasResolvedIpAddress(subPost, seedIpAddressMap, cachedProfileIpAddressMap)) {
                    (subPost.author?.id?.takeIf { it != 0L }
                        ?: subPost.author_id.takeIf { it != 0L })?.let(missingAuthorIds::add)
                }
            }
            if (post != null) {
                val hasPostIpAddress = !post.author?.ip_address.isNullOrBlank()
                    || !cachedPostIpAddress.isNullOrBlank()
                    || (post.author?.id?.let(cachedProfileIpAddressMap::get) != null)
                if (!hasPostIpAddress) {
                    post.author?.id?.takeIf { it != 0L }?.let(missingAuthorIds::add)
                }
            }
            return missingAuthorIds.toList()
        }

        private fun hasResolvedIpAddress(
            subPost: SubPostList,
            seedIpAddressMap: Map<Long, String>,
            cachedProfileIpAddressMap: Map<Long, String>,
        ): Boolean =
            seedIpAddressMap[subPost.id] != null
                    || !subPost.author?.ip.isNullOrBlank()
                    || !subPost.author?.ip_address.isNullOrBlank()
                    || !subPost.location?.name.isNullOrBlank()
                    || (subPost.author?.id?.let(cachedProfileIpAddressMap::get) != null)
                    || (subPost.author_id.takeIf { it != 0L }?.let(cachedProfileIpAddressMap::get) != null)

        private fun buildResolvedSubPostIpAddressMap(
            subPosts: List<SubPostList>,
            profileIpAddressMap: Map<Long, String>,
        ): Map<Long, String> = subPosts.mapNotNull { subPost ->
            val authorId = subPost.author?.id?.takeIf { it != 0L }
                ?: subPost.author_id.takeIf { it != 0L }
                ?: return@mapNotNull null
            profileIpAddressMap[authorId]?.let { subPost.id to it }
        }.toMap()

        private suspend fun loadProfileIpAddressMap(
            authorIds: Collection<Long>,
            concurrency: Int,
        ): Map<Long, String> {
            val validAuthorIds = authorIds
                .asSequence()
                .filter { it != 0L }
                .distinct()
                .toList()
            if (validAuthorIds.isEmpty()) return emptyMap()

            val cachedIpAddressMap = validAuthorIds.mapNotNull { authorId ->
                SubPostsIpCache.getUserIpAddress(authorId)?.let { authorId to it }
            }.toMap()
            val missingAuthorIds = validAuthorIds.filterNot(cachedIpAddressMap::containsKey)
            if (missingAuthorIds.isEmpty()) return cachedIpAddressMap

            val fetchedIpAddressMap = coroutineScope {
                buildMap {
                    missingAuthorIds.chunked(concurrency.coerceAtLeast(1)).forEach { batchAuthorIds ->
                        batchAuthorIds
                            .map { authorId ->
                                async {
                                    val ipAddress = runCatching {
                                        TiebaApi.getInstance()
                                            .userProfileFlow(authorId)
                                            .first()
                                            .data_
                                            ?.user
                                            ?.ip_address
                                            ?.takeIf { it.isNotBlank() }
                                    }.getOrNull()
                                    if (!ipAddress.isNullOrBlank()) {
                                        SubPostsIpCache.putUserIpAddress(authorId, ipAddress)
                                        authorId to ipAddress
                                    } else {
                                        null
                                    }
                                }
                            }
                            .awaitAll()
                            .filterNotNull()
                            .forEach { (authorId, ipAddress) ->
                                put(authorId, ipAddress)
                            }
                    }
                }
            }

            return buildMap {
                putAll(cachedIpAddressMap)
                putAll(fetchedIpAddressMap)
            }
        }

        private fun updateNavigationCache(
            threadId: Long,
            postId: Long,
            currentData: SubPostsNavigationCache.CachedData?,
            postIpAddress: String? = null,
            subPostIpAddressMap: Map<Long, String> = emptyMap(),
        ) {
            val mergedPostIpAddress = postIpAddress
                ?.takeIf { it.isNotBlank() }
                ?: currentData?.postIpAddress
            val mergedSubPostIpAddressMap = buildMap {
                putAll(currentData?.subPostIpAddressMap.orEmpty())
                putAll(subPostIpAddressMap)
            }
            if (mergedPostIpAddress != null || mergedSubPostIpAddressMap.isNotEmpty()) {
                SubPostsNavigationCache.put(
                    threadId = threadId,
                    postId = postId,
                    data = SubPostsNavigationCache.CachedData(
                        postIpAddress = mergedPostIpAddress,
                        subPostIpAddressMap = mergedSubPostIpAddressMap,
                        previewPost = currentData?.previewPost,
                        previewSubPosts = currentData?.previewSubPosts.orEmpty(),
                        previewTotalCount = currentData?.previewTotalCount
                            ?: currentData?.previewSubPosts?.size
                            ?: 0,
                    )
                )
            }
        }
    }

}

sealed interface SubPostsUiIntent : UiIntent {
    data class Load(
        val forumId: Long,
        val threadId: Long,
        val postId: Long,
        val subPostId: Long = 0L,
        val page: Int = 1,
    ) : SubPostsUiIntent

    data class LoadMore(
        val forumId: Long,
        val threadId: Long,
        val postId: Long,
        val subPostId: Long = 0L,
        val page: Int = 1,
    ) : SubPostsUiIntent

    data class Agree(
        val forumId: Long,
        val threadId: Long,
        val postId: Long,
        val subPostId: Long? = null,
        val agree: Boolean
    ) : SubPostsUiIntent

    data class DeletePost(
        val forumId: Long,
        val forumName: String,
        val threadId: Long,
        val postId: Long,
        val subPostId: Long? = null,
        val deleteMyPost: Boolean,
        val tbs: String? = null
    ) : SubPostsUiIntent
}

sealed interface SubPostsPartialChange : PartialChange<SubPostsUiState> {
    sealed class Load : SubPostsPartialChange {
        override fun reduce(oldState: SubPostsUiState): SubPostsUiState =
            when (this) {
                is Start -> oldState.copy(
                    isRefreshing = true
                )

                is Prefill -> oldState.copy(
                    hasMore = hasMore,
                    totalCount = totalCount,
                    post = post,
                    postContentRenders = postContentRenders,
                    subPosts = subPosts,
                )

                is Success -> oldState.copy(
                    isRefreshing = false,
                    hasMore = hasMore,
                    currentPage = currentPage,
                    totalPage = totalPage,
                    totalCount = totalCount,
                    forum = forum,
                    thread = thread,
                    post = post,
                    postContentRenders = postContentRenders,
                    subPosts = subPosts,
                )

                is Failure -> oldState.copy(
                    isRefreshing = false,
                )
            }

        data object Start : Load()

        data class Prefill(
            val post: ImmutableHolder<Post>,
            val postContentRenders: ImmutableList<PbContentRender>,
            val subPosts: ImmutableList<SubPostItemData>,
            val hasMore: Boolean,
            val totalCount: Int,
        ) : Load()

        data class Success(
            val anti: ImmutableHolder<Anti>,
            val forum: ImmutableHolder<SimpleForum>,
            val thread: ImmutableHolder<ThreadInfo>,
            val post: ImmutableHolder<Post>,
            val postContentRenders: ImmutableList<PbContentRender>,
            val subPosts: ImmutableList<SubPostItemData>,
            val hasMore: Boolean,
            val currentPage: Int,
            val totalPage: Int,
            val totalCount: Int,
        ) : Load()

        data class Failure(val throwable: Throwable) : Load()
    }

    sealed class LoadMore : SubPostsPartialChange {
        override fun reduce(oldState: SubPostsUiState): SubPostsUiState =
            when (this) {
                is Start -> oldState.copy(
                    isLoading = true
                )

                is Success -> oldState.copy(
                    isLoading = false,
                    hasMore = hasMore,
                    currentPage = currentPage,
                    totalPage = totalPage,
                    totalCount = totalCount,
                    subPosts = (oldState.subPosts + subPosts).toImmutableList(),
                )

                is Failure -> oldState.copy(
                    isLoading = false,
                )
            }

        data object Start : LoadMore()

        data class Success(
            val subPosts: ImmutableList<SubPostItemData>,
            val hasMore: Boolean,
            val currentPage: Int,
            val totalPage: Int,
            val totalCount: Int,
        ) : LoadMore()

        data class Failure(val throwable: Throwable) : LoadMore()
    }

    sealed class ResolveIp : SubPostsPartialChange {
        override fun reduce(oldState: SubPostsUiState): SubPostsUiState =
            when (this) {
                is Success -> oldState.copy(
                    post = postIpAddress
                        ?.takeIf { it.isNotBlank() }
                        ?.let { ipAddress ->
                            oldState.post?.getImmutable {
                                if (author?.ip_address == ipAddress) {
                                    this
                                } else {
                                    copy(author = author?.copy(ip_address = ipAddress))
                                }
                            }
                        } ?: oldState.post,
                    subPosts = oldState.subPosts.map { subPost ->
                        val ipAddress = subPostIpAddressMap[subPost.id]
                        if (ipAddress.isNullOrBlank() || subPost.ipAddress == ipAddress) {
                            subPost
                        } else {
                            subPost.copy(ipAddress = ipAddress)
                        }
                    }.toImmutableList(),
                )
            }

        data class Success(
            val postIpAddress: String? = null,
            val subPostIpAddressMap: Map<Long, String> = emptyMap(),
        ) : ResolveIp()
    }

    sealed class Agree : SubPostsPartialChange {
        private fun List<SubPostItemData>.updateAgreeStatus(
            subPostId: Long,
            hasAgreed: Boolean,
        ): ImmutableList<SubPostItemData> =
            map {
                if (it.id == subPostId) {
                    it.updateAgreeStatus(if (hasAgreed) 1 else 0)
                } else {
                    it
                }
            }.toImmutableList()

        override fun reduce(oldState: SubPostsUiState): SubPostsUiState =
            when (this) {
                is Start -> oldState.copy(
                    post = if (subPostId == null)
                        oldState.post?.getImmutable { updateAgreeStatus(if (hasAgreed) 1 else 0) }
                    else
                        oldState.post,
                    subPosts = if (subPostId != null)
                        oldState.subPosts.updateAgreeStatus(subPostId, hasAgreed)
                    else
                        oldState.subPosts,
                )

                is Success -> oldState.copy(
                    post = if (subPostId == null)
                        oldState.post?.getImmutable { updateAgreeStatus(if (hasAgreed) 1 else 0) }
                    else
                        oldState.post,
                    subPosts = if (subPostId != null)
                        oldState.subPosts.updateAgreeStatus(subPostId, hasAgreed)
                    else
                        oldState.subPosts,
                )

                is Failure -> oldState.copy(
                    post = if (subPostId == null)
                        oldState.post?.getImmutable { updateAgreeStatus(if (hasAgreed) 1 else 0) }
                    else
                        oldState.post,
                    subPosts = if (subPostId != null)
                        oldState.subPosts.updateAgreeStatus(subPostId, hasAgreed)
                    else
                        oldState.subPosts,
                )
            }

        data class Start(
            val subPostId: Long?,
            val hasAgreed: Boolean
        ) : Agree()

        data class Success(
            val subPostId: Long?,
            val hasAgreed: Boolean
        ) : Agree()

        data class Failure(
            val subPostId: Long?,
            val hasAgreed: Boolean,
            val throwable: Throwable,
        ) : Agree()
    }

    sealed class DeletePost : SubPostsPartialChange {
        override fun reduce(oldState: SubPostsUiState): SubPostsUiState = when (this) {
            is Success -> {
                if (subPostId == null) {
                    oldState
                } else {
                    oldState.copy(
                        subPosts = oldState.subPosts.filter { it.id != subPostId }
                            .toImmutableList(),
                    )
                }
            }

            is Failure -> oldState
        }

        data class Success(
            val postId: Long,
            val subPostId: Long? = null,
        ) : DeletePost()

        data class Failure(
            val errorCode: Int,
            val errorMessage: String,
        ) : DeletePost()
    }
}

@Immutable
data class SubPostItemData(
    val subPost: ImmutableHolder<SubPostList>,
    val subPostContentRenders: ImmutableList<PbContentRender>,
    val ipAddress: String? = null,
    val blocked: Boolean = subPost.get { shouldBlock() },
) {
    constructor(
        subPost: SubPostList,
    ) : this(
        subPost.wrapImmutable(),
        subPost.content.renders.toImmutableList(),
        null,
        subPost.shouldBlock()
    )

    val id: Long
        get() = subPost.get { id }

    val author: ImmutableHolder<User>?
        get() = subPost.get { author }?.wrapImmutable()
}

private fun SubPostItemData.updateAgreeStatus(hasAgreed: Int): SubPostItemData =
    copy(subPost = subPost.getImmutable { updateAgreeStatus(hasAgreed) })

private const val PRIORITY_SUB_POST_COUNT = 4
private const val PRIORITY_PROFILE_LOOKUP_CONCURRENCY = 4
private const val BACKGROUND_PROFILE_LOOKUP_CONCURRENCY = 2

data class SubPostsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,

    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val totalPage: Int = 1,
    val totalCount: Int = 0,

    val anti: ImmutableHolder<Anti>? = null,
    val forum: ImmutableHolder<SimpleForum>? = null,
    val thread: ImmutableHolder<ThreadInfo>? = null,
    val post: ImmutableHolder<Post>? = null,
    val postContentRenders: ImmutableList<PbContentRender> = persistentListOf(),
    val subPosts: ImmutableList<SubPostItemData> = persistentListOf(),
) : UiState

sealed interface SubPostsUiEvent : UiEvent {
    data object ScrollToSubPosts : SubPostsUiEvent
}
