package com.huanchengfly.tieba.post.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileOutputStream

internal enum class FairMemoryAction(val intentAction: String) {
    TRIM("itgsa.intent.action.TRIM"),
    KILL("itgsa.intent.action.KILL"),
}

internal fun fairMemoryActionOf(intentAction: String?): FairMemoryAction? =
    FairMemoryAction.entries.firstOrNull { it.intentAction == intentAction }

internal class HyperOsFairMemoryReceiver(
    private val handleMemory: (FairMemoryAction) -> Boolean,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = fairMemoryActionOf(intent.action) ?: return
        val common = intent.getBundleExtra(KEY_COMMON) ?: return
        val callback = common.getBinder(KEY_CALLBACK) ?: return
        val notifyType = common.getInt(KEY_NOTIFY_TYPE)
        val notifyId = common.getInt(KEY_NOTIFY_ID)
        val result = runCatching { handleMemory(action) }.fold(
            onSuccess = { handled -> if (handled) RESULT_SUCCESS else RESULT_FAILED },
            onFailure = {
                Log.e(TAG, "Failed to release memory", it)
                RESULT_FAILED
            },
        )
        reply(callback, notifyType, notifyId, result, action)
    }

    private fun reply(
        callback: IBinder,
        notifyType: Int,
        notifyId: Int,
        result: Int,
        action: FairMemoryAction,
    ) {
        val data = Parcel.obtain()
        try {
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(result)
            data.writeBundle(Bundle().apply { putString(KEY_REPLY, action.name.lowercase()) })
            if (!callback.transact(
                    IBinder.FIRST_CALL_TRANSACTION,
                    data,
                    null,
                    IBinder.FLAG_ONEWAY,
                )
            ) {
                Log.w(TAG, "Memory callback was not handled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reply to memory callback", e)
        } finally {
            data.recycle()
        }
    }

    companion object {
        private const val TAG = "HyperOsFairMemory"
        private const val KEY_COMMON = "common"
        private const val KEY_NOTIFY_TYPE = "notifyType"
        private const val KEY_NOTIFY_ID = "notifyId"
        private const val KEY_CALLBACK = "callback"
        private const val KEY_REPLY = "reply"
        private const val RESULT_SUCCESS = 0
        private const val RESULT_FAILED = 1

        @Suppress("DEPRECATION")
        fun register(context: Context, handleMemory: (FairMemoryAction) -> Boolean) {
            val receiver = HyperOsFairMemoryReceiver(handleMemory)
            val filter = IntentFilter().apply {
                FairMemoryAction.entries.forEach { addAction(it.intentAction) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }
    }
}

internal object FairMemoryStateStore {
    private const val TAG = "FairMemoryStateStore"
    private const val FILE_NAME = "hyperos_fair_memory_navigation_state"

    fun save(context: Context, state: Bundle): Boolean {
        val atomicFile = atomicFile(context)
        val parcel = Parcel.obtain()
        var output: FileOutputStream? = null
        return try {
            parcel.writeBundle(state)
            val stream = atomicFile.startWrite()
            output = stream
            stream.write(parcel.marshall())
            atomicFile.finishWrite(stream)
            output = null
            true
        } catch (e: Exception) {
            output?.let(atomicFile::failWrite)
            Log.e(TAG, "Failed to save navigation state", e)
            false
        } finally {
            parcel.recycle()
        }
    }

    fun consume(context: Context): Bundle? {
        val atomicFile = atomicFile(context)
        if (!atomicFile.baseFile.exists()) return null
        val parcel = Parcel.obtain()
        return try {
            val bytes = atomicFile.readFully()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            parcel.readBundle(context.classLoader)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore navigation state", e)
            null
        } finally {
            parcel.recycle()
            atomicFile.delete()
        }
    }

    fun clear(context: Context) {
        atomicFile(context).delete()
    }

    private fun atomicFile(context: Context): AtomicFile =
        AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
}
