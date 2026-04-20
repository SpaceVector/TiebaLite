package com.huanchengfly.tieba.post.services

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.TiebaApi
import com.huanchengfly.tieba.post.api.models.MsgBean
import com.huanchengfly.tieba.post.pendingIntentFlagImmutable
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import com.huanchengfly.tieba.post.utils.JobServiceUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs

class NotifyJobService : JobService() {
    var notificationManager: NotificationManager? = null
    private fun createChannel(id: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                id,
                name, NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.group = CHANNEL_GROUP
            channel.setShowBadge(true)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelGroup = NotificationChannelGroup(CHANNEL_GROUP, CHANNEL_GROUP_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    channelGroup.description = "贴吧的各种消息通知"
                }
                notificationManager!!.createNotificationChannelGroup(channelGroup)
                createChannel(CHANNEL_REPLY, CHANNEL_REPLY_NAME)
                createChannel(CHANNEL_AT, CHANNEL_AT_NAME)
            }
        }
        TiebaApi.getInstance().msg().enqueue(object : Callback<MsgBean> {
            override fun onFailure(call: Call<MsgBean>, t: Throwable) {
                jobFinished(params, true)
            }

            override fun onResponse(call: Call<MsgBean>, response: Response<MsgBean>) {
                val msgBean = response.body() ?: return
                if (notificationManager != null) {
                    var total = 0
                    if ("0" != msgBean.message?.replyMe) {
                        val replyCount = msgBean.message?.replyMe?.let { Integer.valueOf(it) }
                        if (replyCount != null) {
                            total += replyCount
                        }
                        sendBroadcast(
                            Intent()
                                .setAction(ACTION_NEW_MESSAGE)
                                .putExtra("channel", CHANNEL_REPLY)
                                .putExtra("count", replyCount)
                        )
                        updateNotification(
                            getString(
                                R.string.tips_message_reply,
                                msgBean.message?.replyMe
                            ),
                            ID_REPLY,
                            CHANNEL_REPLY,
                            CHANNEL_REPLY_NAME,
                            Intent(ACTION_VIEW, Uri.parse("tblite://notifications/0"))
                        )
                    }
                    if ("0" != msgBean.message?.atMe) {
                        val atCount = msgBean.message?.atMe?.let { Integer.valueOf(it) }
                        if (atCount != null) {
                            total += atCount
                        }
                        sendBroadcast(
                            Intent()
                                .setAction(ACTION_NEW_MESSAGE)
                                .putExtra("channel", CHANNEL_AT)
                                .putExtra("count", msgBean.message?.atMe)
                        )
                        updateNotification(
                            getString(
                                R.string.tips_message_at,
                                msgBean.message?.atMe
                            ),
                            ID_AT,
                            CHANNEL_AT,
                            CHANNEL_AT_NAME,
                            Intent(ACTION_VIEW, Uri.parse("tblite://notifications/1"))
                        )
                    }
                    sendBroadcast(
                        Intent()
                            .setAction(ACTION_NEW_MESSAGE)
                            .putExtra("channel", CHANNEL_TOTAL)
                            .putExtra("count", total)
                    )
                }
                jobFinished(params, false)
            }
        })
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }

    private fun updateNotification(
        text: String,
        id: Int,
        channel: String,
        channelName: String,
        intent: Intent
    ) {
        val notification = NotificationCompat.Builder(this, channel)
            .setSubText(channelName)
            .setContentText(getString(R.string.tip_touch_to_view))
            .setContentTitle(text)
            .setSmallIcon(R.drawable.ic_round_drafts)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    pendingIntentFlagImmutable()
                )
            )
            .setColor(ThemeUtils.getColorByAttr(this, R.attr.colorPrimary))
            .build()
        notificationManager!!.notify(id, notification)
    }

    companion object {
        const val ACTION_NEW_MESSAGE = "com.huanchengfly.tieba.post.action.NEW_MESSAGE"
        const val CHANNEL_GROUP = "20"
        const val CHANNEL_AT = "3"
        const val CHANNEL_AT_NAME = "提到我的"
        const val CHANNEL_TOTAL = "total"
        const val ID_REPLY = 20
        const val ID_AT = 21
        private const val IMMEDIATE_JOB_ID = 200001
        private const val CHANNEL_GROUP_NAME = "消息通知"
        private const val CHANNEL_REPLY = "2"
        private const val CHANNEL_REPLY_NAME = "回复我的"
        private const val PREFS_NOTIFY_JOB = "notify_job"
        private const val KEY_LAST_IMMEDIATE_SCHEDULE_AT = "last_immediate_schedule_at"
        private const val MIN_IMMEDIATE_SCHEDULE_INTERVAL_MS = 60_000L

        private fun getJobScheduler(context: Context): JobScheduler {
            return context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        }

        private fun hasPendingJob(context: Context, jobId: Int): Boolean {
            return getJobScheduler(context).allPendingJobs.any { it.id == jobId }
        }

        fun scheduleImmediate(context: Context) {
            if (hasPendingJob(context, IMMEDIATE_JOB_ID)) {
                return
            }
            val prefs = context.getSharedPreferences(PREFS_NOTIFY_JOB, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastScheduledAt = prefs.getLong(KEY_LAST_IMMEDIATE_SCHEDULE_AT, 0L)
            if (abs(now - lastScheduledAt) < MIN_IMMEDIATE_SCHEDULE_INTERVAL_MS) {
                return
            }
            prefs.edit().putLong(KEY_LAST_IMMEDIATE_SCHEDULE_AT, now).apply()
            val jobScheduler = getJobScheduler(context)
            val builder = JobInfo.Builder(
                IMMEDIATE_JOB_ID,
                ComponentName(context, NotifyJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(0L)
                .setOverrideDeadline(0L)
            jobScheduler.schedule(builder.build())
        }

        fun schedulePeriodic(context: Context) {
            if (hasPendingJob(context, JobServiceUtil.getJobId(context))) {
                return
            }
            val jobScheduler = getJobScheduler(context)
            val builder = JobInfo.Builder(
                JobServiceUtil.getJobId(context),
                ComponentName(context, NotifyJobService::class.java)
            )
                .setPersisted(true)
                .setPeriodic(30 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            jobScheduler.schedule(builder.build())
        }
    }
}
