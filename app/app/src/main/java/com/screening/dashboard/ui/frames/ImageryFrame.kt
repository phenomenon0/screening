package com.screening.dashboard.ui.frames

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.screening.shared.model.ImageInfo
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.tv.material3.Text

private const val DISPLAY_DURATION_MS = 10_000L

@Composable
fun ImageryFrame(
    images: List<ImageInfo>,
    serverBaseUrl: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Text("No images", style = DashboardTypography.titleLarge.copy(color = Outline))
        }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(images.size) {
        while (true) { delay(DISPLAY_DURATION_MS); currentIndex = (currentIndex + 1) % images.size }
    }

    val currentImage = images[currentIndex % images.size]

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = currentIndex, animationSpec = tween(1200), label = "gallery") { idx ->
            val img = images[idx % images.size]
            val url = "$serverBaseUrl${img.url}"
            GallerySlide(url = url, image = img, imageLoader = imageLoader)
        }

        // Top gradient for status bar legibility
        Box(modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))

        // Bottom gradient + photo info overlay
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .padding(start = 24.dp, end = 48.dp, bottom = 48.dp, top = 64.dp)
        ) {
            Column {
                Text(
                    text = cleanImageName(currentImage.filename),
                    style = DashboardTypography.headlineLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (currentImage.width > 0) {
                        Text(text = "${currentImage.width}x${currentImage.height}", style = DashboardTypography.labelMedium.copy(color = OnSurfaceVariant))
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySlide(url: String, image: ImageInfo, imageLoader: ImageLoader) {
    val imageAR = if (image.height > 0) image.width.toFloat() / image.height else 1f
    val isPortrait = imageAR < 1f
    val context = LocalPlatformContext.current

    // Cinematic zoom
    val kenBurns = remember(url) { Animatable(0f) }
    LaunchedEffect(url) {
        kenBurns.snapTo(0f)
        kenBurns.animateTo(1f, tween(DISPLAY_DURATION_MS.toInt(), easing = LinearEasing))
    }
    val p = kenBurns.value
    val scale = 1.02f + p * 0.04f
    val seed = remember(url) { Random.nextInt(4) }
    val driftX = when (seed) { 0 -> p * 8f; 1 -> p * -8f; else -> 0f }
    val driftY = when (seed) { 2 -> p * -5f; 3 -> p * 5f; else -> 0f }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).size(Size(1920, 1080)).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = if (isPortrait) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = driftX; translationY = driftY
            }
        )
    }
}

private fun cleanImageName(filename: String): String {
    return filename.substringBeforeLast(".").replace("_", " ").replace("-", " ").trim()
}
