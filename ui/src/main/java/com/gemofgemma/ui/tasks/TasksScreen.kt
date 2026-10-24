package com.gemofgemma.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * Duke addition (change 3): the Tasks screen - next up, being
 * processed, completed - fed by the Queue Manager through the postbox.
 * Long-press and drag a "next up" row to re-sort it; hold and release
 * ride the same road. The Control Terminal on Puppy is the other face
 * of this same queue.
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val next by viewModel.next.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tasks on Puppy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.refresh() }, enabled = !busy) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SectionTitle("Next up - long-press and drag to re-sort")
            }
            if (next.isEmpty()) {
                item { EmptyLine("Nothing waiting.") }
            }
            items(next, key = { "next-${it.id}" }) { job ->
                DraggableJobRow(
                    job = job,
                    index = next.indexOf(job),
                    listSize = next.size,
                    onMove = { position -> viewModel.move(job, position) },
                    actionLabel = if (job.state == "held") "Release" else "Hold",
                    onAction = {
                        if (job.state == "held") viewModel.release(job) else viewModel.hold(job)
                    }
                )
            }
            item { SectionTitle("Being processed") }
            if (running.isEmpty()) {
                item { EmptyLine("Nothing running right now.") }
            }
            items(running, key = { "run-${it.id}" }) { job ->
                JobRow(job = job, actionLabel = null, onAction = null)
            }
            item { SectionTitle("Completed") }
            if (done.isEmpty()) {
                item { EmptyLine("No completed history yet.") }
            }
            items(done, key = { "done-${it.id}" }) { job ->
                JobRow(job = job, actionLabel = null, onAction = null)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun JobRow(job: QmJob, actionLabel: String?, onAction: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(job.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${job.id} - ${job.kind} - ${job.state}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun DraggableJobRow(
    job: QmJob,
    index: Int,
    listSize: Int,
    onMove: (Int) -> Unit,
    actionLabel: String,
    onAction: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surface
            )
            .onSizeChanged { rowHeight = it.height.toFloat() }
            .pointerInput(job.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        if (rowHeight > 0f) {
                            val steps = (dragOffset / rowHeight).roundToInt()
                            val target = (index + steps).coerceIn(0, maxOf(0, listSize - 1))
                            if (target != index) {
                                onMove(target)
                            }
                        }
                        dragging = false
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                    }
                )
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Drag to re-sort",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(job.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${job.id} - ${job.kind} - ${job.state} - priority ${job.priority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}
