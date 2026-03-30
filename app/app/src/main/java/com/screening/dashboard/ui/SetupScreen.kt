package com.screening.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.screening.dashboard.ui.theme.*

@Composable
fun SetupScreen(
    defaultIp: String = "",
    isScanning: Boolean = false,
    onConnect: (String, Int) -> Unit,
    onScan: () -> Unit
) {
    var ip by remember { mutableStateOf(defaultIp) }
    var port by remember { mutableStateOf("9900") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            Text(
                text = "Screening",
                style = DashboardTypography.headlineLarge.copy(color = AccentCyan)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect to your server",
                style = DashboardTypography.bodyLarge
            )
            Spacer(modifier = Modifier.height(48.dp))

            // IP input
            Text(
                text = "Server IP Address",
                style = DashboardTypography.bodyMedium,
                modifier = Modifier.fillMaxWidth(0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = ip,
                onValueChange = { ip = it },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 24.sp
                ),
                cursorBrush = SolidColor(AccentCyan),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .padding(16.dp)
                    .focusRequester(focusRequester)
                    .focusable(),
                decorationBox = { innerTextField ->
                    if (ip.isEmpty()) {
                        Text(
                            text = "192.168.1.100",
                            style = TextStyle(color = TextDim, fontSize = 24.sp)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Connect button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCyan)
                        .clickable { if (ip.isNotBlank()) onConnect(ip.trim(), 9900) }
                        .focusable()
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Connect",
                        style = DashboardTypography.titleLarge.copy(color = DarkBackground)
                    )
                }

                // Scan button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .clickable { onScan() }
                        .focusable()
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isScanning) "Scanning..." else "Auto-Scan",
                        style = DashboardTypography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Enter your server IP and press Enter, or use Auto-Scan",
                style = DashboardTypography.bodyMedium.copy(color = TextDim)
            )
        }
    }
}
