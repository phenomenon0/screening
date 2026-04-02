package com.screening.dashboard.ui.frames

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import com.screening.dashboard.data.AiAction
import com.screening.dashboard.data.AiClient
import com.screening.dashboard.data.VoiceSession
import com.screening.dashboard.ui.theme.*
import com.screening.shared.model.DashboardState
import kotlinx.coroutines.launch

private enum class MicState { IDLE, RECORDING, TRANSCRIBING, THINKING }
private data class ChatMessage(val role: String, val text: String)

@Composable
fun ChatFrame(
    state: DashboardState,
    okTrigger: Int,
    onToggleTodo: (String) -> Unit,
    onAddTodo: (String, Int) -> Unit,
    onToggleHabit: (String) -> Unit,
    onSwitchFrame: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var micState by remember { mutableStateOf(MicState.IDLE) }

    val voiceSession = remember { VoiceSession() }
    val aiClient = remember { AiClient() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Clean up on navigate away
    DisposableEffect(Unit) {
        onDispose { voiceSession.stopRecording() }
    }

    // OK button trigger from DashboardScreen (avoids Crossfade focus issue)
    LaunchedEffect(okTrigger) {
        if (okTrigger == 0) return@LaunchedEffect
        when (micState) {
            MicState.IDLE -> {
                if (!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@LaunchedEffect
                }
                micState = MicState.RECORDING
                voiceSession.startRecording()
                scope.launch {
                    voiceSession.collectAudio()
                    micState = MicState.TRANSCRIBING
                    val transcript = voiceSession.transcribe()
                    if (transcript.isNotBlank()) {
                        messages.add(ChatMessage("user", transcript))
                        micState = MicState.THINKING
                        val response = aiClient.ask(transcript, state)
                        messages.add(ChatMessage("assistant", response.text))
                        response.actions.forEach { action ->
                            when (action) {
                                is AiAction.SwitchFrame -> onSwitchFrame(action.frame)
                                is AiAction.ToggleTodo  -> onToggleTodo(action.id)
                                is AiAction.AddTodo     -> onAddTodo(action.text, action.priority)
                                is AiAction.ToggleHabit -> onToggleHabit(action.id)
                            }
                        }
                    }
                    micState = MicState.IDLE
                }
            }
            MicState.RECORDING -> voiceSession.stopRecording()
            else -> { /* busy */ }
        }
    }

    // Pulsing dot for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Background)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Assistant",
                    style = DashboardTypography.headlineMedium.copy(
                        fontFamily = ManropeFamily,
                        color = OnSurface
                    )
                )
                Text(
                    text = "OK to speak  •  ↑ or BACK to exit",
                    style = DashboardTypography.labelSmall.copy(color = OnSurfaceVariant)
                )
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxWidth().padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🎙", style = DashboardTypography.displayLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Press OK on your remote to speak",
                                    style = DashboardTypography.titleLarge.copy(color = OnSurfaceVariant),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ask about your calendar, todos, habits — or control the TV",
                                    style = DashboardTypography.bodyMedium.copy(color = OnSurfaceVariant),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                items(messages) { msg -> MessageBubble(msg) }
            }

            // Status bar
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerHigh)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                when (micState) {
                    MicState.IDLE -> Text(
                        text = "🎙  Press OK to speak",
                        style = DashboardTypography.titleMedium.copy(color = OnSurfaceVariant)
                    )
                    MicState.RECORDING -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(AccentRed)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Listening… press OK to stop",
                            style = DashboardTypography.titleMedium.copy(color = OnSurface)
                        )
                    }
                    MicState.TRANSCRIBING -> Text(
                        text = "Transcribing…",
                        style = DashboardTypography.titleMedium.copy(color = PrimaryContainer)
                    )
                    MicState.THINKING -> Text(
                        text = "Thinking…",
                        style = DashboardTypography.titleMedium.copy(color = PrimaryContainer)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Text(
                text = "🤖",
                modifier = Modifier.padding(end = 8.dp, top = 4.dp),
                style = DashboardTypography.titleMedium
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(if (isUser) PrimaryContainer.copy(alpha = 0.25f) else SurfaceContainerHigh)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = msg.text,
                style = DashboardTypography.bodyLarge.copy(
                    color = if (isUser) PrimaryContainer else OnSurface,
                    fontSize = 18.sp
                )
            )
        }
    }
}
