package com.screening.dashboard.model

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
data class ServerMessage(
    val type: String,
    val events: List<CalendarEvent>? = null,
    val items: List<TodoItem>? = null,
    val images: List<ImageInfo>? = null,
    val videos: List<VideoInfo>? = null,
    val music: List<MusicTrack>? = null,
    val url: String? = null  // for screen_share_active
)

@Serializable
data class ClientMessage(
    val type: String,
    val id: String? = null
)

data class DashboardState(
    val events: List<CalendarEvent> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val images: List<ImageInfo> = emptyList(),
    val videos: List<VideoInfo> = emptyList(),
    val music: List<MusicTrack> = emptyList(),
    val connected: Boolean = false,
    val serverBaseUrl: String = "",
    val screenShareUrl: String? = null  // non-null = screen share active
)
