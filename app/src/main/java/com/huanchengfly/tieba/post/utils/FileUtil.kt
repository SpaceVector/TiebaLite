package com.huanchengfly.tieba.post.utils

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.webkit.URLUtil
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.utils.PermissionUtils.PermissionData
import com.huanchengfly.tieba.post.utils.PermissionUtils.askPermission
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays

object FileUtil {
    const val FILE_TYPE_DOWNLOAD = 0
    const val FILE_TYPE_VIDEO = 1
    const val FILE_TYPE_AUDIO = 2
    const val FILE_FOLDER = "TiebaLite"
    fun deleteAllFiles(root: File) {
        val files = root.listFiles()
        if (files != null) for (f in files) {
            if (f.isDirectory) { // 判断是否为文件夹
                deleteAllFiles(f)
                try {
                    f.delete()
                } catch (e: Exception) {
                }
            } else {
                if (f.exists()) { // 判断是否存在
                    deleteAllFiles(f)
                    try {
                        f.delete()
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    /**
     * @param context 上下文对象
     * @param dir     存储目录
     * @return
     */
    fun getFilePath(context: Context, dir: String): String {
        val directoryPath = if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            context.getExternalFilesDir(dir)!!.absolutePath
        } else {
            context.filesDir.toString() + File.separator + dir
        }
        val file = File(directoryPath)
        if (!file.exists()) {
            file.mkdirs()
        }
        return directoryPath
    }

    @JvmStatic
    fun copyUriToCacheFile(
        context: Context,
        uri: Uri,
        prefix: String = "uri_",
        suffix: String = ".tmp",
    ): File {
        val tempFile = File.createTempFile(prefix, suffix, context.cacheDir)
        val inputStream = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { FileInputStream(File(it)) }
            else -> context.contentResolver.openInputStream(uri)
        } ?: throw IOException("Unable to open uri: $uri")
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    fun getFilePathByUri(context: Context, uri: Uri): String? {
        return runCatching {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> uri.path
                else -> copyUriToCacheFile(context, uri, prefix = "uri_path_").absolutePath
            }
        }.getOrNull()
    }

    @JvmStatic
    fun getRealPathFromUri(context: Context, contentUri: Uri?): String {
        if (contentUri == null) {
            return ""
        }
        return getFilePathByUri(context, contentUri).orEmpty()
    }

    fun downloadBySystem(context: Context, fileType: Int, url: String?) {
        val fileName = URLUtil.guessFileName(url, null, null)
        downloadBySystem(context, fileType, url, fileName)
    }

    private fun downloadBySystemWithPermission(
        context: Context,
        fileType: Int,
        url: String?,
        fileName: String,
    ) {
        // 指定下载地址
        val request = DownloadManager.Request(Uri.parse(url))
        // 设置通知的显示类型，下载进行时和完成后显示通知
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        // 允许漫游时下载
        request.setAllowedOverRoaming(false)
        // 设置下载文件保存的路径和文件名
        val directory: String
        directory = when (fileType) {
            FILE_TYPE_VIDEO -> Environment.DIRECTORY_MOVIES
            FILE_TYPE_AUDIO -> Environment.DIRECTORY_PODCASTS
            FILE_TYPE_DOWNLOAD -> Environment.DIRECTORY_DOWNLOADS
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        request.setDestinationInExternalPublicDir(
            directory,
            FILE_FOLDER + File.separator + fileName
        )
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // 添加一个下载任务
        downloadManager.enqueue(request)
    }

    fun downloadBySystem(context: Context, fileType: Int, url: String?, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadBySystemWithPermission(context, fileType, url, fileName)
            return
        }
        askPermission(
            context,
            PermissionData(
                Arrays.asList(PermissionUtils.WRITE_EXTERNAL_STORAGE),
                context.getString(R.string.tip_permission_storage_download)
            )
        ) {
            downloadBySystemWithPermission(context, fileType, url, fileName)
        }
    }

    @JvmStatic
    fun readFile(file: File?): String? {
        if (file == null || !file.exists() || !file.canRead()) {
            return null
        }
        try {
            val `is`: InputStream = FileInputStream(file)
            val length = `is`.available()
            val buffer = ByteArray(length)
            `is`.read(buffer)
            return String(buffer, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    fun writeFile(file: File?, content: String, append: Boolean): Boolean {
        if (file == null || !file.exists() || !file.canWrite()) {
            return false
        }
        try {
            val fos = FileOutputStream(file, append)
            fos.write(content.toByteArray())
            fos.flush()
            fos.close()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    fun writeFile(file: File, inputStream: InputStream): Boolean {
        if (!file.exists() || !file.canWrite()) {
            return false
        }
        try {
            val fos = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var byteCount: Int
            while (inputStream.read(buffer).also { byteCount = it } != -1) {
                fos.write(buffer, 0, byteCount)
            }
            fos.flush()
            fos.close()
            inputStream.close()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    //修改文件扩展名
    fun changeFileExtension(fileName: String, newExtension: String): String {
        if (TextUtils.isEmpty(fileName)) {
            return fileName
        }
        val index = fileName.lastIndexOf(".")
        return if (index == -1) {
            fileName + newExtension
        } else fileName.substring(0, index) + newExtension
    }
}
