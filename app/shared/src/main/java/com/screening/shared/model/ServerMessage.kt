package com.screening.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    @SerialName("allDay") val allDay: Boolean = false
)

@Serializable
data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean,
    val priority: Int
)

@Serializable
data class ImageInfo(
    val filename: String,
    val width: Int,
    val height: Int,
    val url: String
)

@Serializable
data class VideoInfo(
    val filename: String,
    val url: String
)

@Serializable
data class MusicTrack(
    val filename: String,
    val url: String
)

@Serializable
data class HabitInfo(
    val id: String,
    val name: String,
    val streak: Int = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("done_today") val doneToday: Boolean = false,
    val history: List<String> = emptyList(),
    @SerialName("total_done") val totalDone: Int = 0
)

@Serializable
data class PomodoroInfo(
    val remaining: Int,
    val duration: Int,
    val running: Boolean
)

@Serializable
data class DeviceInfo(
    val id: String,
    val type: String,
    val name: String,
    val connected: Boolean = true
)

@Serializable
data class ServerMessage(
    val type: String,
    val events: List<CalendarEvent>? = null,
    val items: List<TodoItem>? = null,
    val images: List<ImageInfo>? = null,
    val videos: List<VideoInfo>? = null,
    val music: List<MusicTrack>? = null,
    val habits: List<HabitInfo>? = null,
    val pomodoro: PomodoroInfo? = null,
    val devices: List<DeviceInfo>? = null,
    val url: String? = null,
    val frame: Int? = null,
    val target: String? = null,
    val weather: WeatherInfo? = null
)

@Serializable
data class WeatherInfo(
    @SerialName("temp_f") val tempF: String = "",
    @SerialName("temp_c") val tempC: String = "",
    val condition: String = "",
    val emoji: String = ""
)

@Serializable
data class ClientMessage(
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val name: String? = null,
    val priority: Int? = null,
    val minutes: Int? = null,
    val frame: Int? = null,
    val target: String? = null,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("device_name") val deviceName: String? = null
)

data class DashboardState(
    val events: List<CalendarEvent> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val images: List<ImageInfo> = emptyList(),
    val videos: List<VideoInfo> = emptyList(),
    val music: List<MusicTrack> = emptyList(),
    val habits: List<HabitInfo> = emptyList(),
    val pomodoro: PomodoroInfo? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val connected: Boolean = false,
    val serverBaseUrl: String = "",
    val screenShareUrl: String? = null,
    val forceFrame: Int? = null,
    val showQR: Boolean = false,
    val alarmTitle: String? = null,
    val alarmTime: String? = null,
    val weatherEmoji: String = "",
    val weatherTemp: String = ""
)
