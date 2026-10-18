package org.kealohalabs.receiptbeat.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import org.kealohalabs.receiptbeat.MainActivity
import org.kealohalabs.receiptbeat.R

object Notifications {
    private const val WORK = "work"
    private const val NUDGE = "nudges"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(WORK, "Imports and scans", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(NUDGE, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun working(context: Context, text: String, progress: Int? = null): ForegroundInfo {
        val n = NotificationCompat.Builder(context, WORK)
            .setSmallIcon(R.drawable.ic_stat_receipt)
            .setContentTitle("Receipt Beat")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .apply { if (progress != null) setProgress(100, progress, false) else setProgress(0, 0, true) }
            .setContentIntent(open(context, null))
            .build()
        return ForegroundInfo(WORK_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    fun nudge(context: Context, id: Int, title: String, text: String, route: String?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(context, NUDGE)
            .setSmallIcon(R.drawable.ic_stat_receipt)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open(context, route))
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun open(context: Context, route: String?): PendingIntent = PendingIntent.getActivity(
        context,
        route.hashCode(),
        Intent(context, MainActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private const val WORK_ID = 1001
}
