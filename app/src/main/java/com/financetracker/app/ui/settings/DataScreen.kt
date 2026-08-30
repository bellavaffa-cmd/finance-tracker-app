package com.financetracker.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.financetracker.app.data.backup.BackupRepository
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.security.AppLock
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(viewModel: DataViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val lockEnabled by viewModel.appLockEnabled.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lockAvailable = remember { AppLock.isAvailable(context) }

    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    // CreateDocument hands back a Uri the user picked, so the file lands somewhere they control and
    // survives uninstalling the app - which is the entire point of a backup.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.BACKUP_MIME)
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.CSV_MIME)
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestore = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & security") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "Back up") {
                    Text(
                        "Your data lives only on this device. If the phone is lost or the app is " +
                            "uninstalled, it goes with it — so take a backup somewhere else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        label = "Save a backup",
                        supporting = "Everything, in one JSON file you can restore from",
                        icon = Icons.Filled.Save,
                        enabled = !state.busy
                    ) { backupLauncher.launch(BackupRepository.backupFileName()) }

                    ActionRow(
                        label = "Export to CSV",
                        supporting = "Transactions for a spreadsheet. Not restorable — use a backup for that",
                        icon = Icons.Filled.Description,
                        enabled = !state.busy
                    ) { csvLauncher.launch(BackupRepository.csvFileName()) }
                }
            }

            item {
                SectionCard(title = "Restore") {
                    Text(
                        "Restoring replaces everything currently in the app with the contents of " +
                            "the backup file. It does not merge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        label = "Restore from a backup",
                        supporting = "Pick a .json file saved by this app",
                        icon = Icons.Filled.Restore,
                        enabled = !state.busy
                    ) {
                        // Some file pickers do not tag .json correctly, so */* is used and the
                        // file's own contents decide whether it is a valid backup.
                        restoreLauncher.launch(arrayOf("*/*"))
                    }
                }
            }

            item {
                SectionCard(title = "Security") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require unlock", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (lockAvailable) {
                                    "Fingerprint, face or device PIN each time the app opens"
                                } else {
                                    "Set a screen lock on this device first"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lockEnabled,
                            enabled = lockAvailable && activity != null,
                            onCheckedChange = { wanted ->
                                if (!wanted) {
                                    // Turning the lock off must itself be authenticated, or anyone
                                    // holding the unlocked phone could simply switch it off.
                                    activity?.let {
                                        AppLock.prompt(
                                            activity = it,
                                            title = "Turn off the lock",
                                            onSuccess = { viewModel.setAppLockEnabled(false) }
                                        )
                                    }
                                } else {
                                    activity?.let {
                                        AppLock.prompt(
                                            activity = it,
                                            title = "Confirm it's you",
                                            onSuccess = { viewModel.setAppLockEnabled(true) }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Working…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.message?.let { message ->
                item {
                    SectionCard {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isError) Negative else Positive
                        )
                    }
                }
            }
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = "Replace everything?",
            body = "Every account, category, transaction, budget and recurring rule in the app " +
                "will be deleted and replaced with the contents of this file. This cannot be " +
                "undone — save a backup first if you are unsure.",
            confirmLabel = "Replace",
            onConfirm = {
                viewModel.restore(uri)
                pendingRestore = null
            },
            onDismiss = { pendingRestore = null }
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    supporting: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
