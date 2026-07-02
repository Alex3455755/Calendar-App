package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.CalendarEvent

object AlarmHelper {
    fun scheduleAlarm(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        // Don't schedule alarm if the time has already passed
        if (event.dateMillis <= System.currentTimeMillis()) {
            Log.d("AlarmHelper", "Skipping alarm scheduling because event time is in the past: ${event.dateMillis}")
            return
        }

        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("event_title", event.title)
            putExtra("event_desc", event.description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Ensure it wakes up and schedules reliably
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    event.dateMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    event.dateMillis,
                    pendingIntent
                )
            }
            Log.d("AlarmHelper", "Successfully scheduled alarm for event ${event.id} at timestamp ${event.dateMillis}")
        } catch (e: Exception) {
            Log.e("AlarmHelper", "Failed to schedule alarm", e)
        }
    }

    fun cancelAlarm(context: Context, eventId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, EventAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmHelper", "Successfully cancelled alarm for event $eventId")
        }
    }
}
