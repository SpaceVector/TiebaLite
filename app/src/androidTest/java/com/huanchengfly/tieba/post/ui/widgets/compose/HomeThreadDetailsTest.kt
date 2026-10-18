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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptionsBuilder
import androidx.test.espresso.Espresso
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
import com.huanchengfly.tieba.post.ui.page.thread.ThreadSortType
import com.huanchengfly.tieba.post.ui.page.thread.ThreadUiState
import com.huanchengfly.tieba.post.ui.page.thread.ThreadViewModel
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
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

class HomeThreadDetailsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeLevelBadgesUseReadableTierColorsAndKeepForumActions() {
        val dark = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val levels = listOf("3", "8", "12", "17")
        val model = HomeViewModel()
        val state = HomeUiState(
            isLoading = false,
            forums = levels.mapIndexed { index, level ->
                HomeUiState.Forum("", "${-17402001L - index}", "等级预览${index + 1}", index % 2 == 0, level)
            }.toImmutableList(),
        )
        seed(model, state)
        compose.setContent { Preview(dark.value, navigator) { HomePage(viewModel = model) } }
        val measurements = mutableListOf<String>()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            val mode = if (night) "dark" else "light"
            val bitmap = savePreview("home-$mode")
            val origin = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
            val foregrounds = mutableSetOf<Color>()
            for (level in levels) {
                val node = compose.onNodeWithText("Lv.$level", useUnmergedTree = true)
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val foreground = layouts.single().layoutInput.style.color
                foregrounds += foreground
                val background = Color(bitmap.getPixel(
                    (bounds.center.x - origin.left).toInt(),
                    (bounds.top - origin.top - with(compose.density) { 2.dp.toPx() }).toInt(),
                ))
                val ratio = (maxOf(foreground.luminance(), background.luminance()) + 0.05f) /
                        (minOf(foreground.luminance(), background.luminance()) + 0.05f)
                assertTrue("$mode Lv.$level must remain readable: $ratio", ratio >= 4.5f)
                measurements += "$mode Lv.$level: text=${foreground.toArgb()}, fill=${background.toArgb()}, contrast=$ratio"
            }
            assertEquals("Keep distinct existing level tiers", 4, foregrounds.size)
            compose.onAllNodesWithContentDescription(context.getString(R.string.tip_signed), useUnmergedTree = true)
                .assertCountEquals(2)
            compose.onNodeWithText("等级预览1").performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.title_copy_forum_name)).assertIsDisplayed()
            Espresso.pressBack()
            compose.onNodeWithText("等级预览1").performClick()
            assertEquals(ForumPageDestination("等级预览1").route, navigator.routes.last())
        }
        assertEquals(state, model.uiState.value)
        writeMeasurements("levels", measurements)
    }

    @Test
    fun threadBackFloatsAboveScrollingContentAndKeepsNavigation() {
        val dark = mutableStateOf(false)
        val navigator = RecordingNavigator()
        val author = User(id = -17402020L, nameShow = "界面预览")
        val model = ThreadViewModel().apply { initialized = true }
        val state = ThreadUiState(
            hasMore = false, sortType = ThreadSortType.SORT_TYPE_DESC, author = author.wrapImmutable(),
            // Blank thread metadata deliberately disables history persistence in this fixture.
            threadInfo = ThreadInfo(id = -17402021L, title = "").wrapImmutable(),
            forum = SimpleForum(id = -17402022L, name = "悬浮预览", avatar = "").wrapImmutable(),
            firstPost = Post(id = -17402023L, floor = 1, title = "帖子悬浮返回预览", author = author).wrapImmutable(),
            firstPostContentRenders = persistentListOf(TextContentRender("初始正文避开返回键，滚动时内容从玻璃按钮下方经过。")),
            data = (1..6).map { index ->
                PostItemData(
                    post = Post(id = -17402023L - index, floor = index + 1, author = author).wrapImmutable(),
                    contentRenders = persistentListOf(TextContentRender("回复预览 $index：" + "保持卡片内容与悬浮按钮分离。".repeat(8))),
                    subPosts = persistentListOf(), blocked = false,
                )
            }.toImmutableList(),
        )
        seed(model, state)
        compose.setContent {
            Preview(dark.value, navigator) {
                CompositionLocalProvider(LocalDestination provides ThreadPageDestination) {
                    ThreadPage(-17402021L, navigator, sortType = ThreadSortType.SORT_TYPE_DESC, viewModel = model)
                }
            }
        }
        val measurements = mutableListOf<String>()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            val mode = if (night) "dark" else "light"
            savePreview("thread-$mode")
            val back = compose.onNodeWithContentDescription(context.getString(R.string.button_back))
            back.assertIsDisplayed()
            val initial = back.fetchSemanticsNode().boundsInRoot
            assertTrue(initial.width >= with(compose.density) { 48.dp.toPx() } - 1f)
            val title = compose.onNodeWithText("帖子悬浮返回预览").fetchSemanticsNode().boundsInRoot
            assertTrue("Initial content must clear the floating button", title.top >= initial.bottom)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
            compose.onNodeWithText("帖子悬浮返回预览").assertIsNotDisplayed()
            assertEquals(initial, back.fetchSemanticsNode().boundsInRoot)
            savePreview("thread-$mode-scrolled")
            val before = navigator.backs
            compose.onNodeWithContentDescription(context.getString(R.string.btn_more)).performClick()
            back.performClick()
            compose.runOnIdle { assertEquals("Back closes the expanded menu first", before, navigator.backs) }
            back.performClick()
            compose.runOnIdle { assertEquals(before + 1, navigator.backs) }
            compose.onNodeWithText(context.getString(R.string.title_forum, "悬浮预览")).performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertEquals(ForumPageDestination("悬浮预览").route, navigator.routes.last()) }
            measurements += "$mode: fixed back bounds=$initial; initial clearance, scroll, menu dismissal and forum route verified"
        }
        assertEquals(state, model.uiState.value)
        writeMeasurements("back", measurements)
    }

    private fun <S : UiState> seed(model: BaseViewModel<*, *, *, *>, state: S) {
        BaseViewModel::class.java.getDeclaredField("uiState").apply {
            isAccessible = true
            set(model, MutableStateFlow(state))
        }
    }

    private class RecordingNavigator : DestinationsNavigator by EmptyDestinationsNavigator {
        val routes = mutableListOf<String>()
        var backs = 0
        override fun navigate(direction: Direction, onlyIfResumed: Boolean, builder: NavOptionsBuilder.() -> Unit) {
            routes += direction.route
        }
        override fun navigateUp(): Boolean { backs++; return true }
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

    private fun savePreview(name: String): Bitmap {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        SystemClock.sleep(800)
        val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "home-thread-details-v7").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun writeMeasurements(name: String, values: List<String>) {
        File(context.getExternalFilesDir(null), "home-thread-details-v7/$name-measurements.txt")
            .writeText(values.joinToString("\n", postfix = "\n"))
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
