package com.screening.dashboard.ui.frames

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.screening.dashboard.model.ImageInfo
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val DISPLAY_DURATION_MS = 10_000L

@Composable
fun ImageryFrame(
    images: List<ImageInfo>,
    serverBaseUrl: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Text(
                text = "No images",
                style = DashboardTypography.titleLarge.copy(color = TextDim)
            )
        }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(images.size) {
        while (true) {
            delay(DISPLAY_DURATION_MS)
            currentIndex = (currentIndex + 1) % images.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Crossfade(
            targetState = currentIndex,
            animationSpec = tween(1200),
            label = "gallery_crossfade"
        ) { idx ->
            val img = images[idx % images.size]
            val url = "$serverBaseUrl${img.url}"
            GallerySlide(url = url, image = img, imageLoader = imageLoader)
        }
    }
}

@Composable
private fun GallerySlide(
    url: String,
    image: ImageInfo,
    imageLoader: ImageLoader
) {
    val imageAR = if (image.height > 0) image.width.toFloat() / image.height else 1f
    val isPortrait = imageAR < 1f

    val context = LocalPlatformContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isPortrait) {
            // Portrait: fit on black, subtle Ken Burns
            val seed = remember(url) { Random.nextInt(4) }
            val kenBurns = remember(url) { Animatable(0f) }

            LaunchedEffect(url) {
                kenBurns.snapTo(0f)
                kenBurns.animateTo(
                    1f, tween(DISPLAY_DURATION_MS.toInt(), easing = LinearEasing)
                )
            }

            val p = kenBurns.value
            val scale = 1f + p * 0.05f
            val driftX = when (seed) { 0 -> p * 10f; 1 -> p * -10f; else -> 0f }
            val driftY = when (seed) { 2 -> p * -6f; 3 -> p * 6f; else -> 0f }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url).crossfade(true).size(Size(1920, 1080)).build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = driftX; translationY = driftY
                    }
            )
        } else {
            // Landscape: crop to fill, looks good
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url).crossfade(true).size(Size(1920, 1080)).build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
