package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.CalendarDatabase
import com.example.data.CalendarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform update in background coroutine to avoid ANR
        val database = CalendarDatabase.getDatabase(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            val allEventsFlow = database.eventDao().getAllEvents()
            val allEventsList = allEventsFlow.firstOrNull() ?: emptyList()

            val today = LocalDate.now()
            
            // Filter events for today
            val todayEvents = allEventsList.filter { event ->
                val eventDate = Instant.ofEpochMilli(event.dateMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                eventDate == today
            }.sortedBy { event ->
                Instant.ofEpochMilli(event.dateMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
            }

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, today, todayEvents)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        today: LocalDate,
        events: List<CalendarEvent>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Setup date text
        val dayNumFormatter = DateTimeFormatter.ofPattern("d", Locale("ru"))
        val monthFormatter = DateTimeFormatter.ofPattern("LLLL", Locale("ru"))
        val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", Locale("ru"))

        val dayNumber = today.format(dayNumFormatter)
        val monthName = today.format(monthFormatter).replaceFirstChar { it.titlecase(Locale("ru")) }
        val dayOfWeek = today.format(dayOfWeekFormatter).replaceFirstChar { it.titlecase(Locale("ru")) }

        views.setTextViewText(R.id.widget_day_number, dayNumber)
        views.setTextViewText(R.id.widget_month_name, monthName)
        views.setTextViewText(R.id.widget_day_of_week, dayOfWeek)

        // Reset views visibility first
        views.setViewVisibility(R.id.widget_empty_text, View.GONE)
        views.setViewVisibility(R.id.widget_event_1, View.GONE)
        views.setViewVisibility(R.id.widget_event_2, View.GONE)
        views.setViewVisibility(R.id.widget_event_3, View.GONE)
        views.setViewVisibility(R.id.widget_more_events_text, View.GONE)

        if (events.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
        } else {
            val maxToShow = 3
            val size = events.size

            for (i in 0 until minOf(size, maxToShow)) {
                val event = events[i]
                val eventTime = Instant.ofEpochMilli(event.dateMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                val formattedTime = eventTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                
                val displayText = "$formattedTime - ${event.title}"
                val (rowId, textId) = when (i) {
                    0 -> R.id.widget_event_1 to R.id.widget_event_text_1
                    1 -> R.id.widget_event_2 to R.id.widget_event_text_2
                    else -> R.id.widget_event_3 to R.id.widget_event_text_3
                }

                views.setViewVisibility(rowId, View.VISIBLE)
                views.setTextViewText(textId, displayText)
            }

            if (size > maxToShow) {
                views.setViewVisibility(R.id.widget_more_events_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_more_events_text, "+ еще ${size - maxToShow}...")
            }
        }

        // PendingIntent to launch app when clicked
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Instruct widget manager to update
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, CalendarWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
