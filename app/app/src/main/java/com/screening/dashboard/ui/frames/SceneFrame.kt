package com.screening.dashboard.ui.frames

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*

@Composable
fun SceneFrame(
    serverBaseUrl: String,
    sceneId: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val moonlightInstalled = remember {
        isMoonlightInstalled(context)
    }

    // Auto-launch Moonlight if installed
    LaunchedEffect(Unit) {
        if (moonlightInstalled) {
            launchMoonlight(context)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (moonlightInstalled) {
                Text(
                    text = "Worldcast",
                    style = DashboardTypography.headlineLarge.copy(color = AccentCyan)
                )
                Text(
                    text = "Launching Moonlight stream...",
                    style = DashboardTypography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .clickable { launchMoonlight(context) }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open Moonlight",
                        style = DashboardTypography.titleLarge
                    )
                }
            } else {
                Text(
                    text = "Worldcast",
                    style = DashboardTypography.headlineLarge.copy(color = AccentCyan)
                )
                Text(
                    text = "Install Moonlight for ultra-low latency 3D streaming",
                    style = DashboardTypography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Search \"Moonlight\" in the Amazon Appstore",
                    style = DashboardTypography.bodyMedium.copy(color = TextDim)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCyan)
                        .clickable { openAppStore(context) }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open Appstore",
                        style = DashboardTypography.titleLarge.copy(color = DarkBackground)
                    )
                }
            }
        }
    }
}

private fun isMoonlightInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.limelight", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

private fun launchMoonlight(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.limelight")
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun openAppStore(context: Context) {
    try {
        // Amazon Appstore
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("amzn://apps/android?p=com.limelight")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback to browser
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://moonlight-stream.org")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
