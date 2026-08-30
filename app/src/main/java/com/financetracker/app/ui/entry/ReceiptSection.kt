package com.financetracker.app.ui.entry

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.financetracker.app.data.Money
import com.financetracker.app.data.receipt.ReceiptReading
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.BorderColor
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Surface1
import java.io.File

/**
 * Attach a receipt, from the camera or from a file.
 *
 * The camera route goes through ACTION_IMAGE_CAPTURE, which the system camera app services on this
 * app's behalf. That is deliberate: it means no CAMERA permission is declared or requested, and the
 * app keeps its property of asking for nothing at all.
 */
@Composable
fun ReceiptSection(
    attachmentName: String?,
    attaching: Boolean,
    scanning: Boolean,
    reading: ReceiptReading?,
    currencyCode: String,
    uriFor: (String) -> Uri?,
    onPickTarget: () -> Pair<File, Uri>,
    onCaptured: (File) -> Unit,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    onAcceptReading: () -> Unit,
    onDismissReading: () -> Unit
) {
    val context = LocalContext.current
    var pendingCapture by remember { mutableStateOf<File?>(null) }
    var viewing by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCapture
        pendingCapture = null
        if (success && file != null) onCaptured(file)
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPicked) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Receipt",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (attachmentName != null) {
            val bitmap = rememberReceiptThumbnail(attachmentName, uriFor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                        .clickable { viewing = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Receipt",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            "?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Attached", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Tap to view full size",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRemove) { Text("Remove", color = Negative) }
            }

            if (scanning) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Reading the receipt…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // A suggestion, never an automatic fill: a total read off a photograph is a guess, and
            // the line it came from is shown so it can be judged at a glance.
            reading?.totalMinor?.let { total ->
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Accent.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        "Found ${Money.format(total, currencyCode)} on this receipt",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    reading.totalSourceLine?.let { line ->
                        Text(
                            "from \"${line.take(40)}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismissReading) { Text("Ignore") }
                        TextButton(onClick = onAcceptReading) { Text("Use it") }
                    }
                }
            }
        } else if (attaching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Saving image…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttachChip(
                    label = "Photo",
                    icon = Icons.Filled.PhotoCamera,
                    onClick = {
                        val (file, uri) = onPickTarget()
                        pendingCapture = file
                        cameraLauncher.launch(uri)
                    }
                )
                AttachChip(
                    label = "File",
                    icon = Icons.Filled.PhotoLibrary,
                    onClick = { fileLauncher.launch(arrayOf("image/*")) }
                )
            }
        }
    }

    if (viewing && attachmentName != null) {
        ReceiptViewer(
            attachmentName = attachmentName,
            uriFor = uriFor,
            onDismiss = { viewing = false }
        )
    }
}

@Composable
private fun AttachChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ReceiptViewer(
    attachmentName: String,
    uriFor: (String) -> Uri?,
    onDismiss: () -> Unit
) {
    val bitmap = rememberReceiptThumbnail(attachmentName, uriFor, full = true)
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Receipt",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    "That image is missing.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
    }
}

/**
 * Decodes the stored receipt off the composition's own thread and caches it against the file name,
 * so scrolling past a row does not re-read the file every frame.
 */
@Composable
private fun rememberReceiptThumbnail(
    attachmentName: String,
    uriFor: (String) -> Uri?,
    full: Boolean = false
): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(attachmentName, full) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachmentName, full) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val uri = uriFor(attachmentName) ?: return@runCatching null
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = android.graphics.BitmapFactory.Options().apply {
                        // The thumbnail never needs full resolution; the viewer does.
                        inSampleSize = if (full) 1 else 4
                    }
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                        ?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}
