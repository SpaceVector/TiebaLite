package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.PixelCopy
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.ui.common.PbContentText
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.thread.ThreadBottomBar
import com.huanchengfly.tieba.post.utils.EmoticonManager
import com.huanchengfly.tieba.post.utils.EmoticonUtil
import com.huanchengfly.tieba.post.utils.EmoticonUtil.emoticonString
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared reader text, real frame clock, synthetic content only; no thread requests/history writes. */
@SdkSuppress(minSdkVersion = 31)
class ReadingPerformanceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get() = File(context.getExternalFilesDir(null), "reading-performance-v20").apply { mkdirs() }

    @Test
    fun onlyUsedEmoticonsArePreparedAndAnnotationsSurvive() {
        val checks = linkedMapOf<String, Boolean>()
        val details = linkedMapOf<String, Any>()
        val first = EmoticonManager.getEmoticonIdByName("呵呵")!!
        val second = EmoticonManager.getEmoticonIdByName("滑稽")!!
        val fixtures = linkedMapOf(
            "plain" to AnnotatedString("没有表情的正文和评论，不需要任何图片占位。"),
            "two-used" to "#(呵呵) 重复 #(呵呵) 与 #(滑稽)".emoticonString,
            "unknown" to buildAnnotatedString {
                append("保留未知表情文本")
                addStringAnnotation(EmoticonUtil.INLINE_CONTENT_TAG, "Emoticon#offline_unknown", 0, length)
            },
            "links" to buildAnnotatedString {
                append("离线链接")
                addStringAnnotation("url", "https://offline.invalid/reading", 0, length)
                addStringAnnotation(EmoticonUtil.INLINE_CONTENT_TAG, "link_icon", 0, 1)
            }
        )
        val expected = mapOf("plain" to emptySet(), "two-used" to setOf("Emoticon#$first", "Emoticon#$second"),
            "unknown" to emptySet(), "links" to emptySet())
        val full = EmoticonManager.getEmoticonInlineContent(42f)
        checks["registry-initialized"] = full.size > 2
        for ((name, text) in fixtures) {
            repeat(3) { EmoticonManager.getEmoticonInlineContent(42f, text) }
            val startAllocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
            val startCpu = Debug.threadCpuTimeNanos()
            var prepared = 0
            repeat(100) { prepared += EmoticonManager.getEmoticonInlineContent(42f, text).size }
            val cpu = Debug.threadCpuTimeNanos() - startCpu
            val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - startAllocated
            val selected = EmoticonManager.getEmoticonInlineContent(42f, text)
            checks["$name-only-used"] = selected.keys == expected.getValue(name)
            checks["$name-placeholder-identical"] = expected.getValue(name).all { selected[it]?.placeholder == full[it]?.placeholder }
            details[name] = mapOf("registrySize" to full.size, "calls" to 100, "preparedEntries" to prepared,
                "javaAllocatedBytes" to allocated, "threadCpuNs" to cpu)
        }
        val marked = "正文 #(呵呵) #(滑稽) #（保留） #(未知测试表情)\n末尾".emoticonString
        checks["parser-idempotent"] = marked.emoticonString === marked
        val annotated = buildAnnotatedString {
            append("#(呵呵) 离线链接 @用户")
            addStringAnnotation("url", "https://offline.invalid", 7, 11)
            addStringAnnotation("user", "-20", 12, length)
        }
        val parsed = annotated.emoticonString
        checks["existing-annotations-preserved"] = parsed.text == annotated.text &&
            parsed.getStringAnnotations("url", 0, parsed.length) == annotated.getStringAnnotations("url", 0, annotated.length) &&
            parsed.getStringAnnotations("user", 0, parsed.length) == annotated.getStringAnnotations("user", 0, annotated.length)
        checks["parser-emoticon-annotation"] = parsed.getStringAnnotations(EmoticonUtil.INLINE_CONTENT_TAG, 0, parsed.length)
            .single().let { it.start == 0 && it.end == 5 && it.item == "Emoticon#$first" }
        save("text-factory.json", JSONObject()
            .put("checks", JSONObject().apply { checks.forEach { (key, value) -> put(key, value) } })
            .put("details", JSONObject().apply { details.forEach { (key, value) -> put(key, JSONObject.wrap(value)) } }))
        assertTrue("Reader text: $checks", checks.values.all { it })
    }

    @Test
    fun readingListLayoutAndScrollProbe() {
        val dark = mutableStateOf(false)
        val mixed = mutableStateOf(false)
        val list = LazyListState()
        var scope: CoroutineScope? = null
        var firstCard: Rect? = null
        val layouts = ConcurrentHashMap<Int, JSONObject>()
        val frameThread = HandlerThread("reading-frame-metrics").apply { start() }
        val frames = mutableListOf<LongArray>()
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            synchronized(frames) {
                frames += longArrayOf(frame.getMetric(FrameMetrics.TOTAL_DURATION), frame.getMetric(FrameMetrics.DRAW_DURATION),
                    frame.getMetric(FrameMetrics.GPU_DURATION), frame.getMetric(FrameMetrics.DEADLINE))
            }
        }
        val results = JSONArray()
        val plain = List(120) { AnnotatedString("本地阅读样本 $it：正文、评论和楼中楼共用文字组件。保持原来的字重、行距和卡片，不请求真实帖子。再补充一行长文本观察换行。") }
        val withEmoticons = plain.map { text -> buildAnnotatedString {
            append("#(呵呵) #(滑稽) "); append(text)
            addStringAnnotation("url", "https://offline.invalid", length - 4, length)
        } }
        val scenario = ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        fun scroll(pixels: Float, duration: Int) {
            val finished = CountDownLatch(1)
            scenario.onActivity { scope!!.launch {
                try { list.animateScrollBy(pixels, tween(duration, easing = LinearEasing)) }
                finally { finished.countDown() }
            } }
            assertTrue("Reading scroll completed", finished.await(8, TimeUnit.SECONDS))
        }
        fun reset() {
            val done = CountDownLatch(1)
            scenario.onActivity { scope!!.launch { try { list.scrollToItem(0) } finally { done.countDown() } } }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            SystemClock.sleep(600)
        }
        fun capture(name: String) {
            val done = CountDownLatch(1)
            var bitmap: Bitmap? = null
            var status = PixelCopy.ERROR_UNKNOWN
            scenario.onActivity { activity ->
                val rect = firstCard!!
                bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
                PixelCopy.request(activity.window, rect, bitmap!!, { status = it; done.countDown() }, Handler(activity.mainLooper))
            }
            assertTrue(done.await(3, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS)
            try { File(directory, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap!!.recycle() }
        }
        try {
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(frameThread.looper))
                activity.setContent {
                    val coroutineScope = rememberCoroutineScope()
                    SideEffect { scope = coroutineScope }
                    val palette = palette(dark.value)
                    CompositionLocalProvider(LocalExtendedColors provides palette) {
                        MaterialTheme(colors = getColorPalette(dark.value, palette)) {
                            ProvideNavigator(EmptyDestinationsNavigator) {
                                val backdrop = rememberGlassBackdrop()
                                Box(Modifier.fillMaxSize().background(palette.contentPageBackground)) {
                                    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop, palette.contentPageBackground)) {
                                        MyLazyColumn(state = list, contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)) {
                                            items(plain.size, key = { it }, contentType = { "reading-text" }) { index ->
                                                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                                                    .contentCard().then(if (index == 0) Modifier.onGloballyPositioned {
                                                        val bounds = it.boundsInWindow()
                                                        firstCard = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
                                                    } else Modifier).padding(16.dp)) {
                                                    PbContentText(text = if (mixed.value) withEmoticons[index] else plain[index],
                                                        fontSize = 15.sp, lineSpacing = 0.8.sp, color = palette.text,
                                                        onTextLayout = { if (index < 3) layouts[index] = layout(it) })
                                                }
                                            }
                                        }
                                    }
                                    ThreadBottomBar(onClickReply = {}, onAgree = {}, onClickMore = {},
                                        modifier = Modifier.align(Alignment.BottomCenter), backdrop = backdrop, themeColors = palette)
                                }
                            }
                        }
                    }
                }
            }
            SystemClock.sleep(1200)
            for (night in listOf(false, true)) for (emoticons in listOf(false, true)) {
                scenario.onActivity { dark.value = night; mixed.value = emoticons }
                reset()
                val label = (if (night) "dark" else "light") + if (emoticons) "-mixed" else "-plain"
                assertTrue("Current reader text laid out", layouts.size >= 2 && layouts[0]?.getString("text") ==
                    (if (emoticons) withEmoticons[0] else plain[0]).text)
                save("layout-$label.json", JSONObject(layouts.mapKeys { it.key.toString() }))
                if (emoticons) capture(label)
                scroll(1000f, 450)
                reset()
                repeat(2) { repetition ->
                    synchronized(frames) { frames.clear() }
                    val cpu = Process.getElapsedCpuTime()
                    val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                    val start = SystemClock.elapsedRealtime()
                    scroll(2200f, 1100)
                    SystemClock.sleep(80)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val usedCpu = Process.getElapsedCpuTime() - cpu
                    val bytes = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - allocated
                    val sample = synchronized(frames) { frames.toList() }
                    assertTrue("Native frames and visible progression: $label", sample.size >= 15 && list.firstVisibleItemIndex > 0)
                    results.put(JSONObject().put("label", label).put("repetition", repetition).put("elapsedMs", elapsed)
                        .put("cpuMs", usedCpu).put("javaAllocatedBytes", bytes)
                        .put("rawFramesNs", JSONArray(sample.map { JSONArray(it.toList()) })))
                    reset()
                }
            }
            save("reading-scroll.json", JSONObject().put("sdk", Build.VERSION.SDK_INT).put("clock", "native Android; debug build")
                .put("results", results).put("interpretation", "Short diagnostic samples, not release FPS or power measurements"))
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener); it.finish() }
            scenario.close()
            frameThread.quitSafely()
            frameThread.join(3000)
        }
    }

    private fun layout(value: TextLayoutResult) = JSONObject().put("text", value.layoutInput.text.text)
        .put("width", value.size.width).put("height", value.size.height).put("lines", value.lineCount)
        .put("placeholderRects", JSONArray(value.placeholderRects.map { rect ->
            rect?.let { listOf(it.left, it.top, it.right, it.bottom) }
        }))

    private fun save(name: String, data: JSONObject) = File(directory, name).writeText(data.toString(2))

    private fun palette(dark: Boolean): ExtendedColors {
        val text = if (dark) Color(0xFFEAEAEA) else Color(0xFF202020)
        val page = if (dark) Color(0xFF101010) else Color.White
        val card = if (dark) Color(0xFF202020) else Color.White
        val inset = if (dark) Color(0xFF2A2A2A) else Color(0xFFF0F1F3)
        return ExtendedColors(theme = "test", isNightMode = dark,
            primary = Color(0xFF16A99B), accent = Color(0xFF16A99B), onPrimary = Color.White, onAccent = Color.White,
            text = text, textSecondary = text.copy(alpha = .65f), textDisabled = text.copy(alpha = .35f),
            background = page, card = card, floorCard = inset, chip = inset, onChip = text,
            topBar = page, topBarSurface = inset, onTopBar = text, onTopBarSurface = text,
            onTopBarSecondary = text, onTopBarActive = text, bottomBar = page, bottomBarSurface = inset,
            onBottomBarSurface = text, windowBackground = page, divider = inset, shadow = Color.Transparent,
            placeholder = inset, indicator = inset, unselected = text)
    }
}
