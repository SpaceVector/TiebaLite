package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.panpf.sketch.request.PauseLoadWhenScrollingDrawableDecodeInterceptor
import com.github.panpf.sketch.sketch
import com.huanchengfly.tieba.post.api.models.protos.Abstract
import com.huanchengfly.tieba.post.api.models.protos.Media
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.abstractText
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.thread.ThreadBottomBar
import com.huanchengfly.tieba.post.utils.appPreferences
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real feed cards/native frame clock; generated local images only, no API requests/settings changes. */
@SdkSuppress(minSdkVersion = 31)
class FeedScrollPerformanceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get() = File(context.getExternalFilesDir(null), "feed-scroll-performance-v22").apply { mkdirs() }

    @Test
    fun abstractFactoryAllocationAndOutput() {
        val output = JSONArray()
        for (count in listOf(4, 16, 64)) {
            val parts = List(count) { PbContent(text = "本地  刷帖  摘要 $it\n") }
            val thread = ThreadInfo(richAbstract = parts)
            val post = PostInfoList(rich_abstract = parts)
            val abstracts = parts.map { Abstract(text = it.text) }
            val expected = parts.joinToString("") { it.text.replace(Regex(" {2,}"), " ") }
            for ((name, operation) in linkedMapOf("thread" to { thread.abstractText },
                "post" to { post.abstractText }, "abstract-list" to { abstracts.abstractText })) {
                assertTrue("Summary semantics", operation() == expected)
                repeat(10) { operation() }
                val samples = JSONArray()
                repeat(3) {
                    val bytes = allocated(); val cpu = Debug.threadCpuTimeNanos()
                    var checksum = 0
                    repeat(100) { checksum += operation().length }
                    val usedCpu = Debug.threadCpuTimeNanos() - cpu; val usedBytes = allocated() - bytes
                    samples.put(JSONObject().put("calls", 100).put("threadCpuNs", usedCpu)
                        .put("javaAllocatedBytes", usedBytes).put("checksum", checksum))
                }
                output.put(JSONObject().put("model", name).put("fragments", count).put("text", operation()).put("samples", samples))
            }
        }
        save("abstract-factory.json", JSONObject().put("results", output))
    }

    @Test
    fun feedCardsScrollRecomposeAndMemory() {
        val fixtureDirectory = File(context.cacheDir, "feed-scroll-performance-v22-fixtures").apply { mkdirs() }
        assertTrue("Dedicated fixture folder must be empty", fixtureDirectory.listFiles().orEmpty().isEmpty())
        val images = List(12) { index ->
            File(fixtureDirectory, "image-$index.jpg").also { file ->
                val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.rgb(45 + index * 12, 90 + index * 7, 140))
                    canvas.drawRect(80f + index * 8, 100f, 740f, 520f, Paint().apply { color = android.graphics.Color.rgb(220, 190, 80 + index * 10) })
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                } finally { bitmap.recycle() }
            }
        }
        save("image-fixtures.json", JSONObject().put("width", 960).put("height", 640).put("jpegQuality", 95)
            .put("files", JSONArray(images.map { JSONObject().put("name", it.name).put("bytes", it.length())
                .put("sha256", MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) }) })))
        val dark = mutableStateOf(false)
        val scene = mutableStateOf("text")
        val generation = mutableIntStateOf(0)
        val updated = mutableStateOf(false)
        val list = LazyListState()
        var scope: CoroutineScope? = null
        var firstCard: Rect? = null
        val parts = List(16) { PbContent(text = "第${it}段  本地刷帖  正文，保持原来的卡片、字重和换行。") }
        val textRows = List(40) { ThreadInfo(id = -22000L - it, threadId = -22000L - it,
            title = "本地刷帖对照 $it", richAbstract = parts, replyNum = 12).wrapImmutable() }
        val imageRows = textRows.mapIndexed { index, item -> item.get().copy(media = List(3) { offset ->
            val uri = Uri.fromFile(images[(index * 3 + offset) % images.size]).toString()
            Media(type = 3, srcPic = uri, bigPic = uri, originPic = uri, width = 960, height = 640)
        }).wrapImmutable() }
        val post = PostInfoList(thread_id = -22001L, title = "个人动态摘要", rich_abstract = parts, user_name = "本地用户")
        val frameThread = HandlerThread("feed-v22-frame-metrics").apply { start() }
        val frames = mutableListOf<LongArray>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> synchronized(frames) {
            frames += longArrayOf(metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.DRAW_DURATION),
                metrics.getMetric(FrameMetrics.GPU_DURATION), metrics.getMetric(FrameMetrics.DEADLINE))
        } }
        val results = JSONArray()
        val memory = JSONArray()
        val checks = linkedMapOf<String, Boolean>()
        val scenario = ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        fun memory(label: String) {
            val runtime = Runtime.getRuntime(); val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info)
            val sketch = context.sketch
            memory.put(JSONObject().put("label", label).put("javaUsedBytes", runtime.totalMemory() - runtime.freeMemory())
                .put("nativeAllocatedBytes", Debug.getNativeHeapAllocatedSize()).put("totalPssKiB", info.totalPss)
                .put("imageCacheBytes", sketch.memoryCache.size).put("imageCacheMaxBytes", sketch.memoryCache.maxSize)
                .put("bitmapPoolBytes", sketch.bitmapPool.size).put("bitmapPoolMaxBytes", sketch.bitmapPool.maxSize))
            checks["$label-cache-bounded"] = sketch.memoryCache.size <= sketch.memoryCache.maxSize && sketch.bitmapPool.size <= sketch.bitmapPool.maxSize
        }
        fun run(action: suspend () -> Unit) {
            val done = CountDownLatch(1)
            scenario.onActivity { scope!!.launch { try { action() } finally { done.countDown() } } }
            assertTrue("Feed operation completed", done.await(8, TimeUnit.SECONDS))
        }
        fun reset() { run { list.scrollToItem(0) }; SystemClock.sleep(400) }
        fun probe(label: String, action: () -> Unit) {
            synchronized(frames) { frames.clear() }
            val bytes = allocated(); val cpu = Process.getElapsedCpuTime(); val start = SystemClock.elapsedRealtime()
            action(); SystemClock.sleep(80)
            val elapsed = SystemClock.elapsedRealtime() - start; val usedCpu = Process.getElapsedCpuTime() - cpu
            val usedBytes = allocated() - bytes; val sample = synchronized(frames) { frames.toList() }
            checks["$label-native-frames"] = sample.size >= 12
            results.put(JSONObject().put("label", label).put("elapsedMs", elapsed).put("cpuMs", usedCpu)
                .put("javaAllocatedBytes", usedBytes).put("visibleIndex", list.firstVisibleItemIndex)
                .put("rawFramesNs", JSONArray(sample.map { JSONArray(it.toList()) })))
        }
        fun capture(name: String) {
            val done = CountDownLatch(1); var bitmap: Bitmap? = null; var result = PixelCopy.ERROR_UNKNOWN
            scenario.onActivity { activity ->
                val rect = firstCard!!
                bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
                PixelCopy.request(activity.window, rect, bitmap!!, { result = it; done.countDown() }, Handler(activity.mainLooper))
            }
            assertTrue(done.await(3, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS)
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
                                        key(scene.value) {
                                            if (scene.value != "blank") MyLazyColumn(state = list,
                                                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)) {
                                                val single = scene.value.startsWith("recompose")
                                                items(if (single) 1 else 40, key = { it }, contentType = { "feed-card" }) { index ->
                                                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).contentCard()
                                                        .then(if (index == 0) Modifier.onGloballyPositioned {
                                                            val bounds = it.boundsInWindow()
                                                            firstCard = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
                                                        } else Modifier)) {
                                                        if (scene.value == "recompose-post") {
                                                            val item = remember(generation.intValue, updated.value) {
                                                                post.copy(agree_num = generation.intValue,
                                                                    rich_abstract = if (updated.value) listOf(PbContent(text = "更新后的摘要，必须显示新内容。")) else parts).wrapImmutable()
                                                            }
                                                            FeedCard(item, {}, {})
                                                        } else {
                                                            val item = if (single) remember(generation.intValue, updated.value) {
                                                                textRows[0].get().copy(agreeNum = generation.intValue,
                                                                    richAbstract = if (updated.value) listOf(PbContent(text = "更新后的摘要，必须显示新内容。")) else parts).wrapImmutable()
                                                            } else (if (scene.value == "images") imageRows else textRows)[index]
                                                            FeedCard(item, {}, {})
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    ThreadBottomBar({}, {}, {}, Modifier.align(Alignment.BottomCenter), backdrop = backdrop, themeColors = palette)
                                }
                            }
                        }
                    }
                }
            }
            SystemClock.sleep(1000)
            memory("initial")
            for (name in listOf("recompose-thread", "recompose-post")) {
                scenario.onActivity { scene.value = name; generation.intValue = 0; updated.value = false }
                reset()
                run { repeat(10) { withFrameNanos { generation.intValue++ } } }
                repeat(2) { repetition -> probe("$name-$repetition") { run {
                    repeat(24) { withFrameNanos { generation.intValue++ } }
                } } }
                scenario.onActivity { updated.value = true }
                SystemClock.sleep(400)
                capture("$name-updated")
            }
            for (night in listOf(false, true)) for (name in listOf("text", "images")) {
                scenario.onActivity { dark.value = night; scene.value = name; updated.value = false }
                reset()
                val label = (if (night) "dark-" else "light-") + name
                run { list.animateScrollBy(2800f, tween(650, easing = LinearEasing)) }
                reset()
                if (name == "text") capture(label)
                memory("$label-warm")
                repeat(2) { repetition ->
                    probe("$label-$repetition") { run { list.animateScrollBy(2800f, tween(1000, easing = LinearEasing)) } }
                    checks["$label-$repetition-progress"] = list.firstVisibleItemIndex > 0
                    SystemClock.sleep(300)
                    memory("$label-round-$repetition")
                    reset()
                }
            }
            val ownKeys = context.sketch.memoryCache.keys().filter {
                context.sketch.memoryCache[it]?.imageUri?.contains(fixtureDirectory.absolutePath) == true
            }
            checks["local-images-decoded"] = ownKeys.isNotEmpty()
            save("image-cache-details.json", JSONObject().put("ownEntries", ownKeys.size).put("hideMedia", context.appPreferences.hideMedia)
                .put("entries", JSONArray(ownKeys.mapNotNull { context.sketch.memoryCache[it]?.let { value ->
                    JSONObject().put("image", File(java.net.URI(value.imageUri)).name).put("imageInfo", value.imageInfo.toString())
                        .put("bitmapWidth", value.countBitmap.bitmap?.width ?: 0).put("bitmapHeight", value.countBitmap.bitmap?.height ?: 0)
                } })))
            scenario.onActivity { scene.value = "blank" }
            SystemClock.sleep(500)
            memory("after-list-disposed")
            checks["decode-pause-released"] = !PauseLoadWhenScrollingDrawableDecodeInterceptor.scrolling
            save("feed-scroll.json", JSONObject().put("checks", JSONObject().apply { checks.forEach { (key, value) -> put(key, value) } })
                .put("results", results).put("memory", memory).put("interpretation", "Short native-clock debug comparison, not release FPS, leak proof or energy estimate"))
            assertTrue("Feed scroll: $checks", checks.values.all { it })
        } finally {
            try {
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener); it.finish() }
                scenario.close()
            } finally {
                scope = null
                frameThread.quitSafely(); frameThread.join(3000)
                memory("after-activity-closed")
                val cache = context.sketch.memoryCache
                val keys = cache.keys().filter { cache[it]?.imageUri?.contains(fixtureDirectory.absolutePath) == true }
                keys.forEach { key -> cache.remove(key)?.let { value -> context.sketch.resultCache.remove(value.requestCacheKey) } }
                images.forEach { assertTrue("Own image fixture removed", it.delete()) }
                assertTrue("Own fixture directory removed", fixtureDirectory.delete())
                memory("after-own-fixtures-cleaned")
                save("exit-memory.json", JSONObject().put("memory", memory).put("onlyOwnFixtureCacheEntriesRemoved", keys.size))
            }
        }
    }

    @Test
    fun pureTextScrollFollowUp() {
        val dark = mutableStateOf(false)
        val list = LazyListState()
        var scope: CoroutineScope? = null
        val parts = List(16) { PbContent(text = "第${it}段  本地刷帖  正文，保持原来的卡片、字重和换行。") }
        val rows = List(40) { ThreadInfo(id = -22000L - it, threadId = -22000L - it,
            title = "本地刷帖对照 $it", richAbstract = parts, replyNum = 12).wrapImmutable() }
        val frameThread = HandlerThread("feed-v22-followup-frames").apply { start() }
        val handler = Handler(frameThread.looper)
        val frames = mutableListOf<LongArray>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> synchronized(frames) {
            frames += longArrayOf(metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.DRAW_DURATION),
                metrics.getMetric(FrameMetrics.GPU_DURATION), metrics.getMetric(FrameMetrics.DEADLINE))
        } }
        val results = JSONArray()
        val checks = linkedMapOf<String, Boolean>()
        val scenario = ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        fun run(action: suspend () -> Unit) {
            val done = CountDownLatch(1)
            scenario.onActivity { scope!!.launch { try { action() } finally { done.countDown() } } }
            assertTrue("Followup operation completed", done.await(8, TimeUnit.SECONDS))
        }
        fun reset() { run { list.scrollToItem(0) }; SystemClock.sleep(150) }
        fun drainFrameCallbacks() {
            val done = CountDownLatch(1)
            handler.post { done.countDown() }
            assertTrue("Frame callbacks drained", done.await(3, TimeUnit.SECONDS))
        }
        try {
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, handler)
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
                                            items(rows.size, key = { it }, contentType = { "feed-card" }) { index ->
                                                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).contentCard()) {
                                                    FeedCard(rows[index], {}, {})
                                                }
                                            }
                                        }
                                    }
                                    ThreadBottomBar({}, {}, {}, Modifier.align(Alignment.BottomCenter), backdrop = backdrop, themeColors = palette)
                                }
                            }
                        }
                    }
                }
            }
            SystemClock.sleep(1000)
            for (night in listOf(false, true)) {
                scenario.onActivity { dark.value = night }
                reset()
                repeat(3) {
                    run { list.animateScrollBy(2800f, tween(650, easing = LinearEasing)) }
                    reset()
                }
                repeat(2) { repetition ->
                    drainFrameCallbacks()
                    synchronized(frames) { frames.clear() }
                    val bytes = allocated(); val cpu = Process.getElapsedCpuTime(); val start = SystemClock.elapsedRealtime()
                    run { list.animateScrollBy(2800f, tween(1000, easing = LinearEasing)) }
                    SystemClock.sleep(80)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    val usedCpu = Process.getElapsedCpuTime() - cpu; val usedBytes = allocated() - bytes
                    drainFrameCallbacks()
                    val sample = synchronized(frames) { frames.toList() }
                    val label = (if (night) "dark-text-" else "light-text-") + repetition
                    checks["$label-native-frames"] = sample.size >= 12
                    checks["$label-progress"] = list.firstVisibleItemIndex > 0
                    results.put(JSONObject().put("label", label).put("elapsedMs", elapsed).put("cpuMs", usedCpu)
                        .put("javaAllocatedBytes", usedBytes).put("visibleIndex", list.firstVisibleItemIndex)
                        .put("rawFramesNs", JSONArray(sample.map { JSONArray(it.toList()) })))
                    reset()
                }
            }
            save("pure-text-followup.json", JSONObject()
                .put("phase", InstrumentationRegistry.getArguments().getString("phase"))
                .put("warmRoundsPerPalette", 3).put("results", results)
                .put("checks", JSONObject().apply { checks.forEach { (name, value) -> put(name, value) } }))
            assertTrue("Pure text scroll: $checks", checks.values.all { it })
        } finally {
            try {
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener); it.finish() }
                scenario.close()
            } finally {
                scope = null
                frameThread.quitSafely(); frameThread.join(3000)
            }
        }
    }

    private fun allocated() = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
    private fun save(name: String, value: JSONObject) = File(directory, name).writeText(value.toString(2))

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
