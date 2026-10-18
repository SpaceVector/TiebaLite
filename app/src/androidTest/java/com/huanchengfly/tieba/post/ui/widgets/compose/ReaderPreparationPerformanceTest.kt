package com.huanchengfly.tieba.post.ui.widgets.compose

import android.os.Debug
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.renders
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.ui.common.PicContentRender
import com.huanchengfly.tieba.post.ui.common.TextContentRender
import com.huanchengfly.tieba.post.ui.common.VideoContentRender
import com.huanchengfly.tieba.post.ui.common.VoiceContentRender
import com.huanchengfly.tieba.post.ui.page.thread.PostItemData
import com.huanchengfly.tieba.post.ui.page.thread.findLastVisibleThreadPost
import java.io.File
import kotlinx.collections.immutable.persistentListOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real reader preparation paths; synthetic metadata only, no Activity, requests or history writes. */
class ReaderPreparationPerformanceTest {
    private val directory get() = File(InstrumentationRegistry.getInstrumentation().targetContext
        .getExternalFilesDir(null), "reading-memory-performance-v21").apply { mkdirs() }

    @Test
    fun richContentPreparationPreservesBoundariesAndAnnotations() {
        val checks = linkedMapOf<String, Boolean>()
        val snapshots = JSONObject()
        val measurements = JSONArray()
        val mixed = listOf(
            PbContent(text = "正文开头\n"),
            PbContent(type = 1, text = "离线链接", link = "https://offline.invalid/reader"),
            PbContent(type = 2, text = "image_emoticon1", c = "呵呵"),
            PbContent(type = 4, text = "@本地用户", uid = 2021L),
            PbContent(type = 999, text = "忽略的类型"),
            PbContent(type = 9, text = "\n合并的尾段"),
            PbContent(type = 27, text = "保留尾部换行\n"),
        )
        val image = PbContent(type = 3, bsize = "640,480", src = "https://offline.invalid/photo.jpg",
            originSrc = "https://offline.invalid/photo.jpg", originSize = 12345, showOriginalBtn = 1)
        val media = listOf(
            PbContent(text = "图片前"), image, PbContent(text = "图片后"),
            PbContent(type = 10, voiceMD5 = "offline-voice", duringTime = 3),
            PbContent(text = "语音后"),
            PbContent(type = 5, bsize = "640,360", src = "https://offline.invalid/poster.jpg",
                link = "https://offline.invalid/video.mp4", text = "https://offline.invalid/video"),
            PbContent(text = "视频后"),
            PbContent(type = 20, bsize = "24,24", src = "https://offline.invalid/sticker.gif"),
            PbContent(text = ""),
            PbContent(type = 5, text = "https://offline.invalid/video-link"),
        )
        val fixtures = linkedMapOf("empty" to emptyList(), "empty-text" to listOf(PbContent()),
            "ignored-only" to listOf(PbContent(type = 999)), "mixed" to mixed, "media" to media,
            "adjacent-media" to listOf(image, image),
            "empty-before-media" to listOf(PbContent(), image, PbContent()))
        fixtures.forEach { (name, content) ->
            snapshots.put(name, JSONArray(content.renders.map { render ->
                when (render) {
                    is TextContentRender -> JSONObject().put("type", "text").put("value", annotated(render.text))
                    is PicContentRender -> JSONObject().put("type", "image").put("url", render.picUrl)
                        .put("origin", render.originUrl).put("showOriginal", render.showOriginBtn)
                        .put("bytes", render.originSize).put("id", render.picId)
                        .put("width", render.width).put("height", render.height)
                    is VoiceContentRender -> JSONObject().put("type", "voice").put("md5", render.voiceMd5)
                        .put("duration", render.duration)
                    is VideoContentRender -> JSONObject().put("type", "video").put("url", render.videoUrl)
                        .put("poster", render.picUrl).put("webUrl", render.webUrl)
                        .put("width", render.width).put("height", render.height)
                    else -> error("Unexpected render: $render")
                }
            }))
        }
        val mixedText = (mixed.renders.single() as TextContentRender).text
        checks["text-types-and-ignored-merge"] = mixedText.text == "正文开头\n🔗离线链接#(呵呵)@本地用户\n合并的尾段保留尾部换行\n"
        checks["link-annotation"] = mixedText.getStringAnnotations("url", 0, mixedText.length).single().let {
            it.item == "https://offline.invalid/reader" && mixedText.subSequence(it.start, it.end).text == "离线链接"
        }
        checks["user-annotation"] = mixedText.getStringAnnotations("user", 0, mixedText.length).single().let {
            it.item == "2021" && mixedText.subSequence(it.start, it.end).text == "@本地用户"
        }
        checks["styles-retained"] = mixedText.spanStyles.size == 2
        checks["inline-icons-retained"] = mixedText.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, mixedText.length)
            .map { it.item }.toSet() == setOf("link_icon", "Emoticon#image_emoticon1")
        checks["empty-text-retained"] = fixtures.getValue("empty-text").renders.single() == TextContentRender("")
        checks["empty-list-retained"] = emptyList<PbContent>().renders.isEmpty()
        checks["media-boundaries-retained"] = media.renders.map { it.javaClass.simpleName } == listOf(
            "TextContentRender", "PicContentRender", "TextContentRender", "VoiceContentRender", "TextContentRender",
            "VideoContentRender", "TextContentRender", "PicContentRender", "TextContentRender")
        for (count in listOf(32, 128, 512)) {
            val content = List(count) { PbContent(text = "离线阅读第${it}段：验证长文准备分配，保留文字与换行。\n") }
            val expected = content.joinToString("") { it.text }
            checks["plain-$count-text-identical"] = (content.renders.single() as TextContentRender).text == AnnotatedString(expected)
            val samples = measure(5) { content.renders.sumOf { (it as TextContentRender).text.length } }
            measurements.put(JSONObject().put("label", "plain-$count").put("fragments", count)
                .put("utf16Length", expected.length).put("samples", JSONArray(samples)))
            if (count == 512) {
                val median = samples.map { it.getLong("javaAllocatedBytes") }.sorted()[1] / 5
                checks["long-text-linear-allocation-budget"] = median <= expected.length * 32L + 128 * 1024L
            }
        }
        val rich = List(32) { mixed }.flatten()
        val richText = rich.renders.single() as TextContentRender
        checks["many-links-ranges-retained"] = richText.text.getStringAnnotations("url", 0, richText.text.length).size == 32 &&
            richText.text.getStringAnnotations("user", 0, richText.text.length).size == 32 && richText.text.spanStyles.size == 64
        measurements.put(JSONObject().put("label", "mixed-224").put("fragments", rich.size)
            .put("samples", JSONArray(measure(5) { rich.renders.sumOf { (it as TextContentRender).text.length } })))
        save("content-preparation.json", JSONObject().put("checks", json(checks))
            .put("fixtures", snapshots).put("measurements", measurements))
        assertTrue("Reader content preparation: $checks", checks.values.all { it })
    }

    @Test
    fun readingPositionUsesOneViewportLookupAndTracksChanges() {
        val checks = linkedMapOf<String, Boolean>()
        val measurements = JSONArray()
        fun post(id: Long, floor: Int = 2) = PostItemData(Post(id = id, floor = floor).wrapImmutable(),
            blocked = false, contentRenders = persistentListOf(), subPosts = persistentListOf())
        fun viewport(vararg keys: Any) = keys.mapIndexed { index, key -> CountingItem(index, key) }
        val first = mutableStateOf(Post(id = 1L, floor = 1).wrapImmutable())
        val data = mutableStateOf(listOf(post(12), post(312, 3)))
        val visible = mutableStateOf(viewport("Header", "Post_312", "Footer"))
        val position = derivedStateOf { findLastVisibleThreadPost(data.value, visible.value, first.value) }
        checks["exact-id-not-suffix"] = position.value?.item?.id == 312L
        data.value = data.value + post(901, 4)
        visible.value = viewport("Post_312", "Post_901", "Footer")
        checks["snapshot-append"] = position.value?.item?.id == 901L
        data.value = data.value.reversed()
        checks["snapshot-reorder"] = position.value?.item?.id == 901L
        data.value = listOf(post(901, 99))
        checks["snapshot-reload-object"] = position.value?.item?.floor == 99
        data.value = emptyList()
        checks["snapshot-delete-fallback"] = position.value?.item?.id == 1L
        first.value = Post(id = 2L, floor = 1).wrapImmutable()
        checks["snapshot-first-post-refresh"] = position.value?.item?.id == 2L
        data.value = listOf(post(312))
        for ((name, keys) in linkedMapOf(
            "empty" to emptyList(), "non-post" to listOf("Header", 3), "latest-only" to listOf("LatestPost_312"),
            "not-loaded" to listOf("Post_901"), "invalid-id" to listOf("Post_not-a-number"),
            "overflow" to listOf("Post_999999999999999999999"))) {
            checks["$name-fallback"] = findLastVisibleThreadPost(data.value, viewport(*keys.toTypedArray()), first.value)?.item?.id == 2L
        }
        checks["null-fallback"] = findLastVisibleThreadPost(emptyList(), emptyList(), null) == null
        checks["last-visible-normal-post"] = findLastVisibleThreadPost(listOf(post(12), post(312)),
            viewport("Post_312", "Post_12", "LatestPost_312", "Footer"), first.value)?.item?.id == 12L
        for (count in listOf(20, 200, 2000)) {
            val loaded = List(count) { post(10_000_000_000L + it, it + 2) }
            val lastId = loaded.last().post.item.id
            val items = viewport("Header", "Post_${lastId - 2}", "Post_${lastId - 1}", "Post_$lastId", "LatestPost_1", "Footer")
            val result = findLastVisibleThreadPost(loaded, items, first.value)
            val keyReads = items.sumOf { it.keyReads }
            checks["$count-posts-last-id"] = result?.item?.id == lastId
            checks["$count-posts-single-viewport-scan"] = keyReads <= items.size * 3 + 1
            measurements.put(JSONObject().put("loadedPosts", count).put("visibleItems", items.size).put("keyReadsPerLookup", keyReads)
                .put("samples", JSONArray(measure(100) { findLastVisibleThreadPost(loaded, items, first.value)!!.item.floor })))
        }
        save("reading-position.json", JSONObject().put("checks", json(checks)).put("measurements", measurements))
        assertTrue("Reader position: $checks", checks.values.all { it })
    }

    private class CountingItem(override val index: Int, private val storedKey: Any) : LazyListItemInfo {
        var keyReads = 0
        override val key: Any get() { keyReads++; return storedKey }
        override val offset = index * 100
        override val size = 100
    }

    private fun measure(calls: Int, operation: () -> Int): List<JSONObject> {
        repeat(3) { operation() }
        return List(3) {
            val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
            val cpu = Debug.threadCpuTimeNanos()
            var checksum = 0L
            repeat(calls) { checksum += operation() }
            val usedCpu = Debug.threadCpuTimeNanos() - cpu
            val usedBytes = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - allocated
            JSONObject().put("calls", calls).put("threadCpuNs", usedCpu).put("javaAllocatedBytes", usedBytes).put("checksum", checksum)
        }
    }

    private fun annotated(text: AnnotatedString) = JSONObject().put("text", text.text)
        .put("spans", JSONArray(text.spanStyles.map { listOf(it.start, it.end, it.item.toString()) }))
        .put("paragraphs", JSONArray(text.paragraphStyles.map { listOf(it.start, it.end, it.item.toString()) }))
        .put("annotations", JSONArray(text.getStringAnnotations(0, text.length).map { listOf(it.tag, it.start, it.end, it.item) }))

    private fun json(values: Map<String, Boolean>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }
    private fun save(name: String, data: JSONObject) = File(directory, name).writeText(data.toString(2))
}
