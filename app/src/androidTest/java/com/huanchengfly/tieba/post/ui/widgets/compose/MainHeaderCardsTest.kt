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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavOptionsBuilder
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.api.models.MessageListBean
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.userLike.ConcernData
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.models.ThreadItemData
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.SearchPageDestination
import com.huanchengfly.tieba.post.ui.page.main.LocalMainNavigationBottomPadding
import com.huanchengfly.tieba.post.ui.page.main.explore.ExplorePage
import com.huanchengfly.tieba.post.ui.page.main.explore.concern.ConcernUiState
import com.huanchengfly.tieba.post.ui.page.main.explore.concern.ConcernViewModel
import com.huanchengfly.tieba.post.ui.page.main.explore.hot.HotUiState
import com.huanchengfly.tieba.post.ui.page.main.explore.hot.HotViewModel
import com.huanchengfly.tieba.post.ui.page.main.explore.personalized.PersonalizedUiState
import com.huanchengfly.tieba.post.ui.page.main.explore.personalized.PersonalizedViewModel
import com.huanchengfly.tieba.post.ui.page.main.home.HomePage
import com.huanchengfly.tieba.post.ui.page.main.home.HomeUiState
import com.huanchengfly.tieba.post.ui.page.main.home.HomeViewModel
import com.huanchengfly.tieba.post.ui.page.main.notifications.NotificationsPage
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.AtMeListViewModel
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.MessageItemData
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsListUiState
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.ReplyMeListViewModel
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import java.io.File
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainHeaderCardsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private var reportDirectoryName = "header-cards-v8"

    @After
    fun clearFixtureModels() = compose.runOnUiThread { owner.viewModelStore.clear() }

    @Test
    fun mainHeadersKeepCardsAndActionsInLightMode() = verifyHeaders(false)

    @Test
    fun mainHeadersKeepCardsAndActionsInDarkMode() = verifyHeaders(true)

    @Test
    fun exploreAndMessagesShareHomeRoundedScrollOcclusion() {
        reportDirectoryName = "main-floating-headers-v14"
        val dark = mutableStateOf(false)
        val screen = mutableStateOf("explore")
        val navigator = RecordingNavigator()
        val author = User(id = -17403001L, nameShow = "顶部预览用户")
        val threads = (1..25).map { index ->
            ThreadInfo(id = -17403100L - index, threadId = -17403100L - index,
                title = "顶栏滚动预览$index", author = author, forumName = "界面预览", replyNum = 12,
                richAbstract = listOf(PbContent(text = "列表从圆角顶栏背后滚过，初始位置与刷新提示保留顶部留白。")))
        }
        val messages = NotificationsListUiState(hasMore = false, data = (1..25).map { index ->
            MessageItemData(MessageListBean.MessageInfoBean(isFloor = "0", title = "消息预览原帖",
                content = "消息滚动预览$index", replyer = MessageListBean.ReplyerInfoBean(id = "-17403001", nameShow = "顶部预览用户"),
                threadId = "${-17403200L - index}", postId = "${-17403300L - index}", time = "0"), false)
        }.toImmutableList())
        val fixtures = listOf(
            ConcernViewModel().apply { initialized = true; seed(this, ConcernUiState(isRefreshing = false,
                hasMore = false, data = threads.map { ConcernData(threadList = it, recommendType = 1) }.toImmutableList())) },
            PersonalizedViewModel().apply { initialized = true; seed(this, PersonalizedUiState(isRefreshing = false,
                data = threads.map { ThreadItemData(it.wrapImmutable(), blocked = false, hidden = false) }.toImmutableList())) },
            HotViewModel().apply { initialized = true; seed(this, HotUiState(isRefreshing = false,
                threadList = threads.map { it.wrapImmutable() }.toImmutableList())) },
            ReplyMeListViewModel().apply { initialized = true; seed(this, messages) },
            AtMeListViewModel().apply { initialized = true; seed(this, messages) },
        )
        compose.runOnUiThread {
            fixtures.forEach { model ->
                ViewModelProvider(owner, object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(model)!!
                })[model.javaClass]
            }
        }
        val initialStates = fixtures.map { it.uiState.value }
        compose.setContent {
            Preview(dark.value, navigator, Account(uid = "-17403001", name = "顶部预览用户")) {
                key(screen.value, dark.value) {
                    when (screen.value) {
                        "explore" -> ExplorePage()
                        "notifications" -> NotificationsPage()
                        else -> NotificationsPage(navigator = navigator)
                    }
                }
            }
        }
        val measurements = mutableListOf<String>()
        fun px(dp: Float) = with(compose.density) { dp.dp.toPx() }
        fun capture(name: String): Bitmap {
            compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); SystemClock.sleep(250)
            val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
            File(outputDirectory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        fun currentList() = compose.onNode(SemanticsMatcher("Visible vertical list") { node ->
            node.config.contains(SemanticsActions.ScrollToIndex) && node.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                node.boundsInRoot.right > 0f && node.boundsInRoot.left < px(360f)
        })
        for (night in listOf(false, true)) {
            for (page in listOf("explore", "notifications", "notifications-standalone")) {
                compose.runOnIdle { dark.value = night; screen.value = page }
                compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
                val tabs = if (page == "explore") listOf(R.string.title_personalized, R.string.title_hot, R.string.title_concern)
                    else listOf(R.string.title_reply_me, R.string.title_at_me)
                for ((index, label) in tabs.withIndex()) {
                    val tab = compose.onNodeWithText(context.getString(label))
                    if (tab.fetchSemanticsNode().config[SemanticsProperties.Selected] != true) tab.performClick()
                    compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
                    tab.assertIsSelected()
                    currentList().performScrollToIndex(0)
                    val name = "$page-$index-${if (night) "dark" else "light"}"
                    val initial = capture("$name-initial")
                    val origin = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
                    val title = compose.onNodeWithText(context.getString(if (page == "explore") R.string.title_explore else R.string.title_notifications))
                    title.assertIsDisplayed()
                    val cardTop = title.fetchSemanticsNode().boundsInRoot.center.y - px(28f)
                    val cardBottom = cardTop + px(104f) // Existing 56dp toolbar plus 48dp text tabs.
                    fun pixel(image: Bitmap, x: Float, y: Float) = image.getPixel(px(x).toInt(), (y - origin.top).toInt())
                    val palette = colors(night)
                    assertEquals("Initial header corner stays open", palette.contentPageBackground.toArgb(), pixel(initial, 14f, cardBottom - px(2f)))
                    val first = compose.onNodeWithText(if (page == "explore") "顶栏滚动预览1" else "消息滚动预览1", substring = true)
                    first.assertIsDisplayed()
                    assertTrue("First card starts below floating header", first.fetchSemanticsNode().boundsInRoot.top >= cardBottom + px(8f))
                    currentList().performScrollToIndex(3).performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> assertTrue(scroll(0f, px(40f))) }
                    val scrolled = capture("$name-scrolled")
                    for (y in listOf(cardBottom - px(2f), cardBottom + px(4f))) {
                        assertEquals("Rounded corner/margin reveals scrolling card, not rectangular page mask",
                            palette.contentCardBackground.toArgb(), pixel(scrolled, 14f, y))
                    }
                    assertEquals("Header center stays opaque", palette.contentCardBackground.toArgb(), pixel(scrolled, 180f, cardBottom - px(2f)))
                    assertEquals("Status strip unchanged", pixel(initial, 180f, origin.top + px(10f)), pixel(scrolled, 180f, origin.top + px(10f)))
                    val layouts = mutableListOf<TextLayoutResult>()
                    title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val foreground = layouts.single().layoutInput.style.color
                    assertEquals(palette.text, foreground)
                    val contrast = (maxOf(foreground.luminance(), palette.contentCardBackground.luminance()) + 0.05f) /
                        (minOf(foreground.luminance(), palette.contentCardBackground.luminance()) + 0.05f)
                    assertTrue("Header title contrast=$contrast", contrast >= 4.5f)
                    tab.assertIsDisplayed().assertIsSelected()
                    measurements += "$name: initial inset correct; corner/margin=${pixel(scrolled, 14f, cardBottom - px(2f))}; opaque header/status; contrast=$contrast; tab stays clickable above scrolling content"
                }
                if (page == "notifications-standalone") {
                    val before = navigator.backs
                    compose.onNodeWithContentDescription(context.getString(R.string.button_back)).performClick()
                    assertEquals(before + 1, navigator.backs)
                } else {
                    compose.onNodeWithContentDescription(context.getString(R.string.title_search)).performClick()
                    assertEquals(SearchPageDestination.route, navigator.routes.last())
                }
            }
        }
        assertEquals(initialStates, fixtures.map { it.uiState.value })
        File(outputDirectory(), "measurements.txt").writeText(measurements.joinToString("\n", postfix = "\n") + "Initialized synthetic models unchanged; no API, account, preference or data writes.\n")
    }

    private fun verifyHeaders(dark: Boolean) {
        val screen = mutableStateOf("home")
        val navigator = RecordingNavigator()
        val home = HomeViewModel()
        val homeState = HomeUiState(
            isLoading = false,
            forums = persistentListOf(HomeUiState.Forum("", "-17402101", "顶部卡片预览", true, "8")),
        )
        seed(home, homeState)
        val messages = NotificationsListUiState(
            hasMore = false,
            data = persistentListOf(MessageItemData(MessageListBean.MessageInfoBean(
                isFloor = "0", title = "顶部样式预览", content = "消息内容继续使用现有卡片。",
                replyer = MessageListBean.ReplyerInfoBean(id = "-17402102", nameShow = "预览用户"),
                threadId = "-17402103", postId = "-17402104", time = "0",
            ), false)),
        )
        val fixtures = listOf(
            PersonalizedViewModel().apply { initialized = true; seed(this, PersonalizedUiState(isRefreshing = false)) },
            HotViewModel().apply { initialized = true; seed(this, HotUiState(isRefreshing = false)) },
            ReplyMeListViewModel().apply { initialized = true; seed(this, messages) },
            AtMeListViewModel().apply { initialized = true; seed(this, messages) },
        )
        compose.runOnUiThread {
            fixtures.forEach { model ->
                ViewModelProvider(owner, object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(model)!!
                })[model.javaClass]
            }
        }
        val initialStates = fixtures.map { it.uiState.value }
        compose.setContent {
            Preview(dark, navigator) {
                key(screen.value) {
                    when (screen.value) {
                        "home" -> HomePage(viewModel = home)
                        "explore" -> ExplorePage()
                        "notifications" -> NotificationsPage()
                        else -> NotificationsPage(navigator = navigator)
                    }
                }
            }
        }
        val measurements = mutableListOf<String>()
        for ((page, title) in listOf(
            "home" to R.string.title_main,
            "explore" to R.string.title_explore,
            "notifications" to R.string.title_notifications,
            "notifications-standalone" to R.string.title_notifications,
        )) {
            compose.runOnIdle { screen.value = page }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            SystemClock.sleep(800)
            val preview = compose.onNodeWithTag("preview")
            val bitmap = preview.captureToImage().asAndroidBitmap()
            val titleNode = compose.onNodeWithText(context.getString(title))
            titleNode.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val foreground = layouts.single().layoutInput.style.color
            assertEquals(colors(dark).text, foreground)
            val origin = preview.fetchSemanticsNode().boundsInRoot
            val titleBounds = titleNode.fetchSemanticsNode().boundsInRoot
            val top = titleBounds.center.y - origin.top - with(compose.density) { 28.dp.toPx() }
            fun pixel(x: Float, y: Float) = bitmap.getPixel(with(compose.density) { x.dp.roundToPx() }, y.toInt())
            val fill = Color(pixel(180f, top + with(compose.density) { 4.dp.toPx() }))
            assertEquals(colors(dark).contentCardBackground.toArgb(), fill.toArgb())
            assertEquals(colors(dark).contentPageBackground.toArgb(), pixel(2f, titleBounds.center.y - origin.top))
            assertEquals("Rounded corner must expose the page", colors(dark).contentPageBackground.toArgb(), pixel(12f, top + 2f))
            val contrast = (maxOf(foreground.luminance(), fill.luminance()) + 0.05f) /
                (minOf(foreground.luminance(), fill.luminance()) + 0.05f)
            assertTrue("$page header must remain readable with a primary-colored toolbar theme", contrast >= 4.5f)
            val mode = if (dark) "dark" else "light"
            File(outputDirectory(), "$page-$mode.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (page == "home") {
                compose.onNodeWithContentDescription(context.getString(R.string.title_oksign)).assertIsDisplayed()
                compose.onNodeWithContentDescription(context.getString(R.string.title_switch_list_single)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.hint_search)).performClick()
                assertEquals(SearchPageDestination.route, navigator.routes.last())
            } else {
                val labels = if (page == "explore") listOf(R.string.title_hot, R.string.title_personalized)
                    else listOf(R.string.title_at_me, R.string.title_reply_me)
                labels.forEach { label ->
                    compose.onNodeWithText(context.getString(label)).performClick()
                    compose.mainClock.advanceTimeBy(500)
                    compose.onNodeWithText(context.getString(label)).assertIsSelected()
                }
                if (page == "notifications-standalone") {
                    compose.onNodeWithContentDescription(context.getString(R.string.button_back)).performClick()
                    assertEquals(1, navigator.backs)
                } else {
                    compose.onNodeWithContentDescription(context.getString(R.string.title_search)).performClick()
                    assertEquals(SearchPageDestination.route, navigator.routes.last())
                }
            }
            measurements += "$page-$mode: shared fill=${fill.toArgb()}, 12dp outer gutter, rounded corner exposed, title contrast=$contrast; header actions verified"
        }
        File(outputDirectory(), "${if (dark) "dark" else "light"}-measurements.txt")
            .writeText(measurements.joinToString("\n", postfix = "\n"))
        assertEquals(homeState, home.uiState.value)
        assertEquals(initialStates, fixtures.map { it.uiState.value })
    }

    private fun <S : UiState> seed(model: BaseViewModel<*, *, *, *>, state: S) {
        BaseViewModel::class.java.getDeclaredField("uiState").apply {
            isAccessible = true
            set(model, MutableStateFlow(state))
        }
    }

    private fun outputDirectory() = File(context.getExternalFilesDir(null), reportDirectoryName).apply { mkdirs() }

    private class RecordingNavigator : DestinationsNavigator by EmptyDestinationsNavigator {
        val routes = mutableListOf<String>()
        var backs = 0
        override fun navigate(direction: Direction, onlyIfResumed: Boolean, builder: NavOptionsBuilder.() -> Unit) {
            routes += direction.route
        }
        override fun navigateUp(): Boolean { backs++; return true }
    }

    @Composable
    private fun Preview(dark: Boolean, navigator: DestinationsNavigator, account: Account? = null, content: @Composable () -> Unit) {
        val palette = colors(dark)
        CompositionLocalProvider(
            LocalExtendedColors provides palette, LocalAccount provides account,
            LocalViewModelStoreOwner provides owner, LocalMainNavigationBottomPadding provides 80.dp,
        ) {
            MaterialTheme(colors = getColorPalette(dark, palette)) {
                ProvideNavigator(navigator) {
                    ProvideContentColor(palette.text) {
                        Box(Modifier.width(360.dp).height(720.dp).background(palette.contentPageBackground).testTag("preview")) { content() }
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
        // A deliberately colored legacy toolbar catches white-on-white action/title regressions.
        return ExtendedColors(
            theme = "test", isNightMode = dark,
            primary = Color(0xFF16A99B), accent = Color(0xFF16A99B), onPrimary = Color.White, onAccent = Color.White,
            text = text, textSecondary = text.copy(alpha = 0.65f), textDisabled = text.copy(alpha = 0.35f),
            background = background, card = card, floorCard = inset, chip = inset, onChip = text,
            topBar = Color(0xFF087F78), topBarSurface = inset, onTopBar = Color.White, onTopBarSurface = Color.White,
            onTopBarSecondary = Color.White, onTopBarActive = Color.White, bottomBar = background,
            bottomBarSurface = inset, onBottomBarSurface = text, windowBackground = background,
            divider = inset, shadow = Color.Transparent, placeholder = inset, indicator = inset, unselected = text,
        )
    }
}
