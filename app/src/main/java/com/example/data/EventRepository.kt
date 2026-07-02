package com.example.data

import android.content.Context
import com.example.receiver.AlarmHelper
import kotlinx.coroutines.flow.Flow

class EventRepository(
    private val context: Context,
    private val eventDao: EventDao
) {
    val allEvents: Flow<List<CalendarEvent>> = eventDao.getAllEvents()

    suspend fun insertEvent(event: CalendarEvent) {
        val id = eventDao.insertEvent(event)
        val eventWithId = event.copy(id = id.toInt())
        
        if (eventWithId.hasNotification) {
            AlarmHelper.scheduleAlarm(context, eventWithId)
        }
    }

    suspend fun deleteEvent(event: CalendarEvent) {
        AlarmHelper.cancelAlarm(context, event.id)
        eventDao.deleteEvent(event)
    }

    suspend fun deleteEventById(id: Int) {
        AlarmHelper.cancelAlarm(context, id)
        eventDao.deleteEventById(id)
    }
}
