package com.huanchengfly.tieba.post.ui.widgets.compose

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.LocalDestination
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.ui.common.TextContentRender
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.SubPostsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SubPostsSheetPageDestination
import com.huanchengfly.tieba.post.ui.page.main.home.HomePage
import com.huanchengfly.tieba.post.ui.page.main.home.HomeUiState
import com.huanchengfly.tieba.post.ui.page.main.home.HomeViewModel
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostItemData as SheetSubPostItemData
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsContent
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsUiState
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsViewModel
import com.huanchengfly.tieba.post.ui.page.thread.PostAgreeBtn
import com.huanchengfly.tieba.post.ui.page.thread.PostCard
import com.huanchengfly.tieba.post.ui.page.thread.SubPostItemData
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ContentCardsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val author = User(id = -17401811L, name = "界面示例", nameShow = "界面示例", ip_address = "本地预览")

    @Test
    fun postAgreeButtonBlendsIntoParentAndKeepsToggleAction() {
        val dark = mutableStateOf(false)
        val agreed = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            Preview(dark.value) {
                Box(Modifier.padding(24.dp).background(colors(dark.value).floorCard)) {
                    PostAgreeBtn(
                        hasAgreed = agreed.value,
                        agreeNum = if (agreed.value) 13L else 12L,
                        onClick = { clicks++; agreed.value = !agreed.value },
                        modifier = Modifier.testTag("agree-button"),
                    )
                }
            }
        }
        val failures = mutableListOf<String>()
        val measurements = mutableListOf<String>()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; agreed.value = false }
            val mode = if (night) "dark" else "light"
            for (selected in listOf(false, true)) {
                compose.mainClock.advanceTimeBy(500)
                compose.waitForIdle()
                SystemClock.sleep(800)
                val bitmap = compose.onNodeWithTag("agree-button").captureToImage().asAndroidBitmap()
                val y = with(compose.density) { 2.dp.roundToPx() }
                val fill = bitmap.getPixel(bitmap.width / 2, y)
                val expected = colors(night).floorCard.toArgb()
                measurements += "$mode selected=$selected: background=$fill parent=$expected size=${bitmap.width}x${bitmap.height}"
                if (fill != expected) failures += "$mode selected=$selected: background=$fill parent=$expected"
                compose.onNodeWithText(if (selected) "13" else "12").assertIsDisplayed()
                savePreview("agree-$mode-${if (selected) "selected" else "idle"}")
                compose.onNodeWithTag("agree-button").performClick()
            }
        }
        File(context.getExternalFilesDir(null), "subposts-scroll-v5/like-measurements.txt")
            .writeText(measurements.joinToString("\n", postfix = "\n"))
        compose.runOnIdle { assertEquals(4, clicks) }
        assertTrue("Like actions must inherit the surrounding surface: $failures", failures.isEmpty())
    }

    @Test
    fun subPostsReplyCountScrollsAwayAndReturnsWithListInBothThemes() {
        val dark = mutableStateOf(false)
        val sheetMode = mutableStateOf(true)
        var closed = 0
        val model = SubPostsViewModel().apply { initialized = true }
        val state = SubPostsUiState(
            post = Post(id = -17401821L, floor = 2, author = author).wrapImmutable(),
            postContentRenders = persistentListOf(TextContentRender("楼中楼原回复，点赞不再有独立色块。")),
            subPosts = (1..8).map { index ->
                SheetSubPostItemData(
                    subPost = SubPostList(id = -17401821L - index, author = author).wrapImmutable(),
                    subPostContentRenders = persistentListOf(TextContentRender("回复示例 $index：保持轻盈的阅读层次。")),
                    blocked = false,
                )
            }.toImmutableList(),
            totalCount = 8,
            hasMore = false,
        )
        BaseViewModel::class.java.getDeclaredField("uiState").apply {
            isAccessible = true
            set(model, MutableStateFlow(state))
        }
        compose.setContent {
            Preview(dark.value) {
                CompositionLocalProvider(
                    LocalDestination provides if (sheetMode.value) SubPostsSheetPageDestination else SubPostsPageDestination,
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF738EAF))) {
                        SubPostsContent(
                            viewModel = model, forumId = -1L, threadId = -17401820L,
                            postId = -17401821L, isSheet = sheetMode.value, onNavigateUp = { closed++ },
                        )
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        val measurements = mutableListOf<String>()
        for ((sheet, night) in listOf(true to false, true to true, false to false, false to true)) {
            compose.runOnIdle { sheetMode.value = sheet; dark.value = night }
            val mode = "${if (sheet) "sheet" else "page"}-${if (night) "dark" else "light"}"
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            val label = context.getString(R.string.title_sub_posts_header, 8)
            compose.onNodeWithText(label).assertIsDisplayed()
            val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            val preview = compose.onNodeWithTag("preview")
            val origin = preview.fetchSemanticsNode().boundsInRoot
            val bitmap = preview.captureToImage().asAndroidBitmap()
            val x = with(compose.density) { (if (sheet) 24.dp else 12.dp).roundToPx() }
            val y = (bounds.center.y - origin.top).toInt()
            val below = (bounds.bottom - origin.top).toInt() + with(compose.density) { 16.dp.roundToPx() }
            val header = bitmap.getPixel(x, y)
            val surrounding = bitmap.getPixel(x, below)
            measurements += "$mode: header gutter=$header surrounding sheet=$surrounding headerBounds=$bounds"
            // Samples at different Y positions include the sheet's subtle vertical highlight.
            val matchesSurface = listOf(0, 8, 16, 24).all { shift ->
                kotlin.math.abs((header ushr shift and 255) - (surrounding ushr shift and 255)) <= 1
            }
            if (!matchesSurface) failures += "$mode: header gutter=$header surrounding sheet=$surrounding"
            savePreview("subposts-$mode")
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(9)
            compose.onNodeWithText("回复示例 8：保持轻盈的阅读层次。").assertIsDisplayed()
            compose.onNodeWithText(label).assertIsNotDisplayed()
            savePreview("subposts-$mode-scrolled")
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithText(label).assertIsDisplayed()
            measurements += "$mode: count visible initially, hidden after scroll, visible after returning to top"
        }
        File(context.getExternalFilesDir(null), "subposts-scroll-v5/count-measurements.txt")
            .writeText(measurements.joinToString("\n", postfix = "\n"))
        compose.onNodeWithContentDescription(context.getString(R.string.btn_close)).performClick()
        compose.runOnIdle { assertEquals(1, closed); assertEquals(state, model.uiState.value) }
        assertTrue("The reply count must not paint a full-width contrasting strip: $failures", failures.isEmpty())
    }

    @Test
    fun pageColorsSeparateLightCardsButPreserveNightAndTransparentThemes() {
        assertNotEquals(colors(false).card, colors(false).contentPageBackground)
        assertEquals(colors(true).background, colors(true).contentPageBackground)
        val translucent = colors(false).copy(theme = ThemeUtil.THEME_TRANSLUCENT, background = Color.Transparent)
        assertEquals(Color.Transparent, translucent.contentPageBackground)
        val dynamicNight = colors(true).copy(floorCard = colors(true).card)
        assertNotEquals(dynamicNight.card, dynamicNight.contentInsetBackground)
        assertEquals(colors(false).card, colors(false).contentCardBackground)
        assertEquals(colors(false).floorCard, colors(false).contentInsetBackground)
        val transparentNight = colors(true).copy(
            theme = ThemeUtil.THEME_TRANSLUCENT_DARK,
            card = Color.Black.copy(alpha = 0.125f),
            floorCard = Color.Black.copy(alpha = 0.2f),
        )
        assertEquals(transparentNight.card.alpha, transparentNight.contentCardBackground.alpha, 0.001f)
        assertEquals(transparentNight.floorCard.alpha, transparentNight.contentInsetBackground.alpha, 0.001f)
        val transparentLight = colors(false).copy(
            theme = ThemeUtil.THEME_TRANSLUCENT_LIGHT,
            background = Color.Transparent,
            card = Color.White.copy(alpha = 0.125f),
        )
        assertEquals(Color.Transparent, transparentLight.contentPageBackground)
        assertEquals(transparentLight.card, transparentLight.contentCardBackground)
    }

    @Test
    fun outlinesSeparateLowContrastPalettesInBothThemes() {
        val palette = mutableStateOf(colors(true))
        compose.setContent {
            Preview(palette.value.isNightMode, palette.value) {
                Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(96.dp).contentCard().testTag("contrast-card"))
                }
            }
        }
        val measurements = mutableListOf<String>()
        val variants = listOf(
            "blue" to colors(true).copy(background = Color(0xFF131D28), card = Color(0xFF15202B)),
            "grey" to colors(true).copy(background = Color(0xFF202020), card = Color(0xFF15202B)),
            "amoled" to colors(true).copy(background = Color.Black, card = Color(0xFF101010)),
            "equal-surfaces" to colors(true).copy(background = Color(0xFF202020)),
            "light" to colors(false),
            "lilac-light" to colors(false).copy(
                background = Color(0xFFFCF8FF), card = Color(0xFFFCF8FF), text = Color(0xFF211B2B),
            ),
            "warm-light" to colors(false).copy(
                background = Color(0xFFFDF5E8), card = Color(0xFFFDF5E8), text = Color(0xFF30291F),
            ),
        )
        for ((name, colors) in variants) {
            compose.runOnIdle { palette.value = colors }
            val node = compose.onNodeWithTag("contrast-card")
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertEquals(with(compose.density) { 336.dp.toPx() }, bounds.width, 1f)
            assertEquals(with(compose.density) { 96.dp.toPx() }, bounds.height, 1f)
            val bitmap = node.captureToImage().asAndroidBitmap()
            val x = bitmap.width / 2
            val edge = with(compose.density) { 0.5.dp.toPx().toInt() }
            val fill = Color(bitmap.getPixel(x, bitmap.height / 2))
            val top = Color(bitmap.getPixel(x, edge))
            val bottom = Color(bitmap.getPixel(x, bitmap.height - 1 - edge))
            assertEquals(colors.contentCardBackground.toArgb(), fill.toArgb())
            if (colors.isNightMode) {
                assertTrue("$name card fill should be brighter", fill.luminance() > colors.card.luminance())
                assertTrue("$name outline should visibly separate the card", top.luminance() > fill.luminance() + 0.01f)
                assertTrue("$name lower edge should remain visible", bottom.luminance() > fill.luminance())
                assertTrue("$name highlight should fade downward", top.luminance() > bottom.luminance() + 0.01f)
            } else {
                assertEquals(colors.card.toArgb(), fill.toArgb())
                val previousPage = lerp(colors.background, colors.text.copy(alpha = 1f), 0.035f)
                assertTrue("$name page should separate cards more clearly", colors.contentPageBackground.luminance() < previousPage.luminance() - 0.015f)
                assertTrue("$name lower rim should be visible", bottom.luminance() < fill.luminance() - 0.03f)
                assertTrue("$name bright edge should fade into the lower rim", top.luminance() > bottom.luminance() + 0.04f)
            }
            assertEquals(colors.contentPageBackground.toArgb(), bitmap.getPixel(0, 0))
            measurements += "$name: fill=${fill.toArgb()}, top=${top.toArgb()}, bottom=${bottom.toArgb()}, " +
                    "fillLuminance=${fill.luminance()}, topLuminance=${top.luminance()}, bottomLuminance=${bottom.luminance()}, " +
                    "page=${colors.contentPageBackground.toArgb()}, pageLuminance=${colors.contentPageBackground.luminance()}, bounds=$bounds"
            savePreview("outline-$name")
        }
        File(context.getExternalFilesDir(null), "subposts-scroll-v5/contrast-measurements.txt")
            .writeText(measurements.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun homeForumCardsKeepLongPressMenusInBothThemes() {
        val dark = mutableStateOf(false)
        val model = HomeViewModel()
        val state = HomeUiState(
            isLoading = false,
            forums = persistentListOf(
                HomeUiState.Forum("", "-11", "数码交流", true, "12"),
                HomeUiState.Forum("", "-12", "摄影分享", false, "8"),
                HomeUiState.Forum("", "-13", "日常生活", true, "10"),
                HomeUiState.Forum("", "-14", "软件体验", false, "6"),
            ),
        )
        // Synthetic screen data only: no account persistence or refresh intent.
        BaseViewModel::class.java.getDeclaredField("uiState").apply {
            isAccessible = true
            set(model, MutableStateFlow(state))
        }
        compose.setContent { Preview(dark.value) { HomePage(viewModel = model) } }
        compose.onNodeWithText("数码交流").assertIsDisplayed()
        savePreview("home-light")
        compose.onNodeWithText("数码交流").performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.title_copy_forum_name)).assertIsDisplayed()
        Espresso.pressBack()
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("软件体验").assertIsDisplayed()
        savePreview("home-dark")
        assertEquals(state, model.uiState.value)
    }

    @Test
    fun feedCardKeepsOpenReplyAndLikeActionsInsideRoundedSurface() {
        val dark = mutableStateOf(false)
        var opened = 0
        var replies = 0
        var likes = 0
        val item = ThreadInfo(
            id = -21L, threadId = -21L, title = "卡片化首页 · 第一版",
            author = author, forumName = "界面预览", replyNum = 12, agreeNum = 31,
            richAbstract = listOf(PbContent(text = "内容使用实底圆角卡片，底部继续保留透明悬浮操作。")),
        ).wrapImmutable()
        compose.setContent {
            Preview(dark.value) {
                Column {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).contentCard()) {
                        FeedCard(item, { opened++ }, { likes++ }, onClickReply = { replies++ })
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).contentCard()) {
                        Card(content = { Text("卡片之间保留留白，不再用粗分割线切开内容。") })
                    }
                }
            }
        }
        compose.onNodeWithText("卡片化首页 · 第一版", substring = true).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.desc_comment)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.desc_like)).performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), listOf(opened, replies, likes)) }
        savePreview("feed-light")
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("卡片化首页 · 第一版", substring = true).assertIsDisplayed()
        savePreview("feed-dark")
    }

    @Test
    fun postCardsKeepNestedRepliesAndImmersiveReading() {
        val dark = mutableStateOf(false)
        val immersive = mutableStateOf(false)
        var replies = 0
        var likes = 0
        var openedSubPost: Long? = null
        val body = "正文与头像对齐，保留阅读宽度。楼中楼使用更轻的内嵌卡片。"
        val post = Post(id = -31L, floor = 2, author = author, sub_post_number = 3).wrapImmutable()
        val subPost = SubPostList(id = -32L, author = author)
        compose.setContent {
            Preview(dark.value) {
                Column {
                    PostCard(
                        postHolder = Post(id = -30L, floor = 1, title = "帖子详情 · 卡片化预览", author = author).wrapImmutable(),
                        contentRenders = persistentListOf(TextContentRender("主帖与回复各自成卡，悬浮底栏保持原来的玻璃效果。")),
                        immersiveMode = immersive.value,
                    )
                    Text("全部回复", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    Box(Modifier.fillMaxWidth().testTag("post")) {
                        PostCard(
                            postHolder = post,
                            contentRenders = persistentListOf(TextContentRender(body)),
                            subPosts = persistentListOf(SubPostItemData(subPost.wrapImmutable(), AnnotatedString("示例回复：内嵌内容清晰分层。"), false)),
                            immersiveMode = immersive.value,
                            onAgree = { likes++ },
                            onReplyClick = { replies++ },
                            onOpenSubPosts = { openedSubPost = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(body).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.title_agree)).performClick()
        compose.onNodeWithText(context.getString(R.string.open_all_sub_posts, 3)).performClick()
        compose.runOnIdle {
            assertEquals(1, replies)
            assertEquals(1, likes)
            assertEquals(0L, openedSubPost)
        }
        val image = compose.onNodeWithTag("post").captureToImage().asAndroidBitmap()
        val x = with(compose.density) { 13.dp.roundToPx() }
        val y = with(compose.density) { 5.dp.roundToPx() }
        assertEquals(colors(false).contentPageBackground.toArgb(), image.getPixel(0, image.height / 2))
        assertEquals(colors(false).contentPageBackground.toArgb(), image.getPixel(x, y))
        assertEquals(colors(false).card.toArgb(), image.getPixel(image.width / 2, y))
        savePreview("thread-light")
        compose.runOnIdle { dark.value = true }
        savePreview("thread-dark")
        compose.runOnIdle { immersive.value = true }
        compose.onNodeWithText(body).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.open_all_sub_posts, 3)).assertDoesNotExist()
        val flat = compose.onNodeWithTag("post").captureToImage().asAndroidBitmap()
        assertEquals(colors(true).background.toArgb(), flat.getPixel(flat.width / 2, 0))
        savePreview("thread-immersive")
    }

    @Composable
    private fun Preview(dark: Boolean, palette: ExtendedColors = colors(dark), content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalExtendedColors provides palette, LocalAccount provides null) {
            MaterialTheme(colors = getColorPalette(dark, palette)) {
                ProvideNavigator(EmptyDestinationsNavigator) {
                    ProvideContentColor(palette.text) {
                        Box(Modifier.width(360.dp).height(640.dp).background(palette.contentPageBackground).testTag("preview")) {
                            Box(Modifier.fillMaxSize()) { content() }
                        }
                    }
                }
            }
        }
    }

    private fun savePreview(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // Android's native ripple runs on the render thread, not Compose's test clock.
        SystemClock.sleep(400)
        val bitmap = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "subposts-scroll-v5").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
            onTopBarSecondary = text, onTopBarActive = text,
            bottomBar = background, bottomBarSurface = inset, onBottomBarSurface = text,
            windowBackground = background, divider = inset, shadow = Color.Transparent,
            placeholder = inset, indicator = inset, unselected = text,
        )
    }
}
