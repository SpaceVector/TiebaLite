package com.huanchengfly.tieba.post.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.BOUNDARY
import com.huanchengfly.tieba.post.api.booleanToString
import com.huanchengfly.tieba.post.api.models.UploadPictureResultBean
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.api.retrofit.body.MyMultipartBody
import com.huanchengfly.tieba.post.api.retrofit.body.buildMultipartBody
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaException
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorCode
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.utils.MD5Util
import com.huanchengfly.tieba.post.utils.appPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile

class ImageUploader(
    private val forumName: String,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE = 512000

        const val IMAGE_MAX_SIZE = 5242880
        const val ORIGIN_IMAGE_MAX_SIZE = 10485760

        const val PIC_WATER_TYPE_NO = "0"
        const val PIC_WATER_TYPE_USER_NAME = "1"
        const val PIC_WATER_TYPE_FORUM_NAME = "2"
    }

    fun uploadImages(
        filePaths: List<String>,
        isOriginImage: Boolean = false,
    ): Flow<List<UploadPictureResultBean>> {
        return filePaths.asFlow()
            .map { filePath ->
                uploadSinglePicture(filePath, isOriginImage)
            }
            .runningFold<UploadPictureResultBean, MutableList<UploadPictureResultBean>>(initial = mutableListOf()) { list, result ->
                list.add(result)
                list
            }
            .filter { it.size == filePaths.size }
    }

    private suspend fun compressImage(
        filePath: String,
        isOriginImage: Boolean,
        tempFile: File,
    ) {
        val originFile = File(filePath)
        val fileLength = originFile.length()
        val maxSize = if (isOriginImage) ORIGIN_IMAGE_MAX_SIZE else IMAGE_MAX_SIZE
        if (isOriginImage && fileLength <= maxSize) {
            originFile.copyTo(tempFile, true)
        } else {
            var bitmap = checkNotNull(BitmapFactory.decodeFile(filePath)) { "图片解码失败" }
            try {
                writeJpeg(bitmap, tempFile)
                currentCoroutineContext().ensureActive()
                if (tempFile.length() > maxSize) {
                    // 压缩尺寸至 1080P
                    val width = bitmap.width
                    val height = bitmap.height
                    val scale = if (width > height) {
                        1080f / width
                    } else {
                        1080f / height
                    }
                    if (scale < 1) {
                        val newWidth = (width * scale).toInt()
                        val newHeight = (height * scale).toInt()
                        val newBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                        bitmap.recycle()
                        bitmap = newBitmap
                        writeJpeg(bitmap, tempFile)
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        file.outputStream().buffered().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "图片压缩失败" }
        }
    }

    suspend fun uploadSinglePicture(
        filePath: String,
        isOriginImage: Boolean = false,
    ): UploadPictureResultBean = uploadSinglePicture(filePath, isOriginImage) {
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.uploadPicture(it)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun uploadSinglePicture(
        filePath: String,
        isOriginImage: Boolean,
        upload: (MyMultipartBody) -> Flow<UploadPictureResultBean>,
    ): UploadPictureResultBean = withContext(Dispatchers.IO) {
        val option = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, option)
        val width = option.outWidth
        val height = option.outHeight
        check(width > 0 && height > 0) { "图片宽高不正确" }
        val file = File.createTempFile("temp", ".tmp")
        try {
            compressImage(filePath, isOriginImage, file)
            currentCoroutineContext().ensureActive()
            val fileLength = file.length()
            val maxSize = if (isOriginImage) ORIGIN_IMAGE_MAX_SIZE else IMAGE_MAX_SIZE
            check(fileLength <= maxSize) { "图片大小超过限制" }
            val fileMd5 = MD5Util.toMd5(file)
            val isMultipleChunkSize = fileLength % chunkSize == 0L
            val totalChunkNum = fileLength / chunkSize + if (isMultipleChunkSize) 0 else 1
            val picWatermarkType =
                App.INSTANCE.appPreferences.picWatermarkType ?: PIC_WATER_TYPE_FORUM_NAME
            val requestBodies = (0 until totalChunkNum).asFlow().map { chunk ->
                val isFinish = chunk == totalChunkNum - 1
                val curChunkSize = if (isFinish) {
                    if (isMultipleChunkSize) {
                        chunkSize
                    } else {
                        fileLength % chunkSize
                    }
                } else {
                    chunkSize
                }.toInt()
                val chunkBytes = ByteArray(curChunkSize)
                RandomAccessFile(file, "r").use {
                    it.seek(chunk * chunkSize.toLong())
                    it.readFully(chunkBytes)
                }
                buildMultipartBody(BOUNDARY) {
                    setType(MyMultipartBody.FORM)
                    addFormDataPart("alt", "json")
                    addFormDataPart("chunkNo", "${chunk + 1}")
                    if (forumName.isNotEmpty()) addFormDataPart("forum_name", forumName)
                    addFormDataPart("groupId", "1")
                    addFormDataPart("height", "$height")
                    addFormDataPart("isFinish", isFinish.booleanToString())
                    addFormDataPart("is_bjh", "0")
                    addFormDataPart("pic_water_type", picWatermarkType)
                    addFormDataPart("resourceId", "$fileMd5$chunkSize")
                    addFormDataPart("saveOrigin", isOriginImage.booleanToString())
                    addFormDataPart("size", "$fileLength")
                    if (forumName.isNotEmpty()) addFormDataPart("small_flow_fname", forumName)
                    addFormDataPart("width", "$width")
                    addFormDataPart("chunk", "file", chunkBytes.toRequestBody())
                }
            }
            requestBodies
                .flatMapConcat { upload(it) }
                .catch {
                    throw UploadPictureFailedException(it.getErrorCode(), it.getErrorMessage())
                }
                .last()
        } finally {
            file.delete()
        }
    }
}

class UploadPictureFailedException(
    override val code: Int = -1,
    override val message: String = "上传图片失败",
) : TiebaException(message)
