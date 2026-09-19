package com.flowa.data

import android.app.*
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.content.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("reminders", "随心记提醒事项", NotificationManager.IMPORTANCE_DEFAULT))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val notification = Notification.Builder(context, "reminders").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(intent.getStringExtra("title") ?: "随心记提醒").setContentText(intent.getStringExtra("notes")).setAutoCancel(true)
        if (launch != null) notification.setContentIntent(PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.notify(intent.action.hashCode(), notification.build())
    }
}
