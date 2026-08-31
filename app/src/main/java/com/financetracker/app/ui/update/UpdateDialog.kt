package com.financetracker.app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.update.UpdateInfo
import com.financetracker.app.data.update.UpdateUiState
import com.financetracker.app.ui.common.ProgressBar
import com.financetracker.app.ui.theme.Accent

/**
 * Offered, not forced. An update that installs itself would interrupt whatever the user opened the
 * app to do, and "Later" has to actually mean later.
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = state.downloading || state.installing

    AlertDialog(
        // A download in flight must not be dismissed by a stray tap outside the dialog.
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Update available") },
        text = {
            Column {
                Text(
                    "Version ${info.versionName} is ready to install.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        info.notes.trim().take(300),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.downloading) {
                    Spacer(Modifier.height(14.dp))
                    val progress = state.downloadProgress
                    if (progress != null) {
                        ProgressBar(fraction = progress, color = Accent)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Downloading…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                state.message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Downloads about 42 MB from GitHub.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !busy) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Later") }
        }
    )
}
