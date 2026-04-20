package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.os.Looper
import com.github.panpf.sketch.sketch
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 图片缓存工具类
 * Created by Trojx on 2016/10/10 0010.
 */
object ImageCacheUtil {
    const val DEFAULT_DISK_CACHE_DIR = "image_manager_disk_cache"
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 清除图片磁盘缓存
     */
    fun clearImageDiskCache(context: Context) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ioScope.launch {
                    context.sketch
                        .downloadCache
                        .clear()
                    context.sketch
                        .resultCache
                        .clear()
                }
            } else {
                context.sketch
                    .downloadCache
                    .clear()
                context.sketch
                    .resultCache
                    .clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清除图片内存缓存
     */
    fun clearImageMemoryCache(context: Context) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) { //只能在主线程执行
                context.sketch
                    .memoryCache
                    .clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清除图片所有缓存
     */
    fun clearImageAllCache(context: Context) {
        clearImageDiskCache(context)
        clearImageMemoryCache(context)
        context.externalCacheDir?.let { deleteFolderFile(File(it, DEFAULT_DISK_CACHE_DIR), false) }
        deleteFolderFile(File(context.cacheDir, ".shareTemp"), false)
    }

    /**
     * 获取缓存大小
     *
     * @return CacheSize
     */
    fun getCacheSize(context: Context): String {
        try {
            val glideCacheSize = getFolderSize(
                File(
                    context.cacheDir,
                    DEFAULT_DISK_CACHE_DIR
                )
            ).toDouble()
            val shareCacheSize = getFolderSize(File(context.cacheDir, ".shareTemp")).toDouble()
            val sketchCacheSize = context.sketch.run { downloadCache.size + resultCache.size }
            return getFormatSize(glideCacheSize + shareCacheSize + sketchCacheSize)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * 获取指定文件夹内所有文件大小的和
     *
     * @param file file
     * @return size
     */
    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        try {
            val fileList = file.listFiles() ?: return 0
            for (aFileList in fileList) {
                size = if (aFileList.isDirectory) {
                    size + getFolderSize(aFileList)
                } else {
                    size + aFileList.length()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    /**
     * 删除指定目录下的文件，这里用于缓存的删除
     *
     * @param filePath       filePath
     * @param deleteThisPath deleteThisPath
     */
    private fun deleteFolderFile(file: File, deleteThisPath: Boolean) {
        if (!file.exists()) {
            return
        }
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach {
                    deleteFolderFile(it, true)
                }
            }
            if (deleteThisPath) {
                if (!file.isDirectory || file.listFiles().isNullOrEmpty()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 格式化单位
     *
     * @param size size
     * @return size
     */
    fun getFormatSize(size: Double): String {
        val kiloByte = size / 1024
        if (kiloByte < 1) {
            return size.toString() + "B"
        }
        val megaByte = kiloByte / 1024
        if (megaByte < 1) {
            val result1 = BigDecimal(java.lang.Double.toString(kiloByte))
            return result1.setScale(2, RoundingMode.HALF_UP).toPlainString() + "KB"
        }
        val gigaByte = megaByte / 1024
        if (gigaByte < 1) {
            val result2 = BigDecimal(java.lang.Double.toString(megaByte))
            return result2.setScale(2, RoundingMode.HALF_UP).toPlainString() + "MB"
        }
        val teraBytes = gigaByte / 1024
        if (teraBytes < 1) {
            val result3 = BigDecimal(java.lang.Double.toString(gigaByte))
            return result3.setScale(2, RoundingMode.HALF_UP).toPlainString() + "GB"
        }
        val result4 = BigDecimal(teraBytes)
        return result4.setScale(2, RoundingMode.HALF_UP).toPlainString() + "TB"
    }
}
