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
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.screening.dashboard.data.AiAction
import com.screening.dashboard.data.AiClient
import com.screening.dashboard.data.VoiceSession
import com.screening.dashboard.ui.theme.*
import com.screening.shared.model.DashboardState
import kotlinx.coroutines.launch

private enum class MicState { IDLE, RECORDING, TRANSCRIBING, THINKING }
private data class ChatMessage(val role: String, val text: String, val imageUrl: String? = null)

@Composable
fun ChatFrame(
    state: DashboardState,
    okTrigger: Int,
    onToggleTodo: (String) -> Unit,
    onAddTodo: (String, Int) -> Unit,
    onToggleHabit: (String) -> Unit,
    onSwitchFrame: (Int) -> Unit,
    onClearAiQuery: () -> Unit = {},
    onMusicControl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var micState by remember { mutableStateOf(MicState.IDLE) }

    val voiceSession = remember { VoiceSession() }
    val aiClient = remember { AiClient(state.serverBaseUrl) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Auto-scroll + cap at 50 messages to prevent memory bloat on TV
    LaunchedEffect(messages.size) {
        if (messages.size > 50) messages.removeRange(0, messages.size - 50)
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Clean up on navigate away
    DisposableEffect(Unit) {
        onDispose { voiceSession.stopRecording() }
    }

    // Handle queries from the phone remote.
    // Must use scope.launch — LaunchedEffect key changes when onClearAiQuery() runs,
    // which would cancel the in-flight Claude API call.
    LaunchedEffect(state.aiQuery) {
        val query = state.aiQuery ?: return@LaunchedEffect
        onClearAiQuery()
        scope.launch {
            messages.add(ChatMessage("user", query))
            micState = MicState.THINKING
            val response = aiClient.ask(query, state)
            messages.add(ChatMessage("assistant", response.text, imageUrl = response.imageUrl))
            response.actions.forEach { action ->
                when (action) {
                    is AiAction.SwitchFrame  -> onSwitchFrame(action.frame)
                    is AiAction.ToggleTodo   -> onToggleTodo(action.id)
                    is AiAction.AddTodo      -> onAddTodo(action.text, action.priority)
                    is AiAction.ToggleHabit  -> onToggleHabit(action.id)
                    is AiAction.MusicControl -> onMusicControl(action.action)
                    is AiAction.ShowImage    -> { /* handled via imageUrl */ }
                }
            }
            micState = MicState.IDLE
        }
    }

    // OK button trigger from DashboardScreen (avoids Crossfade focus issue)
    // Track last processed trigger to avoid re-firing when re-entering the frame
    var lastProcessedTrigger by remember { mutableIntStateOf(okTrigger) }
    LaunchedEffect(okTrigger) {
        if (okTrigger == 0 || okTrigger == lastProcessedTrigger) return@LaunchedEffect
        lastProcessedTrigger = okTrigger
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
                        messages.add(ChatMessage("assistant", response.text, imageUrl = response.imageUrl))
                        response.actions.forEach { action ->
                            when (action) {
                                is AiAction.SwitchFrame  -> onSwitchFrame(action.frame)
                                is AiAction.ToggleTodo   -> onToggleTodo(action.id)
                                is AiAction.AddTodo      -> onAddTodo(action.text, action.priority)
                                is AiAction.ToggleHabit  -> onToggleHabit(action.id)
                                is AiAction.MusicControl -> onMusicControl(action.action)
                                is AiAction.ShowImage    -> { /* handled via imageUrl */ }
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
                    text = "BACK to exit",
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
                                Text(text = "🤖", style = DashboardTypography.displayLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "AI Assistant",
                                    style = DashboardTypography.headlineLarge.copy(
                                        fontFamily = ManropeFamily,
                                        color = PrimaryContainer
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Open the AI tab on your phone to ask questions,\nsearch the web, control music, or manage your dashboard",
                                    style = DashboardTypography.bodyLarge.copy(color = OnSurfaceVariant),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "🔍 Web Search   📝 Notes   🎵 Music   📺 TV Control",
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
                        text = if (messages.isEmpty()) "Waiting for input from phone…" else "Ready",
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
                            text = "Listening…",
                            style = DashboardTypography.titleMedium.copy(color = OnSurface)
                        )
                    }
                    MicState.TRANSCRIBING -> Text(
                        text = "Transcribing…",
                        style = DashboardTypography.titleMedium.copy(color = PrimaryContainer)
                    )
                    MicState.THINKING -> Text(
                        text = "Thinking… (Opus)",
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
        Column(modifier = Modifier.widthIn(max = 700.dp)) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = if (msg.imageUrl != null) 4.dp else 16.dp,
                            bottomEnd = if (msg.imageUrl != null) 4.dp else 16.dp
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
            if (msg.imageUrl != null) {
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current).data(msg.imageUrl).build(),
                    contentDescription = "Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
