package com.financetracker.app.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.importer.DateStyle
import com.financetracker.app.data.importer.ImportField
import com.financetracker.app.data.importer.ParsedRow
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.common.shortDate
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface2
import com.financetracker.app.ui.theme.Warn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(viewModel: ImportViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.load(it, it.lastPathSegment?.substringAfterLast('/')) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import CSV") },
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
            when (state.stage) {
                ImportStage.PICK -> item {
                    SectionCard {
                        Text(
                            "Bring in transactions from a spreadsheet or a bank export.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Any CSV works - you choose which columns mean what on the next " +
                                "screen, and nothing is written until you have seen a preview.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose a file")
                        }
                    }
                }

                ImportStage.MAP -> {
                    item { MappingCard(state, viewModel) }
                    item { OptionsCard(state, viewModel) }
                    state.preview?.let { preview ->
                        item { PreviewCard(preview, state) }
                    }
                    item {
                        Button(
                            onClick = viewModel::commit,
                            enabled = state.canImport && !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                state.preview?.usable?.size?.let { "Import $it transactions" }
                                    ?: "Import"
                            )
                        }
                    }
                }

                ImportStage.DONE -> item { ResultCard(state, viewModel, onBack) }
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

            state.error?.let { message ->
                item {
                    SectionCard {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = Negative)
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun MappingCard(state: ImportUiState, viewModel: ImportViewModel) {
    var pickingFor by remember { mutableStateOf<ImportField?>(null) }
    var pickingDateStyle by remember { mutableStateOf(false) }

    SectionCard(title = state.fileName ?: "Columns") {
        Text(
            "Match the file's columns to what they mean.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        ImportField.entries.forEach { field ->
            val columnIndex = state.mapping[field]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { pickingFor = field }
                    .padding(vertical = 11.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.width(110.dp)) {
                    Text(field.label, style = MaterialTheme.typography.bodyMedium)
                    if (field.required) {
                        Text(
                            "required",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (columnIndex == null) Negative
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    columnIndex?.let { state.header.getOrNull(it) ?: "Column ${it + 1}" } ?: "Not used",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (columnIndex == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { pickingDateStyle = true }
                .padding(vertical = 11.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Date format",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(110.dp)
            )
            Text(state.dateStyle.label, style = MaterialTheme.typography.bodyLarge)
        }

        // A file where nothing exceeds the 12th genuinely cannot be read either way. Saying so
        // beats silently picking one and being wrong about every date in it.
        if (state.dateStyleWasAmbiguous) {
            Text(
                "Every date in this file could be read either way round. Check a row you " +
                    "recognise in the preview below before importing.",
                style = MaterialTheme.typography.labelSmall,
                color = Warn
            )
        }
    }

    pickingFor?.let { field ->
        val options = remember(state.header) { (-1 until state.header.size).toList() }
        OptionPickerDialog(
            title = field.label,
            options = options,
            selected = state.mapping[field] ?: -1,
            label = { index ->
                if (index < 0) "Not used"
                else state.header.getOrNull(index)?.ifBlank { "Column ${index + 1}" }
                    ?: "Column ${index + 1}"
            },
            supporting = { index ->
                if (index < 0) null
                else state.csv?.rows?.firstOrNull()?.getOrNull(index)?.takeIf { it.isNotBlank() }
            },
            onSelect = {
                viewModel.setColumn(field, it.takeIf { value -> value >= 0 })
                pickingFor = null
            },
            onDismiss = { pickingFor = null }
        )
    }

    if (pickingDateStyle) {
        OptionPickerDialog(
            title = "Date format",
            options = DateStyle.entries,
            selected = state.dateStyle,
            label = { it.label },
            onSelect = { viewModel.setDateStyle(it); pickingDateStyle = false },
            onDismiss = { pickingDateStyle = false }
        )
    }
}

@Composable
private fun OptionsCard(state: ImportUiState, viewModel: ImportViewModel) {
    var pickingAccount by remember { mutableStateOf(false) }
    val account = state.accounts.firstOrNull { it.id == state.defaultAccountId }

    SectionCard(title = "Options") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { pickingAccount = true }
                .padding(vertical = 11.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Import into", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Used for rows without an account of their own",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(account?.name ?: "Pick one", style = MaterialTheme.typography.bodyLarge)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.createMissing, onCheckedChange = viewModel::setCreateMissing)
            Column {
                Text("Create missing accounts and categories", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Off means unknown names fall back to the account above, uncategorised",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.skipDuplicates, onCheckedChange = viewModel::setSkipDuplicates)
            Column {
                Text("Skip duplicates", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Matches an existing entry on the same day, amount, account and payee",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (pickingAccount) {
        OptionPickerDialog(
            title = "Import into",
            options = state.accounts,
            selected = account,
            label = { it.name },
            supporting = { "${it.type.label} · ${it.currencyCode}" },
            leadingColor = { it.colorArgb },
            onSelect = { viewModel.setDefaultAccount(it.id); pickingAccount = false },
            onDismiss = { pickingAccount = false }
        )
    }
}

@Composable
private fun PreviewCard(
    preview: com.financetracker.app.data.importer.ImportPreview,
    state: ImportUiState
) {
    val currency = state.accounts.firstOrNull { it.id == state.defaultAccountId }?.currencyCode ?: "EUR"

    SectionCard(title = "Preview") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "${preview.usable.size} ready",
                style = MaterialTheme.typography.bodyMedium,
                color = Positive
            )
            if (preview.skipped.isNotEmpty()) {
                Text(
                    "${preview.skipped.size} skipped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warn
                )
            }
        }

        if (preview.newAccounts.isNotEmpty() || preview.newCategories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    if (preview.newAccounts.isNotEmpty()) {
                        append("New accounts: ${preview.newAccounts.joinToString(", ")}")
                    }
                    if (preview.newCategories.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append("New categories: ${preview.newCategories.take(8).joinToString(", ")}")
                        if (preview.newCategories.size > 8) {
                            append(" and ${preview.newCategories.size - 8} more")
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        preview.usable.take(5).forEach { row -> PreviewRow(row, currency) }

        if (preview.skipped.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Skipped rows",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            preview.skipped.take(4).forEach { skip ->
                Text(
                    "Line ${skip.lineNumber}: ${skip.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warn
                )
            }
            if (preview.skipped.size > 4) {
                Text(
                    "and ${preview.skipped.size - 4} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewRow(row: ParsedRow.Usable, fallbackCurrency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.payee.ifBlank { row.categoryName ?: "(no payee)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    shortDate(row.dateMillis),
                    row.categoryName,
                    row.accountName
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            (if (row.type == TxnType.EXPENSE) "-" else "+") +
                Money.format(row.amountMinor, row.currencyCode ?: fallbackCurrency),
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.type == TxnType.EXPENSE) Negative else Positive
        )
    }
}

/** "1 row" / "2 rows"; irregular plurals are passed in explicitly. */
private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
    "$count ${if (count == 1) singular else plural}"

@Composable
private fun ResultCard(state: ImportUiState, viewModel: ImportViewModel, onBack: () -> Unit) {
    val result = state.result ?: return
    SectionCard {
        val addedNothing = result.imported == 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                // A green tick over "Imported 0" reads like something went wrong. Nothing did -
                // it just had nothing new to add - so the tone drops to neutral.
                tint = if (addedNothing) MaterialTheme.colorScheme.onSurfaceVariant else Positive
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (addedNothing) "Nothing new to add"
                else "Imported ${plural(result.imported, "transaction")}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(10.dp))

        listOfNotNull(
            result.duplicatesSkipped.takeIf { it > 0 }
                ?.let { "${plural(it, "entry", "entries")} already in the app, skipped" },
            result.rowsSkipped.takeIf { it > 0 }
                ?.let { "${plural(it, "row")} could not be read" },
            result.accountsCreated.takeIf { it > 0 }
                ?.let { "${plural(it, "new account")} created" },
            result.categoriesCreated.takeIf { it > 0 }
                ?.let { "${plural(it, "new category", "new categories")} created" }
        ).forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface2)
                    .clickable { viewModel.reset() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Import another", style = MaterialTheme.typography.labelLarge)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent)
                    .clickable { onBack() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Done",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
