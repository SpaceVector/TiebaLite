package com.huanchengfly.tieba.post.ui.widgets.compose

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptionsBuilder
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.LocalDestination
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.models.database.History
import com.huanchengfly.tieba.post.ui.common.TextContentRender
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.ForumPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.page.main.LocalMainNavigationBottomPadding
import com.huanchengfly.tieba.post.ui.page.main.home.HomePage
import com.huanchengfly.tieba.post.ui.page.main.home.HomeUiState
import com.huanchengfly.tieba.post.ui.page.main.home.HomeViewModel
import com.huanchengfly.tieba.post.ui.page.thread.PostItemData
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPage
import com.huanchengfly.tieba.post.ui.page.thread.ThreadRepliesHeader
import com.huanchengfly.tieba.post.ui.page.thread.ThreadSortType
import com.huanchengfly.tieba.post.ui.page.thread.ThreadUiState
import com.huanchengfly.tieba.post.ui.page.thread.ThreadViewModel
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.huanchengfly.tieba.post.utils.appPreferences
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class SectionCardsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeHistoryAndFollowSectionsUseCardsAndKeepExpansion() {
        val dark = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val model = HomeViewModel()
        val reducerState = model.uiState
        val initial = HomeUiState(
            isLoading = false,
            forums = persistentListOf(HomeUiState.Forum("", "-17402201", "关注预览", true, "8")),
            topForums = persistentListOf(HomeUiState.Forum("", "-17402202", "置顶预览", false, "12")),
            historyForums = persistentListOf(History(title = "最近逛过的吧", data = "最近逛过的吧")),
        )
        val state = seed(model, initial)
        val historyEnabled = context.appPreferences.homePageShowHistoryForum
        try {
            context.appPreferences.homePageShowHistoryForum = true
            compose.setContent { Preview(dark.value, navigator) { HomePage(viewModel = model) } }
            val measurements = mutableListOf<String>()
            for (night in listOf(false, true)) {
                compose.runOnIdle { dark.value = night }
                val mode = if (night) "dark" else "light"
                val bitmap = savePreview("home-$mode")
                for (label in listOf(R.string.title_history_forum, R.string.title_top_forum, R.string.forum_list_title)) {
                    measurements += verifyCard(context.getString(label), night, bitmap)
                }
                compose.onNodeWithText("最近逛过的吧").performClick()
                assertEquals(ForumPageDestination("最近逛过的吧").route, navigator.routes.last())
                compose.onNodeWithText(context.getString(R.string.title_history_forum)).performClick()
                compose.waitUntil(3000) { !reducerState.value.expandHistoryForum }
                // Mirror only the real reducer's expand flag into the synthetic, non-persisted fixture.
                compose.runOnIdle { state.value = initial.copy(expandHistoryForum = reducerState.value.expandHistoryForum) }
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithText("最近逛过的吧").assertIsNotDisplayed()
                savePreview("home-$mode-collapsed")
                compose.onNodeWithText(context.getString(R.string.title_history_forum)).performClick()
                compose.waitUntil(3000) { reducerState.value.expandHistoryForum }
                compose.runOnIdle { state.value = initial.copy(expandHistoryForum = reducerState.value.expandHistoryForum) }
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithText("最近逛过的吧").assertIsDisplayed()
                measurements += "$mode: actual history toggle reducer and history forum route verified"
            }
            assertEquals(initial, state.value)
            writeMeasurements("home", measurements)
        } finally {
            context.appPreferences.homePageShowHistoryForum = historyEnabled
            assertEquals(historyEnabled, context.appPreferences.homePageShowHistoryForum)
            writeMeasurements("preferences", listOf("homePageShowHistoryForum before=$historyEnabled, after=${context.appPreferences.homePageShowHistoryForum}; restored"))
        }
    }

    @Test
    fun threadReplySectionMatchesCardsScrollsAwayAndKeepsFilters() {
        val dark = mutableStateOf(false)
        val isolatedHeader = mutableStateOf(false)
        val onlyAuthor = mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        val navigator = RecordingNavigator()
        val author = User(id = -17402210L, nameShow = "分区预览")
        val model = ThreadViewModel().apply { initialized = true }
        val initial = ThreadUiState(
            hasMore = false, sortType = ThreadSortType.SORT_TYPE_DESC, author = author.wrapImmutable(),
            // Blank metadata title prevents history writes; the first post supplies the fixture title.
            threadInfo = ThreadInfo(id = -17402211L, title = "", replyNum = 21).wrapImmutable(),
            forum = SimpleForum(id = -17402212L, name = "回复分区预览", avatar = "").wrapImmutable(),
            firstPost = Post(id = -17402213L, floor = 1, title = "回复分区卡片预览", author = author).wrapImmutable(),
            firstPostContentRenders = persistentListOf(TextContentRender("正文与回复分区使用统一卡片样式。")),
            data = (1..6).map { index ->
                PostItemData(
                    post = Post(id = -17402213L - index, floor = index + 1, author = author).wrapImmutable(),
                    contentRenders = persistentListOf(TextContentRender("回复 $index：" + "回复分区随列表滚动。".repeat(12))),
                    subPosts = persistentListOf(), blocked = false,
                )
            }.toImmutableList(),
        )
        seed(model, initial)
        compose.setContent {
            Preview(dark.value, navigator) {
                if (isolatedHeader.value) {
                    Column {
                        Spacer(Modifier.height(160.dp))
                        ThreadRepliesHeader("20", onlyAuthor.value) {
                            changes += it
                            onlyAuthor.value = it
                        }
                    }
                } else {
                    CompositionLocalProvider(LocalDestination provides ThreadPageDestination) {
                        ThreadPage(-17402211L, navigator, sortType = ThreadSortType.SORT_TYPE_DESC, viewModel = model)
                    }
                }
            }
        }
        val title = context.getString(R.string.title_thread_header, "20")
        val all = context.getString(R.string.text_all)
        val lz = context.getString(R.string.title_see_lz)
        val measurements = mutableListOf<String>()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; isolatedHeader.value = false }
            val mode = if (night) "dark" else "light"
            measurements += verifyCard(title, night, savePreview("thread-$mode"))
            compose.onNodeWithText(all).assertIsSelected()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
            compose.onNodeWithText(title).assertIsNotDisplayed()
            savePreview("thread-$mode-scrolled")
            compose.runOnIdle { isolatedHeader.value = true }
            compose.onNodeWithText(all).assertIsSelected().assertIsNotEnabled()
            for (label in listOf(all, lz)) {
                assertTrue("Filter touch target must be at least 40dp high",
                    compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.height >= with(compose.density) { 40.dp.toPx() } - 1f)
            }
            compose.onNodeWithText(lz).performClick()
            compose.onNodeWithText(lz).assertIsSelected().assertIsNotEnabled()
            savePreview("thread-$mode-only-author")
            compose.onNodeWithText(all).performClick()
            compose.onNodeWithText(all).assertIsSelected()
            measurements += "$mode: reply section scrolls out of view; both filter callbacks and selected states verified without API requests"
        }
        assertEquals(listOf(true, false, true, false), changes)
        assertEquals(initial, model.uiState.value)
        writeMeasurements("thread", measurements)
    }

    private fun verifyCard(label: String, dark: Boolean, bitmap: Bitmap): String {
        val node = compose.onNodeWithText(label, useUnmergedTree = true)
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val origin = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        val y = (bounds.center.y - origin.top).toInt()
        val fill = bitmap.getPixel(with(compose.density) { 24.dp.roundToPx() }, y)
        val page = bitmap.getPixel(with(compose.density) { 2.dp.roundToPx() }, y)
        assertEquals(colors(dark).contentCardBackground.toArgb(), fill)
        assertEquals(colors(dark).contentPageBackground.toArgb(), page)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color
        val background = Color(fill)
        val contrast = (maxOf(foreground.luminance(), background.luminance()) + 0.05f) /
            (minOf(foreground.luminance(), background.luminance()) + 0.05f)
        assertTrue("Section title must remain readable", contrast >= 4.5f)
        return "${if (dark) "dark" else "light"} $label: cardFill=$fill, pageGutter=$page, contrast=$contrast"
    }

    private fun <S : UiState> seed(model: BaseViewModel<*, *, *, *>, state: S): MutableStateFlow<S> {
        val flow = MutableStateFlow(state)
        BaseViewModel::class.java.getDeclaredField("uiState").apply { isAccessible = true; set(model, flow) }
        return flow
    }

    private class RecordingNavigator : DestinationsNavigator by EmptyDestinationsNavigator {
        val routes = mutableListOf<String>()
        override fun navigate(direction: Direction, onlyIfResumed: Boolean, builder: NavOptionsBuilder.() -> Unit) {
            routes += direction.route
        }
    }

    @Composable
    private fun Preview(dark: Boolean, navigator: DestinationsNavigator, content: @Composable () -> Unit) {
        val palette = colors(dark)
        CompositionLocalProvider(
            LocalExtendedColors provides palette, LocalAccount provides null,
            LocalMainNavigationBottomPadding provides 80.dp,
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

    private fun outputDirectory() = File(context.getExternalFilesDir(null), "section-cards-v9").apply { mkdirs() }

    private fun savePreview(name: String): Bitmap {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        SystemClock.sleep(800)
        val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        File(outputDirectory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun writeMeasurements(name: String, values: List<String>) {
        File(outputDirectory(), "$name-measurements.txt").writeText(values.joinToString("\n", postfix = "\n"))
    }

    private fun colors(dark: Boolean): ExtendedColors {
        val text = if (dark) Color(0xFFEAEAEA) else Color(0xFF202020)
        val background = if (dark) Color(0xFF101010) else Color.White
        val card = if (dark) Color(0xFF202020) else Color.White
        val inset = if (dark) Color(0xFF2A2A2A) else Color(0xFFF0F1F3)
        return ExtendedColors(
            theme = "test", isNightMode = dark,
            primary = Color(0xFF16A99B), accent = Color(0xFF16A99B), onPrimary = Color.White, onAccent = Color.White,
            text = text, textSecondary = text.copy(alpha = 0.65f), textDisabled = text.copy(alpha = 0.35f),
            background = background, card = card, floorCard = inset, chip = inset, onChip = text,
            topBar = background, topBarSurface = inset, onTopBar = text, onTopBarSurface = text,
            onTopBarSecondary = text, onTopBarActive = text, bottomBar = background,
            bottomBarSurface = inset, onBottomBarSurface = text, windowBackground = background,
            divider = inset, shadow = Color.Transparent, placeholder = inset, indicator = inset, unselected = text,
        )
    }
}
