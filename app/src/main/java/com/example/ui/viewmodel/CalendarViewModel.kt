package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CalendarDatabase
import com.example.data.CalendarEvent
import com.example.data.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EventRepository

    init {
        val database = CalendarDatabase.getDatabase(application)
        repository = EventRepository(application, database.eventDao())
    }

    // Reactively observe all events from Room
    val allEvents: StateFlow<List<CalendarEvent>> = repository.allEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow<YearMonth>(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    // Derived: Events occurring on the selected date
    val selectedDateEvents: StateFlow<List<CalendarEvent>> = combine(allEvents, selectedDate) { events, date ->
        events.filter { event ->
            val eventDate = Instant.ofEpochMilli(event.dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            eventDate == date
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Derived: Set of local dates with events for quick calendar dot lookups
    val datesWithEvents: StateFlow<Set<LocalDate>> = allEvents.combine(currentMonth) { events, _ ->
        events.map { event ->
            Instant.ofEpochMilli(event.dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        // Automatically sync displayed month if user clicks a date outside the current month view
        val dateMonth = YearMonth.from(date)
        if (_currentMonth.value != dateMonth) {
            _currentMonth.value = dateMonth
        }
    }

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
    }

    fun prevMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
    }

    fun addEvent(title: String, description: String, time: LocalTime) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val zonedDateTime = date.atTime(time).atZone(ZoneId.systemDefault())
            val timestamp = zonedDateTime.toInstant().toEpochMilli()

            val newEvent = CalendarEvent(
                title = title,
                description = description,
                dateMillis = timestamp,
                hasNotification = true
            )
            repository.insertEvent(newEvent)
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                return CalendarViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
