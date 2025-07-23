package com.example.vasco

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

fun scheduleMedicationAlarm(
    context: Context,
    medName: String,
    triggerAtMillis: Long
) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Se Android 12+ e sem permissão para exact alarms, faz fallback inexact
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
        val intent = Intent(context, NotificationReceiver::class.java)
            .putExtra("MED_NAME", medName)
        val pi = PendingIntent.getBroadcast(
            context,
            medName.hashCode() and 0xffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        return
    }

    // Caso contrário (ou API < S), agenda exact
    val intent = Intent(context, NotificationReceiver::class.java)
        .putExtra("MED_NAME", medName)
    val pi = PendingIntent.getBroadcast(
        context,
        medName.hashCode() and 0xffff,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
}
