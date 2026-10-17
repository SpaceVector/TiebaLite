package com.huanchengfly.tieba.post.ui.page.thread

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
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
import kotlin.math.max

class ThreadBottomBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun replyAgreeMoreAndExistingGestureInsetsKeepWorking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val replyLabel = context.getString(R.string.tip_reply_thread)
        val agreeLabel = context.getString(R.string.title_agree)
        val moreLabel = context.getString(R.string.btn_more)
        val agreed = mutableStateOf(false)
        val showReply = mutableStateOf(true)
        val lifted = mutableStateOf(false)
        var replies = 0
        var agrees = 0
        var menus = 0
        var navigationInset = 0
        var minimumLift = 0f
        var rowHeight = 0f
        compose.setContent {
            val density = LocalDensity.current
            navigationInset = WindowInsets.navigationBars.getBottom(density)
            minimumLift = with(density) { 16.dp.toPx() }
            rowHeight = with(density) { 72.dp.toPx() }
            MaterialTheme {
                ThreadBottomBar(
                    onClickReply = { replies++ },
                    onAgree = { agrees++; agreed.value = !agreed.value },
                    onClickMore = { menus++ },
                    showReply = showReply.value,
                    hasAgreed = agreed.value,
                    agreeNum = 131,
                    liftUpBottomBar = lifted.value,
                    themeColors = colors(dark = false),
                    modifier = Modifier.testTag("threadBar"),
                )
            }
        }
        compose.onNodeWithText(replyLabel).performClick()
        compose.onNodeWithContentDescription(agreeLabel).performClick().assertIsSelected()
        compose.onNodeWithContentDescription(moreLabel).performClick()
        val normalHeight = compose.onNodeWithTag("threadBar").fetchSemanticsNode().boundsInRoot.height
        assertEquals(rowHeight + navigationInset, normalHeight, 1f)
        compose.runOnIdle { lifted.value = true; showReply.value = false }
        compose.onNodeWithText(replyLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(agreeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(moreLabel).assertIsDisplayed()
        val liftedHeight = compose.onNodeWithTag("threadBar").fetchSemanticsNode().boundsInRoot.height
        assertEquals(rowHeight + max(navigationInset.toFloat(), minimumLift), liftedHeight, 1f)
        compose.runOnIdle {
            assertEquals(1, replies)
            assertEquals(1, agrees)
            assertEquals(1, menus)
        }
    }

    @Test
    fun threadCapsulesRenderBothThemesAndLastReplyClearsThem() {
        val dark = mutableStateOf(true)
        compose.setContent {
            val palette = colors(dark.value)
            MaterialTheme(colors = if (dark.value) darkColors() else lightColors()) {
                val backdrop = rememberGlassBackdrop()
                MyScaffold(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .testTag("threadPreview"),
                    backgroundColor = palette.background,
                    topBar = {
                        Box(
                            Modifier.fillMaxWidth().height(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("帖子详情 · 效果测试", color = palette.text)
                        }
                    },
                    bottomBar = {
                        ThreadBottomBar(
                            onClickReply = {},
                            onAgree = {},
                            onClickMore = {},
                            agreeNum = 131,
                            liftUpBottomBar = true,
                            themeColors = palette,
                            backdrop = backdrop,
                            modifier = Modifier.testTag("threadBar"),
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                            .glassBackdropSource(backdrop, palette.background)
                            .testTag("replies"),
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    ) {
                        items(12) { index ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .testTag("reply$index")
                            ) {
                                Text("${index + 1} 楼 · 透明底栏测试", color = palette.text)
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .background(if (index % 2 == 0) Color(0xFF26B6A6) else Color(0xFFE0A764))
                                )
                            }
                        }
                    }
                }
            }
        }
        for (night in listOf(true, false)) {
            compose.runOnIdle { dark.value = night }
            val image = compose.onNodeWithTag("threadPreview").captureToImage().asAndroidBitmap()
            val directory = File(
                InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "floating-navigation-v2",
            ).apply { mkdirs() }
            File(directory, if (night) "thread-dark.png" else "thread-light.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("replies").performScrollToIndex(11)
        val last = compose.onNodeWithTag("reply11").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("threadBar").fetchSemanticsNode().boundsInRoot
        assertTrue("Last reply must scroll above both capsules", last.bottom <= bar.top + 1f)
    }

    private fun colors(dark: Boolean) = ExtendedColors(
        theme = "test",
        isNightMode = dark,
        accent = Color(0xFF26B6A6),
        bottomBar = if (dark) Color(0xFF202020) else Color(0xFFF8F8F8),
        text = if (dark) Color.White else Color(0xFF202020),
        background = if (dark) Color(0xFF101010) else Color(0xFFF8F8F8),
    )
}
