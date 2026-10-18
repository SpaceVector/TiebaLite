package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.arch.BaseViewModel
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getColorPalette
import com.huanchengfly.tieba.post.ui.models.ThreadItemData
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.main.LocalMainNavigationBottomPadding
import com.huanchengfly.tieba.post.ui.page.main.explore.ExplorePage
import com.huanchengfly.tieba.post.ui.page.main.explore.hot.HotUiState
import com.huanchengfly.tieba.post.ui.page.main.explore.hot.HotViewModel
import com.huanchengfly.tieba.post.ui.page.main.explore.personalized.PersonalizedUiState
import com.huanchengfly.tieba.post.ui.page.main.explore.personalized.PersonalizedViewModel
import com.huanchengfly.tieba.post.ui.page.main.home.EmptyScreen
import com.huanchengfly.tieba.post.ui.page.main.home.HomePage
import com.huanchengfly.tieba.post.ui.page.main.home.HomeUiState
import com.huanchengfly.tieba.post.ui.page.main.home.HomeViewModel
import com.huanchengfly.tieba.post.ui.page.main.notifications.NotificationsPage
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.AtMeListViewModel
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.NotificationsListUiState
import com.huanchengfly.tieba.post.ui.page.main.notifications.list.ReplyMeListViewModel
import com.huanchengfly.tieba.post.ui.page.main.user.UserPage
import com.huanchengfly.tieba.post.ui.page.main.user.UserUiState
import com.huanchengfly.tieba.post.ui.page.main.user.UserViewModel
import com.huanchengfly.tieba.post.ui.page.settings.SettingsPage
import com.huanchengfly.tieba.post.ui.page.thread.ThreadBottomBar
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.video.DefaultVideoPlayerController
import com.huanchengfly.tieba.post.ui.widgets.compose.video.VideoPlayer
import com.huanchengfly.tieba.post.ui.widgets.compose.video.VideoPlayerSource
import com.huanchengfly.tieba.post.ui.widgets.compose.video.VideoPlayerState
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Android frame clock: Compose's test clock would hide idle animation work. */
@SdkSuppress(minSdkVersion = 31)
class AppPerformanceTest {
    @Test
    fun appIdleScrollAndVideoLifecycle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "app-performance-v17").apply { mkdirs() }
        val media = File(directory, "silent.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("perf_silent.mp4").use { source ->
            media.outputStream().use { source.copyTo(it) }
        }
        val scene = mutableStateOf("blank")
        val motionOnly = InstrumentationRegistry.getArguments().getString("phase") == "motion"
        val idleFollowUp = InstrumentationRegistry.getArguments().getString("phase") == "idle-follow-up"
        val glassEnabled = mutableStateOf(true)
        val dark = mutableStateOf(false)
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val list = LazyListState()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var animationScope: CoroutineScope? = null
        val threads = (1..100).map { index ->
            ThreadInfo(id = -17404000L - index, threadId = -17404000L - index,
                title = "性能检查 $index", replyNum = 12,
                richAbstract = listOf(PbContent(text = "固定本地内容，测量卡片和悬浮底栏，不访问帖子接口。")))
        }
        fun <S : UiState> seed(model: BaseViewModel<*, *, S, *>, value: S) {
            model.initialized = true
            BaseViewModel::class.java.getDeclaredField("uiState").apply {
                isAccessible = true; set(model, MutableStateFlow(value))
            }
        }
        val home = HomeViewModel().apply { seed(this, HomeUiState(isLoading = false,
            forums = persistentListOf(HomeUiState.Forum("", "-17404001", "性能检查", true, "8")))) }
        val models = listOf(home,
            PersonalizedViewModel().apply { seed(this, PersonalizedUiState(isRefreshing = false,
                data = threads.take(20).map { ThreadItemData(it.wrapImmutable(), blocked = false, hidden = false) }.toImmutableList())) },
            HotViewModel().apply { seed(this, HotUiState(isRefreshing = false, threadList = threads.take(20).map { it.wrapImmutable() }.toImmutableList())) },
            ReplyMeListViewModel().apply { seed(this, NotificationsListUiState(isRefreshing = false, hasMore = false)) },
            AtMeListViewModel().apply { seed(this, NotificationsListUiState(isRefreshing = false, hasMore = false)) },
            UserViewModel().apply { seed(this, UserUiState(isLoading = false)) })
        val frames = mutableListOf<LongArray>()
        val frameThread = HandlerThread("perf-frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            synchronized(frames) {
                frames += longArrayOf(frame.getMetric(FrameMetrics.TOTAL_DURATION),
                    frame.getMetric(FrameMetrics.DRAW_DURATION), frame.getMetric(FrameMetrics.GPU_DURATION),
                    frame.getMetric(FrameMetrics.DEADLINE))
            }
        }
        val results = JSONArray()
        val failures = mutableListOf<String>()
        var video: DefaultVideoPlayerController? = null
        File(directory, "stage.txt").writeText("launching performance Activity\n")
        val scenario = ActivityScenario.launch<ComponentActivity>(
            Intent(context, ComponentActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        fun save() = File(directory, when {
            motionOnly -> "measurements-motion.json"
            idleFollowUp -> "measurements-idle-follow-up.json"
            else -> "measurements.json"
        }).writeText(JSONObject().apply {
            put("sdk", Build.VERSION.SDK_INT); put("build", "debug; screen/risk check, not release or energy benchmark")
            put("clock", "real Android frame clock; no ComposeTestRule")
            put("results", results); put("flags", JSONArray(failures))
        }.toString(2))
        fun percentile(values: List<Long>, fraction: Double): Double =
            if (values.isEmpty()) 0.0 else values.sorted()[((values.size - 1) * fraction).toInt()] / 1_000_000.0
        fun probe(label: String, milliseconds: Long = 2000, action: (() -> Unit)? = null): JSONObject {
            synchronized(frames) { frames.clear() }
            val cpu = Process.getElapsedCpuTime(); val start = SystemClock.elapsedRealtime()
            if (action == null) SystemClock.sleep(milliseconds) else action()
            val elapsed = SystemClock.elapsedRealtime() - start; val used = Process.getElapsedCpuTime() - cpu
            val sample = synchronized(frames) { frames.toList() }
            val result = JSONObject().apply {
                put("label", label); put("elapsedMs", elapsed); put("cpuMs", used)
                put("oneCoreCpuPercent", 100.0 * used / elapsed); put("frames", sample.size)
                put("framesPerSecond", sample.size * 1000.0 / elapsed)
                put("totalP95Ms", percentile(sample.map { it[0] }, .95))
                put("drawP95Ms", percentile(sample.map { it[1] }, .95))
                put("gpuP95Ms", percentile(sample.map { it[2] }.filter { it >= 0 }, .95))
                put("missedDeadline", sample.count { it[3] > 0 && it[0] > it[3] })
                put("rawFramesNs", JSONArray(sample.map { JSONArray(it.toList()) }))
            }
            results.put(result); save(); return result
        }
        fun show(name: String, settle: Long = 2000) {
            File(directory, "stage.txt").writeText("showing $name\n")
            scenario.onActivity { scene.value = name }
            SystemClock.sleep(settle)
        }
        fun capture(name: String) {
            val done = CountDownLatch(1)
            var bitmap: Bitmap? = null
            var result = PixelCopy.ERROR_UNKNOWN
            scenario.onActivity { activity ->
                bitmap = Bitmap.createBitmap(activity.window.decorView.width,
                    activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(activity.window, bitmap!!, { result = it; done.countDown() },
                    Handler(activity.mainLooper))
            }
            assertTrue("Capture $name", done.await(3, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS)
            File(directory, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap!!.recycle()
        }
        try {
            scenario.onActivity { activity ->
                models.forEach { model ->
                    ViewModelProvider(owner, object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(model)!!
                    })[model.javaClass]
                }
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(frameThread.looper))
                activity.setContent {
                    val frameScope = rememberCoroutineScope()
                    SideEffect { animationScope = frameScope }
                    val palette = palette(dark.value)
                    CompositionLocalProvider(LocalExtendedColors provides palette, LocalAccount provides null,
                        LocalViewModelStoreOwner provides owner, LocalMainNavigationBottomPadding provides 80.dp) {
                        MaterialTheme(colors = getColorPalette(dark.value, palette)) {
                            ProvideNavigator(EmptyDestinationsNavigator) {
                                val backdrop = rememberGlassBackdrop(glassEnabled.value)
                                Box(Modifier.fillMaxSize().background(palette.contentPageBackground)) {
                                    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop, palette.contentPageBackground)) {
                                        key(scene.value) {
                                            when (scene.value) {
                                                "home" -> HomePage(viewModel = home)
                                                "explore" -> ExplorePage()
                                                "messages" -> NotificationsPage()
                                                "user" -> UserPage()
                                                "settings" -> SettingsPage(EmptyDestinationsNavigator)
                                                "empty" -> StateScreen(true, false, false) {}
                                                "loading" -> StateScreen(true, false, true) {}
                                                "home-empty" -> EmptyScreen(false, false) {}
                                                "server" -> ErrorTipScreen(ErrorType.SERVER, "本地性能检查")
                                                "unknown" -> ErrorTipScreen(ErrorType.UNKNOWN, "本地性能检查")
                                                "video" -> video?.let { VideoPlayer(it) }
                                                "cards" -> LazyColumn(state = list, contentPadding = PaddingValues(bottom = 88.dp)) {
                                                    items(threads.size) { index ->
                                                        Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).contentCard()) {
                                                            FeedCard(threads[index].wrapImmutable(), {}, {})
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (scene.value !in listOf("blank", "video")) {
                                        ThreadBottomBar({}, {}, {}, Modifier.align(Alignment.BottomCenter), backdrop = backdrop)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SystemClock.sleep(2500)
            probe("blank-control")
            val idleScenes = if (idleFollowUp) listOf("messages" to 3500L, "user" to 3500L,
                "home" to 2500L, "blank" to 2000L)
                else listOf("home" to 2500L, "explore" to 2500L, "messages" to 8500L,
                    "user" to 3500L, "settings" to 2000L, "empty" to 4000L, "home-empty" to 7500L,
                    "server" to 11500L, "unknown" to 9500L)
            if (!motionOnly) for ((name, settle) in idleScenes) {
                show(name, settle)
                val result = probe("idle-$name")
                if (result.getDouble("framesPerSecond") > 2.0) failures += "$name keeps drawing while idle"
                if (name in listOf("messages", "empty", "home-empty", "server", "unknown")) capture("settled-$name")
            }
            if (!motionOnly) {
                show("loading")
                val loading = probe("loading-positive-control")
                if (loading.getInt("frames") < 10) failures += "Real-frame positive control failed"
            }
            if (idleFollowUp) {
                assertTrue("Settled idle risks: $failures", failures.isEmpty())
                return
            }
            show("cards")
            for (night in listOf(false, true)) {
                scenario.onActivity { dark.value = night }
                for (glass in listOf(false, true, true, false, false, true)) {
                    scenario.onActivity { glassEnabled.value = glass }
                    val ready = CountDownLatch(1)
                    scenario.onActivity { animationScope!!.launch { list.scrollToItem(0) }.invokeOnCompletion { ready.countDown() } }
                    assertTrue(ready.await(3, TimeUnit.SECONDS)); SystemClock.sleep(700)
                    val done = CountDownLatch(1)
                    probe("scroll-${if (night) "dark" else "light"}-${if (glass) "glass" else "plain"}") {
                        scenario.onActivity { animationScope!!.launch { list.animateScrollBy(2200f, tween(1600, easing = LinearEasing)) }
                            .invokeOnCompletion { done.countDown() } }
                        assertTrue(done.await(4, TimeUnit.SECONDS))
                    }
                }
                SystemClock.sleep(1000)
                val result = probe("idle-cards-${if (night) "dark" else "light"}")
                if (result.getDouble("framesPerSecond") > 2.0) failures += "Card/glass keeps drawing while idle"
            }
            scenario.onActivity {
                video = DefaultVideoPlayerController(context, VideoPlayerState(), scope).apply {
                    setSource(VideoPlayerSource.Network(media.toURI().toString())); play(false)
                }
            }
            show("video", 2500)
            var playingBefore = false
            scenario.onActivity { playingBefore = video!!.currentState { it.isPlaying } }
            assertTrue("Local media must actually play before lifecycle check", playingBefore)
            scenario.moveToState(Lifecycle.State.CREATED); SystemClock.sleep(600)
            val background = probe("background-video-host-stopped")
            scenario.onActivity { background.put("videoStillPlaying", video!!.currentState { it.isPlaying }) }
            if (background.getBoolean("videoStillPlaying")) failures += "Video keeps playing after host STOP"
            scenario.onActivity {
                video!!.play(false)
                video!!.startLongPressSpeedPlay()
                video!!.pause()
                video!!.stopLongPressSpeedPlay()
                val stopped = video!!.currentState { !it.isPlaying && !it.isLongPressSpeeding && it.playbackSpeed == 1f }
                background.put("longPressStopAndLateReleaseStayPaused", stopped)
                if (!stopped) failures += "Long-press release restarts stopped video"
            }
            save()
        } finally {
            // Finish directly: some ROMs block AndroidX's second background EmptyActivity launch.
            scenario.onActivity {
                video?.release(); it.window.removeOnFrameMetricsAvailableListener(listener)
                owner.viewModelStore.clear(); it.finish()
            }
            val deadline = SystemClock.elapsedRealtime() + 3000
            while (scenario.state != Lifecycle.State.DESTROYED && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            scenario.close(); scope.cancel(); frameThread.quitSafely(); save()
        }
        assertTrue("Performance/resource risks: $failures", failures.isEmpty())
    }

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
