package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.panpf.sketch.request.PauseLoadWhenScrollingDrawableDecodeInterceptor
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UiResourceOwnershipTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun removingWebViewDestroysInstanceAndPreservesSavedHistory() {
        val visible = mutableStateOf(true)
        val current = AtomicReference<WebViewState>()
        val views = mutableListOf<TrackedWebView>()
        val checks = linkedMapOf<String, Boolean>()
        val details = linkedMapOf<String, Any>()
        val url = "https://offline.invalid/resource-check"
        var firstHistorySize = 0
        var firstHistoryUrl = ""
        try {
            compose.setContent {
                val holder = rememberSaveableStateHolder()
                if (visible.value) holder.SaveableStateProvider("web") {
                    val state = rememberSaveableWebViewState()
                    SideEffect { current.set(state) }
                    LaunchedEffect(state) {
                        if (state.viewState == null) state.content = WebContent.Data(
                            data = "<html><head><title>Offline resource check</title></head><body>offline</body></html>",
                            baseUrl = url, historyUrl = url, mimeType = "text/html", encoding = "utf-8"
                        )
                    }
                    WebView(
                        state = state,
                        modifier = Modifier.height(160.dp),
                        onCreated = { it.settings.blockNetworkLoads = true },
                        onDispose = { (it as TrackedWebView).events += "callback" },
                        factory = { context -> TrackedWebView(context).also { views += it } }
                    )
                }
            }
            compose.waitUntil(15_000) { current.get()?.loadingState == LoadingState.Finished }
            val firstState = current.get()
            compose.runOnIdle {
                checks["localPageLoaded"] = firstState.pageTitle == "Offline resource check" && views.size == 1
                val history = views.first().copyBackForwardList()
                firstHistorySize = history.size
                firstHistoryUrl = history.currentItem?.url.orEmpty()
                details["firstHistorySize"] = firstHistorySize
                details["firstHistoryUrl"] = firstHistoryUrl
                views.first().events.clear()
                visible.value = false
            }
            compose.waitForIdle()
            compose.runOnIdle {
                val first = views.first()
                checks["destroyedOnce"] = first.destroyCalls == 1
                checks["stateReleasesView"] = firstState.webView == null
                checks["callbackBeforeDestroy"] = first.events == listOf("callback", "stop", "destroy")
                checks["destroyedAfterDetach"] = first.destroyCalls == 1 && !first.destroyedWhileAttached
                visible.value = true
            }
            compose.waitUntil(10_000) { current.get() !== firstState && current.get()?.webView != null }
            compose.runOnIdle {
                val secondState = current.get()
                details["savedBundleKeys"] = secondState.viewState?.keySet()?.joinToString().orEmpty()
                details["secondHistorySize"] = views.last().copyBackForwardList().size
                details["secondHistoryUrl"] = views.last().copyBackForwardList().currentItem?.url.orEmpty()
                checks["savedHistoryRestored"] = secondState.viewState != null && views.size == 2 &&
                    views.last().copyBackForwardList().let {
                        firstHistorySize > 0 && it.size == firstHistorySize && it.currentItem?.url == firstHistoryUrl
                    }
                views.last().events.clear()
                visible.value = false
            }
            compose.waitForIdle()
            compose.runOnIdle {
                checks["everyInstanceDestroyedOnce"] = views.all { it.destroyCalls == 1 }
                checks["replacementStateReleasesView"] = current.get().webView == null
            }
            save("webview-release.json", checks)
            save("webview-history-details.json", details)
            assertTrue("WebView ownership: $checks", checks.values.all { it })
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.runOnIdle {
                // Clean the deliberately failing baseline as well, without touching cookies/cache.
                views.filter { it.destroyCalls == 0 }.forEach { it.destroy() }
                current.get()?.webView = null
            }
        }
    }

    @Test
    fun imageDecodePauseBelongsToAllActiveLists() {
        val column = LazyListState()
        val grid = LazyGridState()
        val idle = LazyListState()
        val showIdle = mutableStateOf(false)
        val showGrid = mutableStateOf(true)
        val showAll = mutableStateOf(true)
        var scope: CoroutineScope? = null
        var firstScroll: Job? = null
        var secondScroll: Job? = null
        val checks = linkedMapOf<String, Boolean>()
        fun paused() = PauseLoadWhenScrollingDrawableDecodeInterceptor.scrolling
        try {
            compose.setContent {
                scope = rememberCoroutineScope()
                MaterialTheme {
                    if (showAll.value) Column {
                        MyLazyColumn(state = column, modifier = Modifier.height(100.dp)) {
                            items(30) { Text("column $it") }
                        }
                        if (showGrid.value) MyLazyVerticalGrid(columns = GridCells.Fixed(2), state = grid,
                            modifier = Modifier.height(100.dp)) {
                            items(30) { Text("grid $it") }
                        }
                        if (showIdle.value) MyLazyColumn(state = idle, modifier = Modifier.height(100.dp)) {
                            items(30) { Text("idle $it") }
                        }
                    }
                }
            }
            compose.runOnIdle { checks["initiallyNotPaused"] = !paused() }
            compose.runOnIdle { firstScroll = scope!!.launch { column.scroll { scrollBy(1f); awaitCancellation() } } }
            compose.waitForIdle()
            compose.runOnIdle { checks["activeColumnPauses"] = paused(); showIdle.value = true }
            compose.waitForIdle()
            compose.runOnIdle { checks["idleMountDoesNotResume"] = paused(); showIdle.value = false }
            compose.waitForIdle()
            compose.runOnIdle {
                checks["idleRemovalDoesNotResume"] = paused()
                secondScroll = scope!!.launch { grid.scroll { scrollBy(1f); awaitCancellation() } }
            }
            compose.waitForIdle()
            compose.runOnIdle { checks["twoOwnersPause"] = paused(); firstScroll!!.cancel() }
            compose.waitForIdle()
            compose.runOnIdle { checks["oneOwnerStoppingDoesNotResume"] = paused(); showGrid.value = false }
            compose.waitForIdle()
            compose.runOnIdle { checks["finalOwnerRemovalResumes"] = !paused(); secondScroll!!.cancel() }
            compose.waitForIdle()
            compose.runOnIdle { checks["cancelAfterRemovalStaysResumed"] = !paused() }
            save("image-decode-pause.json", checks)
            assertTrue("Image decode ownership: $checks", checks.values.all { it })
        } finally {
            compose.runOnIdle { firstScroll?.cancel(); secondScroll?.cancel(); showAll.value = false }
            compose.waitForIdle()
        }
    }

    private fun save(name: String, checks: Map<String, Any>) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "resource-performance-v18").apply { mkdirs() }
        File(directory, name).writeText(JSONObject(checks).toString(2))
    }

    private class TrackedWebView(context: Context) : android.webkit.WebView(context) {
        val events = mutableListOf<String>()
        var destroyCalls = 0
        var destroyedWhileAttached = false

        override fun stopLoading() {
            events += "stop"
            super.stopLoading()
        }

        override fun destroy() {
            events += "destroy"
            destroyCalls++
            destroyedWhileAttached = isAttachedToWindow
            super.destroy()
        }
    }
}
