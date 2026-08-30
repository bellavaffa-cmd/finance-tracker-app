package com.financetracker.app.ui.rules

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.rules.MatchType
import com.financetracker.app.data.rules.PayeeRule
import com.financetracker.app.data.rules.RuleSuggestion
import com.financetracker.app.ui.accounts.PickerRow
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.EmptyState
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Bg
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive

/** "1 entry" / "2 entries"; irregular plurals are passed in explicitly. */
private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
    "$count ${if (count == 1) singular else plural}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: RulesViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PayeeRule?>(null) }
    var deleting by remember { mutableStateOf<PayeeRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-categorise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = Accent,
                contentColor = Bg
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add rule")
            }
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
                SectionCard {
                    Text(
                        "Rules fill in the category when a payee matches - as you type one in, and " +
                            "on every row of a CSV import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.rules.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = viewModel::runBackfill,
                            enabled = !state.busy
                        ) {
                            Icon(
                                Icons.Filled.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Apply to existing uncategorised entries")
                        }
                    }
                    state.backfill?.let { result ->
                        Text(
                            if (result.categorised == 0) {
                                "Nothing matched among ${plural(result.examined, "uncategorised entry", "uncategorised entries")}."
                            } else {
                                "Categorised ${result.categorised} of " +
                                    plural(result.examined, "uncategorised entry", "uncategorised entries") + "."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.categorised > 0) Positive
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                item {
                    SectionCard(title = "Suggested from your history") {
                        Text(
                            "Payees you have filed the same way several times already.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        state.suggestions.take(5).forEach { suggestion ->
                            SuggestionRow(
                                suggestion = suggestion,
                                onAccept = { viewModel.acceptSuggestion(suggestion) },
                                onDismiss = { viewModel.dismissSuggestion(suggestion) }
                            )
                        }
                    }
                }
            }

            if (state.rules.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        "No rules yet",
                        "Add one, or accept a suggestion above if you have some history already."
                    )
                }
            }

            for ((index, rule) in state.rules.withIndex()) {
                item(key = rule.id) {
                    RuleCard(
                        rule = rule,
                        state = state,
                        isFirst = index == 0,
                        isLast = index == state.rules.lastIndex,
                        onEdit = { editing = rule },
                        onToggle = { viewModel.setActive(rule, !rule.isActive) },
                        onMove = { up -> viewModel.move(rule, up) },
                        onDelete = { deleting = rule }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (creating || editing != null) {
        RuleEditorDialog(
            existing = editing,
            state = state,
            onSave = { pattern, matchType, categoryId, accountId, renameTo ->
                viewModel.save(editing, pattern, matchType, categoryId, accountId, renameTo)
                creating = false
                editing = null
            },
            onDismiss = { creating = false; editing = null }
        )
    }

    deleting?.let { rule ->
        ConfirmDialog(
            title = "Delete this rule?",
            body = "Transactions it already categorised keep their category.",
            onConfirm = { viewModel.delete(rule); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun SuggestionRow(
    suggestion: RuleSuggestion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                suggestion.payee,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${suggestion.categoryName} · ${suggestion.occurrences} times",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onDismiss) { Text("No") }
        TextButton(onClick = onAccept) { Text("Add") }
    }
}

@Composable
private fun RuleCard(
    rule: PayeeRule,
    state: RulesUiState,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onMove: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    SectionCard(modifier = Modifier.clickable { onEdit() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${rule.matchType.label} \"${rule.pattern}\"",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.categoryName(rule.categoryId)?.let { name ->
                        CategoryDot(
                            state.categories.firstOrNull { it.id == rule.categoryId }?.colorArgb,
                            size = 8
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                listOfNotNull(
                    rule.renameTo?.let { "renames to \"$it\"" },
                    state.accountName(rule.accountId)?.let { "files to $it" }
                ).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = rule.isActive, onCheckedChange = { onToggle() })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Order matters because the first match wins, so it has to be adjustable.
            IconButton(onClick = { onMove(true) }, enabled = !isFirst) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = "Check earlier",
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = { onMove(false) }, enabled = !isLast) {
                Icon(
                    Icons.Filled.ArrowDownward,
                    contentDescription = "Check later",
                    modifier = Modifier.size(16.dp)
                )
            }
            TextButton(onClick = onDelete) { Text("Delete", color = Negative) }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    existing: PayeeRule?,
    state: RulesUiState,
    onSave: (String, MatchType, Long?, Long?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var pattern by remember { mutableStateOf(existing?.pattern.orEmpty()) }
    var matchType by remember { mutableStateOf(existing?.matchType ?: MatchType.CONTAINS) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var accountId by remember { mutableStateOf(existing?.accountId) }
    var renameTo by remember { mutableStateOf(existing?.renameTo.orEmpty()) }
    var showMatchPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New rule" else "Edit rule") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("When the payee...") },
                    placeholder = { Text("lidl") },
                    supportingText = { Text(matchType.hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                PickerRow("Match", matchType.label) { showMatchPicker = true }
                PickerRow(
                    "Category",
                    state.categoryName(categoryId) ?: "Leave alone"
                ) { showCategoryPicker = true }
                PickerRow(
                    "Account",
                    state.accountName(accountId) ?: "Leave alone"
                ) { showAccountPicker = true }

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = renameTo,
                    onValueChange = { renameTo = it },
                    label = { Text("Rename payee to (optional)") },
                    placeholder = { Text("Lidl") },
                    supportingText = {
                        Text("Tidies messy bank descriptions into one consistent name")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pattern, matchType, categoryId, accountId, renameTo) },
                // A rule that changes nothing would still match, shadowing every rule below it.
                enabled = pattern.isNotBlank() &&
                    (categoryId != null || accountId != null || renameTo.isNotBlank())
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showMatchPicker) {
        OptionPickerDialog(
            title = "Match",
            options = MatchType.entries,
            selected = matchType,
            label = { it.label },
            supporting = { it.hint },
            onSelect = { matchType = it; showMatchPicker = false },
            onDismiss = { showMatchPicker = false }
        )
    }

    if (showCategoryPicker) {
        val options = remember(state.categories) { listOf<Category?>(null) + state.categories }
        OptionPickerDialog(
            title = "Category",
            options = options,
            selected = options.firstOrNull { it?.id == categoryId },
            label = { it?.name ?: "Leave alone" },
            leadingColor = { it?.colorArgb },
            onSelect = { categoryId = it?.id; showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showAccountPicker) {
        val options = remember(state.accounts) {
            listOf<com.financetracker.app.data.account.AccountWithBalance?>(null) + state.accounts
        }
        OptionPickerDialog(
            title = "Account",
            options = options,
            selected = options.firstOrNull { it?.id == accountId },
            label = { it?.name ?: "Leave alone" },
            leadingColor = { it?.colorArgb },
            onSelect = { accountId = it?.id; showAccountPicker = false },
            onDismiss = { showAccountPicker = false }
        )
    }
}
