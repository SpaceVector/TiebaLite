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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThreadBottomBarExpansionTest {
    @get:Rule
    val compose = createComposeRule()

    private var reportDirectoryName = "thread-bottom-bar-expand"

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
    fun replyCapsuleFadesAndReversesSmoothlyWithMoreMenu() {
        reportDirectoryName = "thread-reply-motion-v15"
        val expanded = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val listState = LazyListState(1, 18)
        var replies = 0
        compose.setContent {
            ExpandingPreview(dark.value, expanded.value, { expanded.value = it }, 520.dp, true, listState,
                onReply = { replies++ })
        }
        val measurements = mutableListOf<String>()
        val replyLabel = label(R.string.tip_reply_thread)
        fun px(dp: Float) = with(compose.density) { dp.dp.toPx() }
        fun leftReply() = compose.onAllNodesWithText(replyLabel).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f }.minBy { it.boundsInRoot.left }
        fun distance(a: Int, b: Int) = listOf(0, 8, 16).sumOf { shift -> abs(((a ushr shift) and 255) - ((b ushr shift) and 255)) }
        fun advanceFrames(milliseconds: Long) {
            repeat(((milliseconds + 15) / 16).toInt()) {
                compose.mainClock.advanceTimeByFrame()
                // Size-dependent transitions also need Android measure/draw, not just clock time.
                compose.waitForIdle()
            }
        }
        try {
            compose.mainClock.autoAdvance = false
            for (night in listOf(false, true)) {
                compose.runOnIdle { dark.value = night; expanded.value = false }
                advanceFrames(500)
                val name = if (night) "dark" else "light"
                val initial = leftReply()
                val collapsed = initial.boundsInRoot
                val originalBar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
                val anchor = compose.onNodeWithContentDescription(label(R.string.title_agree)).fetchSemanticsNode().boundsInRoot
                val scroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                val previewOrigin = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
                // Sample empty glass above the text over colored content, left of the expanded capsule.
                fun sample(image: Bitmap) = image.getPixel(px(80f).toInt(), (collapsed.center.y - px(18f) - previewOrigin.top).toInt())
                val collapsedImage = savePreview("$name-closed")
                // Subcomposed, size-dependent transitions need several layout frames to start.
                // Record that separately; sample fade/motion from the first transformed frame.
                fun toggleAndStart(description: String, opening: Boolean): Long {
                    compose.onNodeWithContentDescription(description).performClick()
                    val start = compose.mainClock.currentTime
                    repeat(10) {
                        advanceFrames(16)
                        val nodes = compose.onAllNodesWithText(replyLabel).fetchSemanticsNodes()
                        val node = if (opening) nodes.firstOrNull { it.id == initial.id }
                            else nodes.filter { it.boundsInRoot.width > 0f }.minByOrNull { it.boundsInRoot.left }
                        val bounds = node?.boundsInRoot
                        if (bounds != null && bounds.height > 0f &&
                            bounds.height < collapsed.height - 0.01f) {
                            return compose.mainClock.currentTime - start
                        }
                    }
                    error("Reply transition did not start within 160ms of frame-clock propagation")
                }
                val openingStartup = toggleAndStart(label(R.string.btn_more), opening = true)
                advanceFrames(96)
                val outgoing = compose.onNode(SemanticsMatcher("Original reply capsule") { it.id == initial.id })
                outgoing.assertExists().assertIsNotEnabled()
                val opening = outgoing.fetchSemanticsNode().boundsInRoot
                val openingImage = savePreview("$name-opening-96ms")
                assertTrue("Exit is a gentle contraction, not an 80ms disappearance: collapsed=$collapsed opening=$opening", opening.left > collapsed.left + px(0.5f) && opening.height < collapsed.height)
                advanceFrames(400)
                outgoing.assertDoesNotExist()
                val expandedImage = savePreview("$name-expanded")
                val menu = compose.onNodeWithTag("menu").fetchSemanticsNode().boundsInRoot
                assertEquals(px(280f), menu.width, 1f)
                val expandedAnchor = compose.onNodeWithContentDescription(label(R.string.title_agree)).fetchSemanticsNode().boundsInRoot
                assertEquals(anchor.right, expandedAnchor.right, 1f)
                assertEquals(anchor.bottom, expandedAnchor.bottom, 1f)
                assertEquals(originalBar.bottom, compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot.bottom, 1f)
                val fullDifference = distance(sample(collapsedImage), sample(expandedImage))
                val outgoingDifference = distance(sample(openingImage), sample(expandedImage))
                assertTrue("Glass fade is visible at 96ms, not already gone: full=$fullDifference exit=$outgoingDifference", fullDifference > 6 && outgoingDifference > 1 && outgoingDifference < fullDifference)
                val closingStartup = toggleAndStart(label(R.string.btn_close), opening = false)
                advanceFrames(64)
                val incoming64 = leftReply().boundsInRoot
                assertTrue("Entry starts before the old 140ms delay", incoming64.left > collapsed.left + px(0.5f) && incoming64.height < collapsed.height)
                val incoming64Image = savePreview("$name-closing-64ms")
                advanceFrames(64)
                val incoming128 = leftReply().boundsInRoot
                val incoming128Image = savePreview("$name-closing-128ms")
                assertTrue("Entry moves back toward its original bounds", incoming128.left < incoming64.left && incoming128.height > incoming64.height)
                val firstDifference = distance(sample(incoming64Image), sample(expandedImage))
                val secondDifference = distance(sample(incoming128Image), sample(expandedImage))
                assertTrue("Entry fades in progressively without a blank delay: full=$fullDifference first=$firstDifference second=$secondDifference", firstDifference > 1 && secondDifference > firstDifference && secondDifference < fullDifference)
                compose.onNodeWithContentDescription(label(R.string.btn_more)).performClick()
                advanceFrames(16)
                val reversedOut = leftReply().boundsInRoot
                assertTrue("Rapid reopen continues from the current pose", abs(reversedOut.left - incoming128.left) <= px(6f))
                savePreview("$name-reopen-first-frame")
                advanceFrames(96)
                val beforeReverseIn = leftReply().boundsInRoot
                compose.onNodeWithContentDescription(label(R.string.btn_close)).performClick()
                advanceFrames(16)
                val reversedIn = leftReply().boundsInRoot
                assertTrue("Rapid reclose continues from the current pose", abs(reversedIn.left - beforeReverseIn.left) <= px(6f))
                savePreview("$name-reclose-first-frame")
                advanceFrames(400)
                val restored = leftReply().boundsInRoot
                assertEquals(collapsed.left, restored.left, 1f)
                assertEquals(collapsed.top, restored.top, 1f)
                assertEquals(collapsed.width, restored.width, 1f)
                assertEquals(collapsed.height, restored.height, 1f)
                assertEquals(originalBar.bottom, compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot.bottom, 1f)
                assertEquals(scroll, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
                compose.onNodeWithText(replyLabel).assertIsEnabled().performClick()
                compose.runOnIdle { assertEquals(if (night) 2 else 1, replies) }
                savePreview("$name-restored")
                measurements += "$name: frameClockStartup open=$openingStartup close=$closingStartup; samples relative to first transformed frame; collapsed=$collapsed; opening96=$opening; closing64=$incoming64; closing128=$incoming128; reverseOut=$reversedOut; reverseIn=$reversedIn; restored=$restored; glassDifference full=$fullDifference exit96=$outgoingDifference enter64=$firstDifference enter128=$secondDifference; menuWidth=${menu.width}; articleScroll=$scroll; anchors unchanged; outgoing disabled; restored reply callback once"
            }
        } finally {
            compose.mainClock.autoAdvance = true
            File(previewDirectory(), "motion-measurements.txt").writeText(measurements.joinToString("\n", postfix = "\n"))
        }
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
        onReply: () -> Unit = { onExpandedChange(false) },
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
                        onClickReply = onReply,
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

    private fun savePreview(name: String): Bitmap {
        val image = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        File(previewDirectory(), "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }

    private fun previewDirectory() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        reportDirectoryName,
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
