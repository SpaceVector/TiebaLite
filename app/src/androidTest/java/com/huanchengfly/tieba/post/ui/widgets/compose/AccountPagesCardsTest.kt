package com.huanchengfly.tieba.post.ui.widgets.compose

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavOptionsBuilder
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.ThreadStoreBean
import com.huanchengfly.tieba.post.api.models.UserLikeForumBean
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.models.database.History
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.BlockSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.CustomSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ForumPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.HabitSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.LoginPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.MoreSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.OKSignSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SearchPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.UserProfilePageDestination
import com.huanchengfly.tieba.post.ui.page.history.HistoryPage
import com.huanchengfly.tieba.post.ui.page.history.list.ForumHistoryListViewModel
import com.huanchengfly.tieba.post.ui.page.history.list.HistoryListUiState
import com.huanchengfly.tieba.post.ui.page.history.list.ThreadHistoryListViewModel
import com.huanchengfly.tieba.post.ui.page.main.LocalMainNavigationBottomPadding
import com.huanchengfly.tieba.post.ui.page.main.home.HomePage
import com.huanchengfly.tieba.post.ui.page.main.home.HomeUiState
import com.huanchengfly.tieba.post.ui.page.main.home.HomeViewModel
import com.huanchengfly.tieba.post.ui.page.settings.SettingsPage
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPageFrom
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPageFromStoreExtra
import com.huanchengfly.tieba.post.ui.page.thread.ThreadSortType
import com.huanchengfly.tieba.post.ui.page.threadstore.ThreadStorePage
import com.huanchengfly.tieba.post.ui.page.threadstore.ThreadStoreUiState
import com.huanchengfly.tieba.post.ui.page.threadstore.ThreadStoreViewModel
import com.huanchengfly.tieba.post.ui.page.user.UserProfilePage
import com.huanchengfly.tieba.post.ui.page.user.UserProfileUiState
import com.huanchengfly.tieba.post.ui.page.user.UserProfileViewModel
import com.huanchengfly.tieba.post.ui.page.user.likeforum.UserLikeForumUiState
import com.huanchengfly.tieba.post.ui.page.user.likeforum.UserLikeForumViewModel
import com.huanchengfly.tieba.post.ui.page.user.post.PostContentData
import com.huanchengfly.tieba.post.ui.page.user.post.PostListItemData
import com.huanchengfly.tieba.post.ui.page.user.post.UserPostUiState
import com.huanchengfly.tieba.post.ui.page.user.post.UserPostViewModel
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.huanchengfly.tieba.post.utils.HistoryUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Only V10's follow group and account subpages; fixtures never persist data or call APIs. */
class AccountPagesCardsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    private var reportDirectoryName = "account-pages-cards-v10"

    @After
    fun clearFixtureModels() = compose.runOnUiThread { owner.viewModelStore.clear() }

    @Test
    fun followedForumsShareOneLazyCardInBothLayoutsAndThemes() {
        reportDirectoryName = "home-soft-scroll-v13"
        val dark = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val model = HomeViewModel()
        val initial = HomeUiState(isLoading = false, forums = (1..25).map {
            HomeUiState.Forum("", "${-17402300 - it}", "关注贴吧$it", it % 2 == 0, "${it % 18 + 1}")
        }.toImmutableList())
        seed(model, initial)
        val oldSingle = context.appPreferences.listSingle
        val measurements = mutableListOf<String>()
        try {
            context.appPreferences.listSingle = true
            awaitListPreference(true)
            compose.setContent { Preview(dark.value, navigator) { HomePage(viewModel = model) } }
            for (night in listOf(false, true)) {
                compose.runOnIdle { dark.value = night }
                for (single in listOf(true, false)) {
                    if (context.appPreferences.listSingle != single) {
                        compose.onNodeWithContentDescription(context.getString(R.string.title_switch_list_single)).performClick()
                        awaitListPreference(single)
                    }
                    compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(0)
                    val name = "follow-${mode(night)}-${if (single) "list" else "grid"}"
                    val bitmap = save(name)
                    val title = bounds(context.getString(R.string.forum_list_title))
                    val first = bounds("关注贴吧1")
                    val third = bounds("关注贴吧3")
                    verifyCard(context.getString(R.string.forum_list_title), night, bitmap)
                    // Quiet rounded cells divide the group without a table-like grid of strokes.
                    for (y in listOf(title.center.y, (title.bottom + first.top) / 2)) {
                        assertEquals("One continuous follow surface", colors(night).contentCardBackground.toArgb(), pixel(bitmap, 200f, y))
                        assertEquals(colors(night).contentPageBackground.toArgb(), pixel(bitmap, 2f, y))
                    }
                    val tile = lerp(colors(night).contentCardBackground, colors(night).contentInsetBackground, 0.55f).toArgb()
                    fun verifyTile(label: String, x: Float, y: Float, image: Bitmap = bitmap) {
                        val actual = pixel(image, x, y)
                        for (shift in listOf(0, 8, 16, 24)) {
                            val delta = kotlin.math.abs(((tile ushr shift) and 255) - ((actual ushr shift) and 255))
                            assertTrue("$label expected=$tile actual=$actual; at most 1 raster rounding step", delta <= 1)
                        }
                        measurements += "$name $label: expected=$tile, actual=$actual; per-channel delta <= 1"
                    }
                    verifyTile("Rounded forum cell", first.left / compose.density.density + 2f, first.center.y)
                    if (single) {
                        val second = bounds("关注贴吧2")
                        val boundary = (first.bottom + second.top) / 2
                        assertEquals("Soft gap, not a divider stroke", colors(night).contentCardBackground.toArgb(), pixel(bitmap, 200f, boundary))
                    } else {
                        assertEquals(first.top, bounds("关注贴吧2").top, 1f)
                        assertEquals("Open space between rounded cells", colors(night).contentCardBackground.toArgb(), pixel(bitmap, 200f, first.center.y))
                        val boundary = (first.bottom + third.top) / 2
                        assertEquals("No horizontal grid stroke", colors(night).contentCardBackground.toArgb(), pixel(bitmap, 150f, boundary))
                    }
                    compose.onNodeWithText("关注贴吧25").assertIsNotDisplayed()
                    compose.onNodeWithText("关注贴吧1").performClick()
                    assertEquals(ForumPageDestination("关注贴吧1").route, navigator.routes.last())
                    compose.onNodeWithText("关注贴吧1").performTouchInput { longClick() }
                    compose.onNodeWithText(context.getString(R.string.title_copy_forum_name)).assertIsDisplayed()
                    pressBack()
                    compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(8)
                    val underHeader = save("$name-under-header")
                    val cardBottom = bounds(context.getString(R.string.hint_search)).bottom + px(12f)
                    for (y in listOf(cardBottom - px(2f), cardBottom + px(4f))) {
                        assertEquals("Rounded corner/margin exposes scrolling group, not a rectangular mask",
                            colors(night).contentCardBackground.toArgb(), pixel(underHeader, 14f, y))
                    }
                    assertEquals("Header itself stays opaque", colors(night).contentCardBackground.toArgb(), pixel(underHeader, 200f, cardBottom - px(2f)))
                    assertEquals("Status strip stays opaque while content scrolls", pixel(bitmap, 200f, px(10f)), pixel(underHeader, 200f, px(10f)))
                    compose.onNodeWithText(context.getString(R.string.hint_search)).performClick()
                    assertEquals(SearchPageDestination.route, navigator.routes.last())
                    compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(25)
                    val end = save("$name-end")
                    compose.onNodeWithText(context.getString(R.string.forum_list_title)).assertIsNotDisplayed()
                    val last = bounds("关注贴吧25")
                    verifyTile("Last rounded cell", last.left / compose.density.density + 2f, last.center.y, end)
                    assertEquals("No floating group background after the final row", colors(night).contentPageBackground.toArgb(), pixel(end, 200f, last.bottom + px(10f)))
                    measurements += "$name: connected group with soft rounded cells and open gaps; scrolling shows through header corners/margins while center/status stay opaque; 25 stable lazy items, last cap, title scroll-away, search/forum click and long press verified"
                }
            }
            assertEquals(initial, model.uiState.value)
        } finally {
            context.appPreferences.listSingle = oldSingle
            awaitListPreference(oldSingle)
            measurements += "listSingle before=$oldSingle, after=${context.appPreferences.listSingle}; restored"
            record("follow", measurements)
        }
    }

    @Test
    fun favoritesKeepCardReadabilityAndNavigationWithoutDeletingData() {
        val dark = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val item = ThreadStoreBean.ThreadStoreInfo(
            threadId = "-17402401", title = "收藏卡片预览", forumName = "收藏预览吧",
            author = ThreadStoreBean.AuthorInfo(lzUid = "-17402402", nameShow = "收藏作者"), media = emptyList(),
            isDeleted = "0", lastTime = "0", type = "0", status = "0", maxPid = "-17402403", minPid = "0",
            markPid = "-17402404", markStatus = "0", postNo = "2", postNoMsg = "", count = "2",
        )
        val model = ThreadStoreViewModel().apply { initialized = true }
        val initial = ThreadStoreUiState(hasMore = false, data = listOf(item))
        seed(model, initial)
        compose.setContent { Preview(dark.value, navigator) { ThreadStorePage(navigator, model) } }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            val bitmap = save("favorites-${mode(night)}")
            verifyCard(context.getString(R.string.title_my_collect), night, bitmap)
            verifyCard("收藏卡片预览", night, bitmap, substring = true)
            // UserHeader's existing author entry is its avatar, not the name/outer item.
            val authorY = compose.onNodeWithText("收藏作者", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.center.y
            val origin = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("preview").performTouchInput {
                click(Offset(px(44f), authorY - origin.top))
            }
            assertEquals(UserProfilePageDestination(-17402402L).route, navigator.routes.last())
            compose.onNodeWithText("收藏卡片预览", substring = true).performClick()
            assertEquals(ThreadPageDestination(
                -17402401L, postId = -17402404L, seeLz = context.appPreferences.collectThreadSeeLz,
                sortType = if (context.appPreferences.collectThreadDescSort) ThreadSortType.SORT_TYPE_DESC else ThreadSortType.SORT_TYPE_DEFAULT,
                from = ThreadPageFrom.FROM_STORE, extra = ThreadPageFromStoreExtra(-17402403L, 2),
            ).route, navigator.routes.last())
            compose.onNodeWithText("收藏卡片预览", substring = true).performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.title_collect_on)).assertIsDisplayed()
            pressBack()
        }
        assertEquals(initial, model.uiState.value)
        record("favorites", listOf("light/dark: header + item card fill/readability; author/thread routes and cancel-only long-press verified; no collection writes"))
    }

    @Test
    fun historyAndSettingsUseCardsAndKeepTabsAndEntryRoutes() {
        val dark = mutableStateOf(false)
        val screen = mutableStateOf("history")
        val navigator = RecordingNavigator()
        val threadState = HistoryListUiState(hasMore = false, todayHistoryData = listOf(
            History(title = "浏览帖子预览", data = "-17402501", type = HistoryUtil.TYPE_THREAD, username = "历史作者"),
        ))
        val forumState = HistoryListUiState(hasMore = false, todayHistoryData = listOf(
            History(title = "浏览贴吧预览", data = "浏览贴吧预览", type = HistoryUtil.TYPE_FORUM),
        ))
        val thread = ThreadHistoryListViewModel().apply { initialized = true; seed(this, threadState) }
        val forum = ForumHistoryListViewModel().apply { initialized = true; seed(this, forumState) }
        register(thread); register(forum)
        compose.setContent {
            Preview(dark.value, navigator) {
                key(screen.value, dark.value) {
                    if (screen.value == "history") HistoryPage(navigator) else SettingsPage(navigator)
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; screen.value = "history" }
            val bitmap = save("history-thread-${mode(night)}")
            verifyCard(context.getString(R.string.title_history), night, bitmap)
            verifyCard("浏览帖子预览", night, bitmap)
            assertEquals(colors(night).contentPageBackground.toArgb(), pixel(bitmap, 24f, bounds(context.getString(R.string.title_history_today)).center.y))
            compose.onNodeWithText("浏览帖子预览").performClick()
            assertEquals(ThreadPageDestination(-17402501L, from = ThreadPageFrom.FROM_HISTORY).route, navigator.routes.last())
            compose.onNodeWithText("浏览帖子预览").performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.title_delete)).assertIsDisplayed()
            pressBack()
            compose.onNodeWithText(context.getString(R.string.title_history_forum)).performClick().assertIsSelected()
            verifyCard("浏览贴吧预览", night, save("history-forum-${mode(night)}"))
            compose.onNodeWithText("浏览贴吧预览").performClick()
            assertEquals(ForumPageDestination("浏览贴吧预览").route, navigator.routes.last())
            compose.runOnIdle { screen.value = "settings" }
            val settings = save("settings-${mode(night)}")
            verifyCard(context.getString(R.string.title_settings), night, settings)
            for ((label, destination) in listOf(
                R.string.title_account_manage to LoginPageDestination,
                R.string.title_block_settings to BlockSettingsPageDestination,
                R.string.title_settings_custom to CustomSettingsPageDestination,
                R.string.title_settings_read_habit to HabitSettingsPageDestination,
                R.string.title_oksign to OKSignSettingsPageDestination,
                R.string.title_settings_more to MoreSettingsPageDestination,
            )) {
                verifyCard(context.getString(label), night, settings)
                compose.onNodeWithText(context.getString(label)).performClick()
                assertEquals(destination.route, navigator.routes.last())
            }
            compose.onNodeWithContentDescription(context.getString(R.string.button_back)).performClick()
        }
        assertEquals(2, navigator.backs)
        assertEquals(threadState, thread.uiState.value); assertEquals(forumState, forum.uiState.value)
        record("history-settings", listOf("light/dark: header/item card fill, readable date labels, both history tabs, six settings routes, back, cancel-only history menu; no history deletion or settings writes"))
    }

    @Test
    fun personalProfileUnifiesDetailsTabsPostsRepliesAndFollowedForums() {
        val dark = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val uid = -17402601L
        val account = Account(uid = uid.toString(), name = "主页预览用户")
        val user = User(id = uid, nameShow = "主页预览用户", intro = "资料、帖子和关注贴吧统一使用卡片。", sex = 1,
            concern_num = 12, fans_num = 345, total_agree_num = 678, thread_num = 1, post_num = 1,
            my_like_num = 1, tieba_uid = "17402601", tb_age = "8.2", ip_address = "预览地区")
        val profile = UserProfileViewModel().apply { initialized = true }
        seed(profile, UserProfileUiState(user = user.wrapImmutable()))
        val threadPost = PostInfoList(thread_id = -17402602L, post_id = -17402603L, user_id = uid,
            name_show = "主页预览用户", user_name = "主页预览用户", is_thread = 1, title = "主页主题卡片", forum_name = "主页预览吧")
        val replyPost = threadPost.copy(thread_id = -17402604L, post_id = -17402605L, is_thread = 0, title = "原帖内嵌卡片")
        val threads = UserPostViewModel().apply { initialized = true; seed(this, UserPostUiState(isRefreshing = false,
            posts = persistentListOf(PostListItemData(threadPost.wrapImmutable())))) }
        val replies = UserPostViewModel().apply { initialized = true; seed(this, UserPostUiState(isRefreshing = false,
            posts = persistentListOf(PostListItemData(replyPost.wrapImmutable(), contents = persistentListOf(
                PostContentData("主页回复卡片", 0L, -17402605L, false)))))) }
        val forums = UserLikeForumViewModel().apply { initialized = true; seed(this, UserLikeForumUiState(
            forums = persistentListOf(UserLikeForumBean.ForumBean(id = "-17402606", name = "主页关注贴吧", slogan = "关注贴吧也使用统一卡片。")))) }
        register(threads, "user_thread_$uid"); register(replies, "user_post_$uid"); register(forums)
        val initial = listOf(profile.uiState.value, threads.uiState.value, replies.uiState.value, forums.uiState.value)
        compose.setContent {
            Preview(dark.value, navigator, account) {
                key(dark.value) { UserProfilePage(uid, navigator, profile) }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            val first = save("profile-threads-${mode(night)}")
            verifyCard("资料、帖子和关注贴吧统一使用卡片。", night, first)
            verifyCard("主页主题卡片", night, first)
            val threadTab = context.getString(R.string.title_profile_threads_tab, "1")
            val replyTab = context.getString(R.string.title_profile_posts_tab, "1")
            val forumTab = context.getString(R.string.title_profile_concern_forums_tab, "1")
            verifyCard(threadTab, night, first)
            compose.onNodeWithText("主页主题卡片").performClick()
            assertEquals(ThreadPageDestination(-17402602L).route, navigator.routes.last())
            compose.onNodeWithText(replyTab).performClick().assertIsSelected()
            val second = save("profile-replies-${mode(night)}")
            verifyCard("主页回复卡片", night, second)
            compose.onNodeWithText("主页回复卡片").performClick()
            assertEquals(ThreadPageDestination(-17402604L, postId = -17402605L, scrollToReply = true).route, navigator.routes.last())
            compose.onNodeWithText("原帖内嵌卡片").performClick()
            assertEquals(ThreadPageDestination(-17402604L).route, navigator.routes.last())
            compose.onNodeWithText(forumTab).performClick().assertIsSelected()
            verifyCard("主页关注贴吧", night, save("profile-forums-${mode(night)}"))
            compose.onNodeWithText("主页关注贴吧").performClick()
            assertEquals(ForumPageDestination("主页关注贴吧").route, navigator.routes.last())
            compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp() }
            save("profile-collapsed-${mode(night)}")
            compose.onNodeWithText(forumTab).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.button_back)).performClick()
        }
        assertEquals(2, navigator.backs)
        assertEquals(initial, listOf(profile.uiState.value, threads.uiState.value, replies.uiState.value, forums.uiState.value))
        record("profile", listOf("light/dark: profile card + inset stats, tabs, thread/reply/forum cards; three tabs, thread/reply/origin/forum routes, collapsing header and back; no profile mutations"))
    }

    private fun <S : UiState> seed(model: BaseViewModel<*, *, *, *>, state: S) {
        BaseViewModel::class.java.getDeclaredField("uiState").apply { isAccessible = true; set(model, MutableStateFlow(state)) }
    }

    private fun register(model: BaseViewModel<*, *, *, *>, key: String? = null) = compose.runOnUiThread {
        val provider = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(model)!!
        })
        if (key == null) provider[model.javaClass] else provider[key, model.javaClass]
    }

    private fun awaitListPreference(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3000
        while (context.appPreferences.listSingle != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(25)
        }
        assertEquals(expected, context.appPreferences.listSingle)
    }

    private fun mode(dark: Boolean) = if (dark) "dark" else "light"
    private fun px(dp: Float) = with(compose.density) { dp.dp.toPx() }
    private fun bounds(label: String): Rect = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
    private fun pixel(bitmap: Bitmap, xDp: Float, rootY: Float): Int {
        val origin = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        return bitmap.getPixel(px(xDp).toInt(), (rootY - origin.top).toInt())
    }

    private fun verifyCard(label: String, dark: Boolean, bitmap: Bitmap, substring: Boolean = false) {
        val text = compose.onNodeWithText(label, substring = substring, useUnmergedTree = true)
        text.assertIsDisplayed()
        val y = text.fetchSemanticsNode().boundsInRoot.center.y
        assertEquals("$label card fill", colors(dark).contentCardBackground.toArgb(), pixel(bitmap, 24f, y))
        assertEquals("$label page gutter", colors(dark).contentPageBackground.toArgb(), pixel(bitmap, 2f, y))
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color
        val background = colors(dark).contentCardBackground
        val contrast = (maxOf(foreground.luminance(), background.luminance()) + 0.05f) /
            (minOf(foreground.luminance(), background.luminance()) + 0.05f)
        assertTrue("$label contrast=$contrast", contrast >= 4.5f)
        File(outputDirectory(), "pixel-measurements.txt").appendText(
            "${mode(dark)} label=$label; card=${pixel(bitmap, 24f, y)}; gutter=${pixel(bitmap, 2f, y)}; foreground=${foreground.toArgb()}; contrast=$contrast; minContrast=4.5\n"
        )
    }

    private fun outputDirectory() = File(context.getExternalFilesDir(null), reportDirectoryName).apply { mkdirs() }
    private fun record(name: String, lines: List<String>) = File(outputDirectory(), "$name-measurements.txt").writeText(lines.joinToString("\n", postfix = "\n"))
    private fun save(name: String): Bitmap {
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); SystemClock.sleep(800)
        val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        File(outputDirectory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private class RecordingNavigator : DestinationsNavigator by EmptyDestinationsNavigator {
        val routes = mutableListOf<String>()
        var backs = 0
        override fun navigate(direction: Direction, onlyIfResumed: Boolean, builder: NavOptionsBuilder.() -> Unit) { routes += direction.route }
        override fun navigateUp(): Boolean { backs++; return true }
    }

    @Composable
    private fun Preview(dark: Boolean, navigator: DestinationsNavigator, account: Account? = null, content: @Composable () -> Unit) {
        val palette = colors(dark)
        CompositionLocalProvider(LocalExtendedColors provides palette, LocalAccount provides account,
            LocalViewModelStoreOwner provides owner, LocalMainNavigationBottomPadding provides 80.dp) {
            MaterialTheme(colors = getColorPalette(dark, palette)) {
                ProvideNavigator(navigator) {
                    ProvideContentColor(palette.text) {
                        Box(Modifier.width(400.dp).height(720.dp).background(palette.contentPageBackground).testTag("preview")) { content() }
                    }
                }
            }
        }
    }

    private fun colors(dark: Boolean): ExtendedColors {
        val text = if (dark) Color(0xFFEAEAEA) else Color(0xFF202020)
        val background = if (dark) Color(0xFF101010) else Color.White
        val card = if (dark) Color(0xFF202020) else Color.White
        val inset = if (dark) Color(0xFF2A2A2A) else Color(0xFFF0F1F3)
        return ExtendedColors(theme = "test", isNightMode = dark,
            primary = Color(0xFF16A99B), accent = Color(0xFF16A99B), onPrimary = Color.White, onAccent = Color.White,
            text = text, textSecondary = text.copy(alpha = 0.65f), textDisabled = text.copy(alpha = 0.35f),
            background = background, card = card, floorCard = inset, chip = inset, onChip = text,
            topBar = Color(0xFF087F78), topBarSurface = inset, onTopBar = Color.White, onTopBarSurface = Color.White,
            onTopBarSecondary = Color.White, onTopBarActive = Color.White, bottomBar = background,
            bottomBarSurface = inset, onBottomBarSurface = text, windowBackground = background,
            divider = inset, shadow = Color.Transparent, placeholder = inset, indicator = inset, unselected = text)
    }
}
