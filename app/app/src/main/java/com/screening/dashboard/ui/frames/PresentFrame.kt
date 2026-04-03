package com.screening.dashboard.ui.frames

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.screening.dashboard.ui.theme.*
import com.screening.shared.model.PresentationInfo
import kotlinx.coroutines.delay

@Composable
fun PresentFrame(
    presentation: PresentationInfo,
    serverBaseUrl: String,
    imageLoader: ImageLoader,
    onNavigateLeft: () -> Unit,
    onNavigateRight: () -> Unit,
    onPageChange: (Int) -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val currentPage = presentation.current.coerceIn(0, (presentation.pageCount - 1).coerceAtLeast(0))
    val pageUrl = if (presentation.pages.isNotEmpty() && currentPage < presentation.pages.size) {
        "$serverBaseUrl${presentation.pages[currentPage].url}"
    } else null

    // Zoom / pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var fillMode by remember { mutableStateOf(ContentScale.Fit) } // Fit vs Crop (full-screen)

    // Reset zoom/pan on page change
    LaunchedEffect(currentPage) {
        scale = 1f; offsetX = 0f; offsetY = 0f
    }

    // HUD visibility — auto-hide after 4 seconds of no interaction
    var showHud by remember { mutableStateOf(true) }
    var hudKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudKey) {
        showHud = true
        delay(4000)
        showHud = false
    }

    fun interact() { hudKey++ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                interact()
                when (event.nativeKeyEvent.keyCode) {
                    // Next page
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (currentPage < presentation.pageCount - 1) onPageChange(currentPage + 1)
                        true
                    }
                    // Prev page
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (currentPage > 0) onPageChange(currentPage - 1)
                        true
                    }
                    // Toggle full-screen (Fit ↔ Crop)
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        fillMode = if (fillMode == ContentScale.Fit) ContentScale.Crop else ContentScale.Fit
                        scale = 1f; offsetX = 0f; offsetY = 0f
                        true
                    }
                    // Zoom in/out cycle: 1x → 1.5x → 2x → 3x → 1x
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        scale = when {
                            scale < 1.4f -> 1.5f
                            scale < 1.9f -> 2f
                            scale < 2.9f -> 3f
                            else -> 1f
                        }
                        if (scale == 1f) { offsetX = 0f; offsetY = 0f }
                        true
                    }
                    // OK / Center = next page (tap-advance)
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (currentPage < presentation.pageCount - 1) onPageChange(currentPage + 1)
                        true
                    }
                    // Close
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                        if (scale > 1.1f) {
                            // Reset zoom first, don't exit
                            scale = 1f; offsetX = 0f; offsetY = 0f
                            true
                        } else {
                            onClose(); onBack(); true
                        }
                    }
                    else -> false
                }
            }
            // Touch: pinch to zoom + drag to pan
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    interact()
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        // Page image — full screen with crossfade + zoom/pan
        Crossfade(
            targetState = pageUrl,
            animationSpec = tween(300),
            label = "page_crossfade"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(url)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = "Page ${currentPage + 1}",
                    contentScale = fillMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pages", style = DashboardTypography.titleLarge.copy(color = Outline))
                }
            }
        }

        // HUD overlay — auto-hides
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Page indicator — bottom center
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Background.copy(alpha = 0.7f))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${currentPage + 1} / ${presentation.pageCount}  —  ${presentation.title}",
                        style = DashboardTypography.titleMedium.copy(
                            fontFamily = ManropeFamily,
                            color = OnSurface,
                            fontSize = 16.sp
                        )
                    )
                }

                // Zoom indicator (only when zoomed)
                if (scale > 1.1f || fillMode == ContentScale.Crop) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 16.dp, start = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Background.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (scale > 1.1f) "${String.format(java.util.Locale.US, "%.1f", scale)}x zoom" else "Full Screen",
                            style = DashboardTypography.labelSmall.copy(color = PrimaryContainer)
                        )
                    }
                }

                // Controls hint — top right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Background.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "←→ pages  ↑ fill  ↓ zoom  BACK exit",
                        style = DashboardTypography.labelSmall.copy(color = OnSurfaceVariant)
                    )
                }
            }
        }
    }
}
