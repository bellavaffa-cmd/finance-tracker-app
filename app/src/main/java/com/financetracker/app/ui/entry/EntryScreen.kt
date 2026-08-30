package com.financetracker.app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financetracker.app.data.Money
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.shortDate
import com.financetracker.app.ui.common.withTimeFrom
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.BorderColor
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface1
import com.financetracker.app.ui.theme.Surface2

/**
 * The fast-entry screen. Amount is typed on a keypad in minor units (tapping 1-2-3-4 gives 12.34),
 * which removes the decimal point from the interaction entirely and is the shortest path from
 * "I just paid for something" to a saved row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    editingId: Long?,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showToAccountPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFxEditor by remember { mutableStateOf(false) }

    LaunchedEffect(editingId, state.loading) {
        if (editingId != null && !state.loading && state.editingId == null) {
            viewModel.startEditing(editingId)
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit transaction" else "New transaction") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete",
                                tint = Negative
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            TypeSelector(selected = state.type, onSelect = viewModel::setType)
            Spacer(Modifier.height(16.dp))

            AmountDisplay(state = state)
            Spacer(Modifier.height(16.dp))

            FieldRow(
                label = if (state.type == TxnType.INCOME) "To account" else "From account",
                value = state.account?.name ?: "Pick an account",
                colorArgb = state.account?.colorArgb,
                onClick = { showAccountPicker = true }
            )

            if (state.type == TxnType.TRANSFER) {
                FieldRow(
                    label = "To account",
                    value = state.toAccount?.name ?: "Pick an account",
                    colorArgb = state.toAccount?.colorArgb,
                    onClick = { showToAccountPicker = true }
                )
                if (state.needsToAmount) {
                    ToAmountField(
                        value = state.toAmountMinor,
                        currencyCode = state.toCurrencyCode,
                        onChange = viewModel::setToAmountMinor
                    )
                }
            } else if (state.isSplit) {
                SplitSection(
                    state = state,
                    onAddLeg = viewModel::addSplitLeg,
                    onRemoveLeg = viewModel::removeSplitLeg,
                    onSetCategory = viewModel::setSplitCategory,
                    onSetAmount = viewModel::setSplitAmount,
                    onAbsorbRemainder = viewModel::absorbRemainder,
                    onCancelSplit = viewModel::cancelSplit
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldRow(
                        label = "Category",
                        value = state.category?.name ?: "Pick a category",
                        colorArgb = state.category?.colorArgb,
                        onClick = { showCategoryPicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::startSplit, enabled = state.amountMinor > 0) {
                        Icon(
                            Icons.Filled.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.width(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Split")
                    }
                }
            }

            FieldRow(
                label = "Date",
                value = shortDate(state.dateMillis),
                onClick = { showDatePicker = true }
            )

            if (state.needsFxRate) {
                FieldRow(
                    label = "Rate to ${state.baseCurrency}",
                    value = "1 ${state.currencyCode} = ${"%.4f".format(state.fxRateToBase)} ${state.baseCurrency}",
                    onClick = { showFxEditor = true }
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.payee,
                onValueChange = viewModel::setPayee,
                label = { Text("Payee") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.payeeSuggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.payeeSuggestions.take(3).forEach { suggestion ->
                        SuggestionChip(suggestion) { viewModel.applyPayee(suggestion) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            ReceiptSection(
                attachmentName = state.attachmentName,
                attaching = state.attaching,
                uriFor = viewModel::attachmentUri,
                onPickTarget = viewModel::cameraTarget,
                onCaptured = viewModel::attachCapture,
                onPicked = viewModel::attachFrom,
                onRemove = viewModel::removeAttachment
            )

            Spacer(Modifier.height(16.dp))
            TagSection(
                allTags = state.allTags,
                selectedTagIds = state.selectedTagIds,
                onToggle = viewModel::toggleTag,
                onCreate = viewModel::createAndApplyTag
            )

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Negative, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            Keypad(
                onDigit = viewModel::appendDigit,
                onBackspace = viewModel::backspace,
                onClear = viewModel::clearAmount,
                onSave = viewModel::save
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAccountPicker) {
        OptionPickerDialog(
            title = "Account",
            options = state.accounts,
            selected = state.account,
            label = { it.name },
            supporting = { "${it.type.label} · ${Money.format(it.balanceMinor, it.currencyCode)}" },
            leadingColor = { it.colorArgb },
            onSelect = { viewModel.setAccount(it.id); showAccountPicker = false },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showToAccountPicker) {
        OptionPickerDialog(
            title = "Transfer to",
            options = state.accounts.filter { it.id != state.accountId },
            selected = state.toAccount,
            label = { it.name },
            supporting = { "${it.type.label} · ${Money.format(it.balanceMinor, it.currencyCode)}" },
            leadingColor = { it.colorArgb },
            onSelect = { viewModel.setToAccount(it.id); showToAccountPicker = false },
            onDismiss = { showToAccountPicker = false }
        )
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            state = state,
            onSelect = { viewModel.setCategory(it); showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showDatePicker) {
        DatePickerSheet(
            initialMillis = state.dateMillis,
            onPick = { viewModel.setDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showFxEditor) {
        FxRateDialog(
            currencyCode = state.currencyCode,
            baseCurrency = state.baseCurrency,
            initial = state.fxRateToBase,
            onConfirm = { viewModel.setFxRate(it); showFxEditor = false },
            onDismiss = { showFxEditor = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this transaction?",
            body = "It is removed from all balances and reports. This cannot be undone from here.",
            onConfirm = { showDeleteConfirm = false; viewModel.delete() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun TypeSelector(selected: TxnType, onSelect: (TxnType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TxnType.entries.forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) typeColor(type).copy(alpha = 0.20f) else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) typeColor(type) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun typeColor(type: TxnType): Color = when (type) {
    TxnType.EXPENSE -> Negative
    TxnType.INCOME -> Positive
    TxnType.TRANSFER -> Accent
}

@Composable
private fun AmountDisplay(state: EntryUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            Money.format(state.amountMinor, state.currencyCode),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = typeColor(state.type),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Showing the base-currency equivalent live is what makes a foreign-currency account
        // usable: you can sanity-check the number without leaving the screen.
        if (state.needsFxRate && state.amountMinor > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "≈ ${Money.format(
                    Money.toBaseMinor(state.amountMinor, state.currencyCode, state.fxRateToBase, state.baseCurrency),
                    state.baseCurrency
                )}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    colorArgb: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        if (colorArgb != null) {
            CategoryDot(colorArgb, size = 9)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToAmountField(value: Long, currencyCode: String, onChange: (Long) -> Unit) {
    // Same reasoning as the split legs: keying on `value` would reset the text on every keystroke.
    var text by remember { mutableStateOf(Money.editString(value, currencyCode)) }
    LaunchedEffect(value) {
        if (Money.parseToMinor(text, currencyCode) != value) {
            text = Money.editString(value, currencyCode)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            Money.parseToMinor(it, currencyCode)?.let(onChange)
        },
        label = { Text("Arrives as ($currencyCode)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Keypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "<")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "C" -> onClear()
                                "<" -> onBackspace()
                                else -> onDigit(key.toInt())
                            }
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Accent)
                .clickable { onSave() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Save",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "<" -> Icon(Icons.Filled.Backspace, contentDescription = "Backspace")
            else -> Text(
                key,
                style = MaterialTheme.typography.titleLarge,
                color = if (key == "C") MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Grouped picker: parents are selectable in their own right, children indented beneath them. */
@Composable
private fun CategoryPickerDialog(
    state: EntryUiState,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val flattened = remember(state.groups) {
        state.groups.flatMap { group -> listOf(group.parent) + group.children }
    }
    OptionPickerDialog(
        title = "Category",
        options = flattened,
        selected = state.category,
        label = { category: Category ->
            if (category.parentId == null) category.name else "    ${category.name}"
        },
        supporting = { category: Category ->
            if (category.parentId == null) null
            else state.categoriesById[category.parentId]?.name
        },
        leadingColor = { it.colorArgb },
        onSelect = { onSelect(it.id) },
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initialMillis: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = pickerState.selectedDateMillis
                if (selected != null) {
                    // The picker returns UTC midnight; re-anchor it to the local date and keep the
                    // original time of day so editing a date does not silently move an entry a day.
                    val localDate = java.time.Instant.ofEpochMilli(selected)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onPick(localDate.withTimeFrom(initialMillis))
                } else {
                    onDismiss()
                }
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        androidx.compose.material3.DatePicker(state = pickerState)
    }
}

@Composable
private fun FxRateDialog(
    currencyCode: String,
    baseCurrency: String,
    initial: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial.toString()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exchange rate") },
        text = {
            Column {
                Text(
                    "How many $baseCurrency one $currencyCode is worth. This rate is stored on this transaction and never changes afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("1 $currencyCode =") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                text.trim().replace(',', '.').toDoubleOrNull()
                    ?.takeIf { it > 0 }
                    ?.let(onConfirm) ?: onDismiss()
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
