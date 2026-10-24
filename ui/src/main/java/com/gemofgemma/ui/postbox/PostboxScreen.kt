package com.gemofgemma.ui.postbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Duke addition (change 2): message a seat on Puppy - Duke or Kevin -
 * through the estate postbox, and read what the phone's mailbox holds.
 */
@Composable
fun PostboxScreen(viewModel: PostboxViewModel = hiltViewModel()) {
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var seat by remember { mutableStateOf("duke") }
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Postbox - message a seat",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            FilterChip(
                selected = seat == "duke",
                onClick = { seat = "duke" },
                label = { Text("Duke") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = seat == "kevin",
                onClick = { seat = "kevin" },
                label = { Text("Kevin") }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.send(seat, text)
                text = ""
            },
            enabled = !busy && text.isNotBlank()
        ) { Text("Send to ${seat.replaceFirstChar { it.uppercase() }}") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Text(
                "This phone's mailbox",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.refreshInbox() }, enabled = !busy) {
                Text("Refresh")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.clearInbox() }, enabled = !busy) {
                Text("Clear")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(inbox, key = { it.id }) { message ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${message.kind} - from ${message.from}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        message.payloadText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6
                    )
                }
            }
        }
    }
}
