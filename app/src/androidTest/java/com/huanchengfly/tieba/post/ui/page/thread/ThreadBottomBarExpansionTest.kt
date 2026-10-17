package com.huanchengfly.tieba.post.ui.page.thread

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.glassBackdropSource
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberGlassBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ThreadBottomBarExpansionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun capsuleExpandsInPlaceWithoutMovingArticleAndCanDismiss() {
        val expanded = mutableStateOf(false)
        val dark = mutableStateOf(true)
        val height = mutableStateOf(520.dp)
        val showReply = mutableStateOf(true)
        val listState = LazyListState(1, 18)
        compose.setContent {
            ExpandingPreview(dark.value, expanded.value, { expanded.value = it }, height.value, showReply.value, listState)
        }
        val more = label(R.string.btn_more)
        val close = label(R.string.btn_close)
        val agree = label(R.string.title_agree)
        val initialBar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val anchor = compose.onNodeWithContentDescription(agree).fetchSemanticsNode().boundsInRoot
        val initialScroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        savePreview("collapsed-dark")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(more).performClick()
        compose.mainClock.advanceTimeBy(144)
        val middle = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        savePreview("opening-144ms")
        compose.mainClock.advanceTimeBy(400)
        val openBar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val openAnchor = compose.onNodeWithContentDescription(agree).fetchSemanticsNode().boundsInRoot
        val openMenu = compose.onNodeWithTag("menu").fetchSemanticsNode().boundsInRoot
        val menuWidth = with(compose.density) { 280.dp.toPx() }
        assertTrue("Opening must interpolate rather than replacing the bar", middle.height > initialBar.height && middle.height < openBar.height)
        assertEquals("Expanded actions should stay narrow for one-handed use", menuWidth, openMenu.width, 1f)
        assertTrue("Expanded actions should stay on the right", openMenu.left > initialBar.width * 0.25f)
        assertEquals("The like button must keep its bottom anchor", anchor.bottom, openAnchor.bottom, 1f)
        assertEquals("The like button must keep its right anchor", anchor.right, openAnchor.right, 1f)
        assertEquals(initialBar.bottom, openBar.bottom, 1f)
        assertEquals(initialScroll, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        savePreview("expanded-dark")
        compose.mainClock.autoAdvance = true

        compose.runOnIdle { dark.value = false }
        savePreview("expanded-light")
        compose.runOnIdle { showReply.value = false }
        compose.onNodeWithText(label(R.string.tip_reply_thread)).assertDoesNotExist()
        compose.onNodeWithContentDescription(close).assertIsDisplayed()
        compose.runOnIdle { showReply.value = true; height.value = 260.dp }
        compose.onNodeWithText(label(R.string.title_report)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(close).assertIsDisplayed()
        savePreview("expanded-compact")
        compose.runOnIdle { height.value = 520.dp }
        compose.onNodeWithContentDescription(close).performClick()
        compose.onNodeWithContentDescription(more).assertIsDisplayed()
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithTag("outside").performTouchInput { click(Offset(8f, 8f)) }
        compose.onNodeWithContentDescription(more).assertIsDisplayed()
        compose.onNodeWithContentDescription(more).performClick()
        Espresso.pressBack()
        compose.onNodeWithContentDescription(more).assertIsDisplayed()
        assertEquals(initialScroll, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        File(previewDirectory(), "layout-measurements.txt").writeText(
            "collapsed=$initialBar\nopening144ms=$middle\nexpanded=$openBar\nmenu=$openMenu\n" +
                    "likeBefore=$anchor\nlikeAfter=$openAnchor\narticleScrollBefore=$initialScroll\n" +
                    "articleScrollAfter=${listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset}\n"
        )
    }

    @Test
    fun menuKeepsAllCallbacksAndAuthorOnlyDelete() {
        val owner = mutableStateOf(false)
        val counts = IntArray(9)
        compose.setContent {
            MaterialTheme(colors = darkColors()) {
                ThreadMoreMenu(
                    isSeeLz = true,
                    isCollected = true,
                    isImmersiveMode = false,
                    isDesc = false,
                    canDelete = { owner.value },
                    onSeeLzClick = { counts[0]++ },
                    onCollectClick = { counts[1]++ },
                    onImmersiveModeClick = { counts[2]++ },
                    onDescClick = { counts[3]++ },
                    onJumpPageClick = { counts[4]++ },
                    onShareClick = { counts[5]++ },
                    onCopyLinkClick = { counts[6]++ },
                    onReportClick = { counts[7]++ },
                    onDeleteClick = { counts[8]++ },
                    themeColors = colors(true),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                )
            }
        }
        compose.onNodeWithText(label(R.string.title_see_lz)).assertIsSelected()
        compose.onNodeWithText(label(R.string.title_collected)).assertIsSelected()
        for (id in listOf(R.string.title_see_lz, R.string.title_collected, R.string.title_pure_read,
            R.string.title_sort, R.string.title_jump_page, R.string.title_share,
            R.string.title_copy_link, R.string.title_report)) {
            compose.onNodeWithText(label(id)).performScrollTo().performClick()
        }
        compose.onNodeWithText(label(R.string.title_delete)).assertDoesNotExist()
        compose.runOnIdle { owner.value = true }
        compose.onNodeWithText(label(R.string.title_delete)).performScrollTo().performClick()
        compose.runOnIdle { for (count in counts) assertEquals(1, count) }
    }

    @Composable
    private fun ExpandingPreview(
        dark: Boolean,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        height: Dp,
        showReply: Boolean,
        listState: LazyListState,
    ) {
        val palette = colors(dark)
        val dismiss = { onExpandedChange(false) }
        BackHandler(enabled = expanded, onBack = dismiss)
        MaterialTheme(colors = if (dark) darkColors() else lightColors()) {
            val backdrop = rememberGlassBackdrop()
            MyScaffold(
                modifier = Modifier.fillMaxWidth().height(height).testTag("preview"),
                backgroundColor = palette.background,
                topBar = {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        Text("帖子详情 · 一体式展开", color = palette.text)
                    }
                },
                bottomBar = { ThreadBottomBarSpacer(liftUpBottomBar = true) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop, palette.background),
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    ) {
                        items(12) { index ->
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("${index + 1} 楼 · 帖子内容", color = palette.text)
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier.fillMaxWidth().height(80.dp)
                                        .background(if (index % 2 == 0) Color(0xFF26B6A6) else Color(0xFFE0A764))
                                )
                            }
                        }
                    }
                    if (expanded) {
                        Box(
                            Modifier.fillMaxSize().testTag("outside").clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = dismiss,
                            )
                        )
                    }
                    ThreadBottomBar(
                        onClickReply = dismiss,
                        onAgree = {},
                        onClickMore = { onExpandedChange(!expanded) },
                        showReply = showReply,
                        agreeNum = 131,
                        liftUpBottomBar = true,
                        themeColors = palette,
                        backdrop = backdrop,
                        menuExpanded = expanded,
                        modifier = Modifier.align(Alignment.BottomCenter).testTag("bar"),
                        moreContent = {
                            ThreadMoreMenu(false, true, false, false, { false },
                                dismiss, dismiss, dismiss, dismiss, dismiss, dismiss, dismiss, dismiss, dismiss,
                                modifier = Modifier.testTag("menu"),
                                themeColors = palette)
                        },
                    )
                }
            }
        }
    }

    private fun savePreview(name: String) {
        val image = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        File(previewDirectory(), "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun previewDirectory() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "thread-bottom-bar-expand",
    ).apply { mkdirs() }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun colors(dark: Boolean) = ExtendedColors(
        theme = "test",
        isNightMode = dark,
        accent = Color(0xFF26B6A6),
        bottomBar = if (dark) Color(0xFF202020) else Color(0xFFF8F8F8),
        text = if (dark) Color.White else Color(0xFF202020),
        background = if (dark) Color(0xFF101010) else Color(0xFFF8F8F8),
    )
}
