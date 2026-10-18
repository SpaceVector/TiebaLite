package com.huanchengfly.tieba.post.services

import android.app.job.JobParameters
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.huanchengfly.tieba.post.api.models.MsgBean
import java.io.File
import java.io.IOException
import java.util.Collections
import okhttp3.Request
import okio.Timeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@SdkSuppress(minSdkVersion = 34)
class NotifyJobServiceTest {
    @Test
    fun everyResponseFinishesAndStoppedRequestsAreCancelled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val finished = Collections.synchronizedList(mutableListOf<Pair<Int, Boolean>>())
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("android.app.job.IJobCallback")
                finished += data.readInt() to (data.readInt() != 0)
                return true
            }
        }
        // Synthetic, unscheduled parameters: no real jobs, account calls, or wakelocks.
        fun params(id: Int): JobParameters {
            // SDK JobParameters Parcelable format (namespace added in API 34).
            // Do not relax the device's hidden-API restrictions to call its hidden constructor.
            val parcel = Parcel.obtain()
            return try {
                parcel.writeInt(id); parcel.writeString("local-perf-test")
                parcel.writePersistableBundle(PersistableBundle()); parcel.writeBundle(Bundle())
                parcel.writeInt(0) // No ClipData.
                parcel.writeStrongBinder(callback)
                parcel.writeInt(0); parcel.writeBoolean(false); parcel.writeBoolean(false)
                parcel.writeTypedArray(emptyArray<Uri>(), 0); parcel.writeStringArray(emptyArray())
                parcel.writeInt(0) // No Network.
                parcel.writeInt(0); parcel.writeInt(0); parcel.writeString(null)
                parcel.setDataPosition(0)
                JobParameters.CREATOR.createFromParcel(parcel).also { check(it.jobId == id) }
            } finally {
                parcel.recycle()
            }
        }
        val service = NotifyJobService()
        instrumentation.runOnMainSync {
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply { isAccessible = true }.invoke(service, instrumentation.targetContext)
            service.onBind(Intent())
        }
        val empty = FakeCall(); val success = FakeCall(); val failure = FakeCall()
        val stopped = FakeCall(); val independent = FakeCall(); val destroyed = FakeCall()
        val p1 = params(17404101); val p2 = params(17404102); val p3 = params(17404103)
        val p4 = params(17404104); val p5 = params(17404105); val p6 = params(17404106)
        instrumentation.runOnMainSync {
            service.requestMessages(p1, empty); empty.response(null)
            service.requestMessages(p2, success); success.response(MsgBean())
            service.requestMessages(p3, failure); failure.failure()
            service.requestMessages(p4, stopped); service.requestMessages(p5, independent)
            service.onStopJob(p4); stopped.failure()
            independent.response(MsgBean())
            service.requestMessages(p6, destroyed); service.onDestroy(); destroyed.failure()
        }
        instrumentation.waitForIdleSync(); SystemClock.sleep(250)
        val result = JSONObject().apply {
            put("emptyResponseFinishesOnce", finished.count { it.first == p1.jobId } == 1)
            put("successFinishesOnce", finished.count { it.first == p2.jobId && !it.second } == 1)
            put("failureUsesSchedulerBackoff", finished.count { it.first == p3.jobId && it.second } == 1)
            put("stopCancelsRequest", stopped.isCanceled)
            put("stoppedLateCallbackIgnored", finished.none { it.first == p4.jobId })
            put("otherJobNotCancelled", !independent.isCanceled && finished.count { it.first == p5.jobId } == 1)
            put("destroyCancelsRequest", destroyed.isCanceled)
            put("destroyedLateCallbackIgnored", finished.none { it.first == p6.jobId })
            put("realJobsScheduled", false); put("networkCalls", 0)
        }
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "app-performance-v17").apply { mkdirs() }
        File(directory, "job-checks.json").writeText(result.toString(2))
        assertTrue("Job ownership/completion checks: $result", result.keys().asSequence()
            .filter { it !in listOf("realJobsScheduled", "networkCalls") }.all { result.getBoolean(it) })
    }

    private class FakeCall : Call<MsgBean> {
        private lateinit var callback: Callback<MsgBean>
        private var cancelled = false
        private var executed = false
        override fun enqueue(callback: Callback<MsgBean>) { this.callback = callback; executed = true }
        fun response(body: MsgBean?) = callback.onResponse(this, Response.success(body))
        fun failure() = callback.onFailure(this, IOException("Synthetic failure"))
        override fun cancel() { cancelled = true }
        override fun isCanceled() = cancelled
        override fun isExecuted() = executed
        override fun execute(): Response<MsgBean> = error("No real network permitted")
        override fun clone(): Call<MsgBean> = FakeCall()
        override fun request(): Request = Request.Builder().url("https://invalid.test/").build()
        override fun timeout() = Timeout.NONE
    }
}
