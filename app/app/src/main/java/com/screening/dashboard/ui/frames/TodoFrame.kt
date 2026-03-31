package com.screening.dashboard.ui.frames

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.screening.shared.model.TodoItem
import com.screening.dashboard.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TodoFrame(
    items: List<TodoItem>,
    onToggle: (String) -> Unit,
    onNavigateLeft: () -> Unit = {},
    onNavigateRight: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (done, pending) = items.partition { it.done }
    val sorted = pending.sortedBy { it.priority } + done

    val focusRequester = remember { FocusRequester() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(sorted.size) {
        if (selectedIndex >= sorted.size && sorted.isNotEmpty()) selectedIndex = sorted.size - 1
    }

    Column(
        modifier = modifier.fillMaxSize().padding(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 24.dp)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                if (sorted.isEmpty()) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { if (selectedIndex > 0) { selectedIndex--; coroutineScope.launch { listState.animateScrollToItem(selectedIndex) } }; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { if (selectedIndex < sorted.size - 1) { selectedIndex++; coroutineScope.launch { listState.animateScrollToItem(selectedIndex) } }; true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { sorted.getOrNull(selectedIndex)?.let { onToggle(it.id) }; true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onNavigateLeft(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onNavigateRight(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onBack(); true }
                    else -> false
                }
            }
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(text = "To Do", style = DashboardTypography.headlineLarge)
                Text(text = "${pending.size} remaining", style = DashboardTypography.bodyLarge.copy(color = PrimaryContainer))
            }
            Text(text = "PRIORITY VIEW", style = DashboardTypography.labelLarge.copy(color = Outline))
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing to do!", style = DashboardTypography.titleLarge.copy(color = Outline))
            }
        } else {
            // Glass container
            Box(
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainerLow.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(sorted, key = { _, item -> item.id }) { index, item ->
                        val isSelected = index == selectedIndex
                        val priorityColor = when (item.priority) { 1 -> AccentRed; 2 -> Amber; else -> TertiaryContainer }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SurfaceContainerHigh else SurfaceContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circle indicator
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape)
                                    .background(if (item.done) TertiaryContainer else SurfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.done) {
                                    Text(text = "\u2713", style = DashboardTypography.labelSmall.copy(color = OnPrimary))
                                } else {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(priorityColor))
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.text,
                                    style = DashboardTypography.titleMedium.copy(
                                        color = if (item.done) Outline else OnSurface,
                                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (item.priority) { 1 -> "High Priority"; 2 -> "Medium"; else -> "Low" },
                                    style = DashboardTypography.bodySmall.copy(color = if (item.done) OutlineVariant else Outline)
                                )
                            }

                            if (isSelected) {
                                Text(text = "\u203A", style = DashboardTypography.headlineMedium.copy(color = PrimaryContainer))
                            }
                        }
                    }
                }
            }
        }
    }
}
