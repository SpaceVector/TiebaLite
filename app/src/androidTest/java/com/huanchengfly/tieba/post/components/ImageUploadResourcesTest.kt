package com.huanchengfly.tieba.post.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.api.models.PicInfo
import com.huanchengfly.tieba.post.api.models.PicInfoItem
import com.huanchengfly.tieba.post.api.models.UploadPictureResultBean
import com.huanchengfly.tieba.post.api.retrofit.body.MyMultipartBody
import com.huanchengfly.tieba.post.utils.ImageUtil
import com.huanchengfly.tieba.post.utils.MD5Util
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUploadResourcesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val uploader = ImageUploader("offline-test")

    @Test
    fun preprocessingPreservesQualityWithoutWholeFileBuffers() = runBlocking {
        val directory = File(context.cacheDir, "upload-resources-v19").apply { mkdirs() }
        val checks = linkedMapOf<String, Boolean>()
        val measurements = linkedMapOf<String, Any>()
        try {
            val tiny = createImage(directory, "tiny.png", 8, 8, false)
            uploader.uploadSinglePicture(tiny.path, false) { flowOf(response("warmup")) }
            val noise = createImage(directory, "noise.png", 2880, 2160, true)
            val gradient = createImage(directory, "gradient.png", 2400, 1800, false)
            val padded = File(directory, "original.png").also {
                tiny.copyTo(it, true)
                RandomAccessFile(it, "rw").use { file -> file.setLength(9L * 1024 * 1024) }
            }
            val exactChunks = File(directory, "exact-chunks.png").also {
                tiny.copyTo(it, true)
                RandomAccessFile(it, "rw").use { file -> file.setLength(2L * ImageUploader.DEFAULT_CHUNK_SIZE) }
            }
            val cases = listOf(
                Triple("normal-resize", noise, false),
                Triple("normal-full-resolution", gradient, false),
                Triple("oversize-original", noise, true),
                Triple("original-byte-exact", padded, true),
                Triple("original-exact-chunks", exactChunks, true),
                Triple("original-single-chunk", tiny, true),
            )
            for ((name, source, original) in cases) {
                val expected = referenceCompression(source, original, File(directory, "expected.jpg"))
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                val outputBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(expected.path, outputBounds)
                val maxSize = if (original) ImageUploader.ORIGIN_IMAGE_MAX_SIZE else ImageUploader.IMAGE_MAX_SIZE
                checks["$name-size-policy"] = expected.length() <= maxSize
                checks["$name-resolution-policy"] = when (name) {
                    "normal-resize" -> maxOf(outputBounds.outWidth, outputBounds.outHeight) == 1080
                    else -> outputBounds.outWidth == bounds.outWidth && outputBounds.outHeight == bounds.outHeight
                }
                val expectedDigest = MD5Util.toMd5(expected)
                val sourceDigest = MD5Util.toMd5(source)
                val beforeTemporary = temporaryFiles()
                val digest = MessageDigest.getInstance("MD5")
                var chunks = 0
                var bytes = 0L
                var fieldsCorrect = true
                var allocated = 0L
                var nativeRetained = 0L
                System.gc()
                System.runFinalization()
                System.gc()
                val beforeAllocated = allocatedBytes()
                val beforeNative = Debug.getNativeHeapAllocatedSize()
                val started = System.nanoTime()
                val result = uploader.uploadSinglePicture(source.path, original) { body ->
                    if (chunks == 0) {
                        allocated = allocatedBytes() - beforeAllocated
                        nativeRetained = Debug.getNativeHeapAllocatedSize() - beforeNative
                    }
                    chunks++
                    val fields = fields(body)
                    val chunk = partBytes(body, "chunk")
                    digest.update(chunk)
                    bytes += chunk.size
                    val finished = bytes == expected.length()
                    fieldsCorrect = fieldsCorrect && fields["chunkNo"] == "$chunks" &&
                        fields["isFinish"] == if (finished) "1" else "0"
                    fieldsCorrect = fieldsCorrect && fields["size"] == "${expected.length()}" &&
                        fields["width"] == "${bounds.outWidth}" && fields["height"] == "${bounds.outHeight}" &&
                        fields["saveOrigin"] == if (original) "1" else "0"
                    fieldsCorrect = fieldsCorrect && fields["resourceId"] == "$expectedDigest${ImageUploader.DEFAULT_CHUNK_SIZE}" &&
                        fields["forum_name"] == "offline-test" && fields["small_flow_fname"] == "offline-test" &&
                        chunk.size == minOf(ImageUploader.DEFAULT_CHUNK_SIZE.toLong(), expected.length() - bytes + chunk.size).toInt()
                    flowOf(response("$chunks"))
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
                checks["$name-byte-exact-jpeg95-or-original"] = MD5Util.toHexString(digest.digest()) == expectedDigest && bytes == expected.length()
                checks["$name-chunk-fields"] = fieldsCorrect && result.chunkNo == "$chunks"
                checks["$name-success-releases-temp"] = temporaryFiles() == beforeTemporary
                // This counts cumulative ART Java allocations before the first request, not RSS/energy.
                checks["$name-bounded-java-allocation"] = allocated in 0..(3L * 1024 * 1024)
                measurements[name] = mapOf(
                    "sourceBytes" to source.length(), "sourceMd5" to sourceDigest, "outputBytes" to expected.length(),
                    "sourceWidth" to bounds.outWidth, "sourceHeight" to bounds.outHeight,
                    "outputWidth" to outputBounds.outWidth, "outputHeight" to outputBounds.outHeight,
                    "outputMd5" to expectedDigest, "chunks" to chunks,
                    "javaAllocatedBeforeFirstRequest" to allocated,
                    "nativeHeapDeltaBeforeFirstRequest" to nativeRetained, "elapsedMs" to elapsedMs
                )
            }
            save("preprocessing.json", checks, measurements)
            assertTrue("Upload preprocessing: $checks", checks.values.all { it })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellationAndPreparationFailureDeleteOwnedTemporaryFiles() = runBlocking {
        val directory = File(context.cacheDir, "upload-cleanup-v19").apply { mkdirs() }
        val before = temporaryFiles()
        val checks = linkedMapOf<String, Boolean>()
        val details = linkedMapOf<String, Any>()
        try {
            val tiny = createImage(directory, "tiny.png", 8, 8, false)
            val started = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.IO) {
                uploader.uploadSinglePicture(tiny.path, true) {
                    flow { started.complete(Unit); awaitCancellation() }
                }
            }
            try {
                withTimeout(10_000) { started.await() }
            } finally {
                job.cancelAndJoin()
            }
            checks["upload-cancelled"] = job.isCancelled
            val cancelLeaks = temporaryFiles() - before
            checks["cancel-releases-temp"] = cancelLeaks.isEmpty()
            details["cancelLeakedFiles"] = cancelLeaks.map { it.name }
            cancelLeaks.forEach { it.delete() }

            var calls = 0
            val failure = runCatching {
                uploader.uploadSinglePicture(tiny.path, true) {
                    flow { calls++; throw IOException("offline synthetic failure") }
                }
            }.exceptionOrNull()
            checks["failure-propagated"] = failure is UploadPictureFailedException && calls == 1
            checks["network-failure-releases-temp"] = temporaryFiles() == before

            calls = 0
            val preparationFailure = runCatching {
                ImageUploader("offline-test", chunkSize = 0).uploadSinglePicture(tiny.path, false) {
                    calls++
                    flowOf(response("unexpected"))
                }
            }.exceptionOrNull()
            checks["preparation-failure-never-uploads"] = preparationFailure is ArithmeticException && calls == 0
            val preparationLeaks = temporaryFiles() - before
            checks["preparation-failure-releases-temp"] = preparationLeaks.isEmpty()
            details["preparationLeakedFiles"] = preparationLeaks.map { it.name }
            details["preparationFailure"] = preparationFailure.toString()
            save("cleanup.json", checks, details)
            assertTrue("Upload cleanup: $checks", checks.values.all { it })
        } finally {
            (temporaryFiles() - before).forEach { it.delete() }
            directory.deleteRecursively()
        }
    }

    private fun createImage(directory: File, name: String, width: Int, height: Int, noise: Boolean): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        var random = 0x193a567b
        try {
            for (y in 0 until height) {
                for (x in row.indices) {
                    random = random xor (random shl 13)
                    random = random xor (random ushr 17)
                    random = random xor (random shl 5)
                    row[x] = if (noise) 0xff000000.toInt() or (random and 0xffffff)
                        else 0xff000000.toInt() or ((x * 255 / width) shl 16) or ((y * 255 / height) shl 8) or 96
                }
                bitmap.setPixels(row, 0, width, 0, y, width, 1)
            }
            return File(directory, name).also { output ->
                output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun referenceCompression(source: File, original: Boolean, output: File): File {
        val limit = if (original) ImageUploader.ORIGIN_IMAGE_MAX_SIZE else ImageUploader.IMAGE_MAX_SIZE
        if (original && source.length() <= limit) return source.copyTo(output, true)
        val bitmap = BitmapFactory.decodeFile(source.path)
        try {
            output.writeBytes(ImageUtil.compressImage(bitmap, 95))
            if (output.length() > limit) {
                val scale = 1080f / maxOf(bitmap.width, bitmap.height)
                if (scale < 1) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                    try { output.writeBytes(ImageUtil.compressImage(scaled, 95)) }
                    finally { scaled.recycle() }
                }
            }
            return output
        } finally {
            bitmap.recycle()
        }
    }

    private fun fields(body: MyMultipartBody): Map<String, String> = body.parts
        .filter { "filename=" !in it.headers?.get("Content-Disposition").orEmpty() }
        .associate { part ->
            val name = part.headers!!["Content-Disposition"]!!.substringAfter("name=\"").substringBefore('"')
            name to Buffer().also { part.body.writeTo(it) }.readUtf8()
        }

    private fun partBytes(body: MyMultipartBody, name: String): ByteArray = Buffer().also { buffer ->
        body.parts.single { "name=\"$name\"" in it.headers?.get("Content-Disposition").orEmpty() }.body.writeTo(buffer)
    }.readByteArray()

    private fun response(chunk: String): UploadPictureResultBean {
        val item = PicInfoItem("8", "8", "jpg", "offline.invalid")
        return UploadPictureResultBean("0", "", "offline", chunk, "offline", PicInfo(item, item, item))
    }

    private fun allocatedBytes() = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
    private fun temporaryFiles(): Set<File> = File(System.getProperty("java.io.tmpdir")!!)
        .listFiles { file -> file.name.startsWith("temp") && file.name.endsWith(".tmp") }?.toSet().orEmpty()

    private fun save(name: String, checks: Map<String, Boolean>, details: Map<String, Any>) {
        val directory = File(context.getExternalFilesDir(null), "image-upload-performance-v19").apply { mkdirs() }
        File(directory, name).writeText(JSONObject(mapOf("checks" to checks, "details" to details)).toString(2))
    }
}
