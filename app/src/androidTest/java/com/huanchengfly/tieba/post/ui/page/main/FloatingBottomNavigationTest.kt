package com.huanchengfly.tieba.post.ui.page.main

import android.os.Build
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.glassBackdrop
import com.huanchengfly.tieba.post.ui.widgets.compose.glassBackdropSource
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlassSurface
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberGlassBackdrop
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalAnimationGraphicsApi::class)
class FloatingBottomNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectionReselectionAndBadgeKeepWorking() {
        val selected = mutableStateOf(0)
        var reselections = 0
        var messageClicks = 0
        compose.setContent {
            MaterialTheme {
                BottomNavigation(
                    currentPosition = selected.value,
                    onChangePosition = { selected.value = it },
                    onReselected = { reselections++ },
                    navigationItems = items(onMessageClick = { messageClicks++ }),
                    themeColors = colors(dark = true),
                )
            }
        }
        compose.onNodeWithText("首页").assertIsSelected()
        compose.onNodeWithText("发现").assertIsDisplayed()
        compose.onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("消息").performClick().assertIsSelected()
        compose.onNodeWithText("消息").performClick()
        compose.runOnIdle {
            assertEquals(2, selected.value)
            assertEquals(1, reselections)
            assertEquals(2, messageClicks)
        }
    }

    @Test
    fun threeTabsWorkWithoutExplore() {
        val selected = mutableStateOf(0)
        compose.setContent {
            MaterialTheme {
                BottomNavigation(
                    currentPosition = selected.value,
                    onChangePosition = { selected.value = it },
                    onReselected = {},
                    navigationItems = items().filter { it.id != "发现" }.toImmutableList(),
                    themeColors = colors(dark = false),
                )
            }
        }
        compose.onNodeWithText("我的").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(2, selected.value) }
    }

    @Test
    fun floatingBarRendersInBothThemesAndLastItemClearsIt() {
        val dark = mutableStateOf(true)
        compose.setContent {
            val palette = colors(dark.value)
            val accent = Color(0xFF26B6A6)
            MaterialTheme(colors = if (dark.value) darkColors(secondary = accent) else lightColors(secondary = accent)) {
                val backdrop = rememberGlassBackdrop(enabled = true)
                MyScaffold(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .testTag("preview"),
                    backgroundColor = palette.background,
                    bottomBar = {
                        Box(Modifier.testTag("navigation")) {
                            BottomNavigation(0, {}, {}, items(), palette, backdrop)
                        }
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassBackdropSource(backdrop)
                            .background(palette.background)
                            .testTag("list"),
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    ) {
                        items(12) { index ->
                            Column(
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth()
                                    .background(palette.bottomBar)
                                    .padding(16.dp)
                                    .testTag("item$index")
                            ) {
                                Text("贴吧 Lite · 示例内容 ${index + 1}", color = palette.text)
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .background(if (index % 2 == 0) accent else Color(0xFFE0A764))
                                )
                            }
                        }
                    }
                }
            }
        }
        savePreview("dark")
        compose.runOnIdle { dark.value = false }
        savePreview("light")
        compose.onNodeWithTag("list").performScrollToIndex(11)
        val last = compose.onNodeWithTag("item11").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Last item must scroll above the floating bar", last.bottom <= bar.top + 1f)
    }

    @Test
    fun backdropReallyBlursAndTracksBackgroundChanges() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val tint = mutableStateOf(Color.Red)
        compose.setContent {
            val backdrop = rememberGlassBackdrop(enabled = true)
            assertNotNull(backdrop)
            Box(Modifier.size(240.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .glassBackdropSource(backdrop)
                        .drawBehind {
                            val stripe = 4.dp.toPx()
                            drawRect(tint.value)
                            for (index in 0..(size.width / stripe).toInt() step 2) {
                                drawRect(Color.White, Offset(index * stripe, 0f), Size(stripe, size.height))
                            }
                        }
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 200.dp, height = 64.dp)
                        .clip(CircleShape)
                        .glassBackdrop(backdrop)
                        .testTag("glass")
                )
            }
        }
        fun centerPixel(): Color {
            val pixels = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        val red = centerPixel()
        assertTrue("Blur must blend red and white stripes: $red", red.red > 0.9f && red.green in 0.25f..0.75f)
        compose.runOnIdle { tint.value = Color.Blue }
        val blue = centerPixel()
        assertTrue("Blur must update rather than retaining an old frame: $blue", blue.blue > 0.9f && blue.red in 0.25f..0.75f)
    }

    @Test
    fun glassTransmitsContentAndEachCapsuleSamplesItsOwnPosition() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val dark = mutableStateOf(true)
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            Box(Modifier.size(width = 320.dp, height = 160.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .glassBackdropSource(backdrop)
                        .drawBehind {
                            drawRect(Color.Red, size = Size(size.width / 2, size.height))
                            drawRect(Color.Blue, topLeft = Offset(size.width / 2, 0f))
                        }
                )
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (tag in listOf("leftGlass", "rightGlass")) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .floatingGlassSurface(backdrop, colors(dark.value))
                                .testTag(tag)
                        )
                    }
                }
            }
        }
        val measurements = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "floating-navigation-v2/transmission.txt",
        ).apply { parentFile!!.mkdirs(); writeText("") }
        for (night in listOf(true, false)) {
            compose.runOnIdle { dark.value = night }
            fun center(tag: String): Color {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                return pixels[pixels.width / 2, pixels.height / 2]
            }
            val left = center("leftGlass")
            val right = center("rightGlass")
            measurements.appendText("night=$night left=$left right=$right\n")
            assertTrue("Red content must remain clearly visible through the left capsule: $left", left.red - left.blue > 0.50f)
            assertTrue("Blue content must remain visible through its own capsule: $right", right.blue - right.red > 0.50f)
        }
    }

    private fun savePreview(name: String) {
        val image = compose.onNodeWithTag("preview").captureToImage().asAndroidBitmap()
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "floating-navigation-v2",
        ).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Composable
    private fun items(onMessageClick: () -> Unit = {}) =
        listOf(
            "首页" to R.drawable.ic_animated_rounded_inventory_2,
            "发现" to R.drawable.ic_animated_toy_fans,
            "消息" to R.drawable.ic_animated_rounded_notifications,
            "我的" to R.drawable.ic_animated_rounded_person,
        ).map { (title, icon) ->
            NavigationItem(
                id = title,
                icon = { AnimatedImageVector.animatedVectorResource(icon) },
                title = { title },
                badge = title == "消息",
                badgeText = "12",
                onClick = if (title == "消息") onMessageClick else null,
            )
        }.toImmutableList()

    private fun colors(dark: Boolean) = ExtendedColors(
        theme = "test",
        isNightMode = dark,
        bottomBar = if (dark) Color(0xFF202020) else Color(0xFFF8F8F8),
        text = if (dark) Color.White else Color(0xFF202020),
        unselected = if (dark) Color(0xFFE0E0E0) else Color(0xFF555555),
        background = if (dark) Color(0xFF121212) else Color(0xFFEDEDED),
    )
}
