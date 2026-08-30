package com.financetracker.app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.tag.Tag
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.BorderColor
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface1
import com.financetracker.app.ui.theme.Surface2
import com.financetracker.app.ui.theme.Warn

/**
 * The split editor: one row per leg, plus a running remainder so it is always obvious how much of
 * the payment is still unassigned. The remainder is the whole point of the section - a split that
 * silently did not add up would put the reports and the balances out of step with each other.
 */
@Composable
fun SplitSection(
    state: EntryUiState,
    onAddLeg: () -> Unit,
    onRemoveLeg: (Int) -> Unit,
    onSetCategory: (Int, Long) -> Unit,
    onSetAmount: (Int, Long) -> Unit,
    onAbsorbRemainder: (Int) -> Unit,
    onCancelSplit: () -> Unit
) {
    var pickerForIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CallSplit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Split across categories",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancelSplit) { Text("Undo split") }
        }

        Spacer(Modifier.height(4.dp))

        state.splits.forEachIndexed { index, leg ->
            SplitLegRow(
                index = index,
                categoryName = leg.categoryId?.let { state.categoriesById[it]?.name },
                categoryColor = leg.categoryId?.let { state.categoriesById[it]?.colorArgb },
                amountMinor = leg.amountMinor,
                currencyCode = state.currencyCode,
                canRemove = state.splits.size > 2,
                onPickCategory = { pickerForIndex = index },
                onAmountChange = { onSetAmount(index, it) },
                onRemove = { onRemoveLeg(index) }
            )
        }

        Spacer(Modifier.height(8.dp))
        RemainderRow(
            state = state,
            onAddLeg = onAddLeg,
            onAbsorb = { onAbsorbRemainder(state.splits.lastIndex) }
        )
    }

    pickerForIndex?.let { index ->
        val flattened = remember(state.groups) {
            state.groups.flatMap { group -> listOf(group.parent) + group.children }
        }
        OptionPickerDialog(
            title = "Category for this part",
            options = flattened,
            selected = state.splits.getOrNull(index)?.categoryId?.let { state.categoriesById[it] },
            label = { category: Category ->
                if (category.parentId == null) category.name else "    ${category.name}"
            },
            leadingColor = { it.colorArgb },
            onSelect = { onSetCategory(index, it.id); pickerForIndex = null },
            onDismiss = { pickerForIndex = null }
        )
    }
}

@Composable
private fun SplitLegRow(
    index: Int,
    categoryName: String?,
    categoryColor: Int?,
    amountMinor: Long,
    currencyCode: String,
    canRemove: Boolean,
    onPickCategory: () -> Unit,
    onAmountChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    // The field owns its own text. Keying `remember` on the amount instead would reset the string
    // on every keystroke - typing changes the amount, which changes the key - making the field
    // impossible to clear and jumping the cursor. Instead it re-syncs only when the value changed
    // from outside, which is exactly the "Fix last" case.
    var text by remember(index) { mutableStateOf(Money.editString(amountMinor, currencyCode)) }
    LaunchedEffect(amountMinor) {
        if (Money.parseToMinor(text, currencyCode) != amountMinor) {
            text = Money.editString(amountMinor, currencyCode)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onPickCategory() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryDot(categoryColor, size = 9)
            Spacer(Modifier.width(8.dp))
            Text(
                categoryName ?: "Pick a category",
                style = MaterialTheme.typography.bodyMedium,
                color = if (categoryName == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                Money.parseToMinor(it, currencyCode)?.let(onAmountChange)
            },
            singleLine = true,
            modifier = Modifier.width(110.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        if (canRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove this part",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Spacer(Modifier.width(36.dp))
        }
    }
}

@Composable
private fun RemainderRow(state: EntryUiState, onAddLeg: () -> Unit, onAbsorb: () -> Unit) {
    val remainder = state.splitRemainderMinor
    val canAbsorb = (state.splits.lastOrNull()?.amountMinor ?: 0L) + remainder >= 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when {
                    remainder == 0L -> "Adds up to ${Money.format(state.amountMinor, state.currencyCode)}"
                    remainder > 0L -> "${Money.format(remainder, state.currencyCode)} still unassigned"
                    else -> "${Money.format(-remainder, state.currencyCode)} over the total"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    remainder == 0L -> Positive
                    remainder > 0L -> Warn
                    else -> Negative
                }
            )
        }
        // Offering "Fix last" when the correction would drive the leg negative would be a button
        // that visibly does nothing, so it is only shown when it can actually resolve things.
        if (remainder != 0L && canAbsorb) {
            TextButton(onClick = onAbsorb) { Text("Fix last") }
        }
        TextButton(onClick = onAddLeg) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Part")
        }
    }
}

/** Horizontal chip row for tags, with an inline "new tag" affordance at the end. */
@Composable
fun TagSection(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allTags.forEach { tag ->
                TagChip(
                    label = "#${tag.name}",
                    color = Color(tag.colorArgb),
                    selected = tag.id in selectedTagIds,
                    onClick = { onToggle(tag.id) }
                )
            }
            TagChip(
                label = "+ New",
                color = Accent,
                selected = false,
                onClick = { creating = true }
            )
        }
    }

    if (creating) {
        NewTagDialog(
            onConfirm = { onCreate(it); creating = false },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun TagChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(alpha = 0.24f) else Surface2)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) color else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewTagDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag") },
        text = {
            Column {
                Text(
                    "Tags cut across categories - one holiday's spending, or everything to claim back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("lisbon-2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
