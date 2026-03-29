package com.screening.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(
    defaultIp: String = "",
    onConnect: (String, Int) -> Unit,
    onScan: () -> Unit
) {
    var ip by remember { mutableStateOf(defaultIp) }

    Box(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Screening", style = MobileType.h1.copy(color = Cyan, fontSize = 32.sp))
            Spacer(Modifier.height(8.dp))
            Text("Connect to your server", style = MobileType.body)
            Spacer(Modifier.height(48.dp))

            Text("Server IP", style = MobileType.label, modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = ip,
                onValueChange = { ip = it },
                textStyle = TextStyle(color = TextW, fontSize = 20.sp),
                cursorBrush = SolidColor(Cyan),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Card)
                    .padding(16.dp),
                decorationBox = { inner ->
                    if (ip.isEmpty()) Text("192.168.1.100", color = Dim, fontSize = 20.sp)
                    inner()
                }
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { if (ip.isNotBlank()) onConnect(ip.trim(), 9900) },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                ) { Text("Connect", color = DarkBg) }

                OutlinedButton(onClick = onScan) {
                    Text("Auto-Scan", color = TextG)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Scan the QR code on your TV, or enter the IP manually", style = MobileType.label)
        }
    }
}
