package com.financetracker.app.ui.categories

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryGroup
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.ui.accounts.PickerRow
import com.financetracker.app.ui.common.CategoryDot
import com.financetracker.app.ui.common.ColorPickerRow
import com.financetracker.app.ui.common.ConfirmDialog
import com.financetracker.app.ui.common.OptionPickerDialog
import com.financetracker.app.ui.common.PALETTE
import com.financetracker.app.ui.common.SectionCard
import com.financetracker.app.ui.settings.ManageViewModel
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.Bg
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Surface2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: ManageViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    val groups = if (kind == CategoryKind.EXPENSE) state.expenseGroups else state.incomeGroups

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
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
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            KindToggle(kind) { kind = it }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (group in groups) {
                    item(key = "group-${group.parent.id}") {
                        CategoryGroupCard(
                            group = group,
                            onEdit = { editing = it },
                            onDelete = { deleting = it },
                            onArchive = { viewModel.setCategoryArchived(it, !it.isArchived) }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (creating) {
        CategoryEditorDialog(
            existing = null,
            kind = kind,
            parents = groups.map { it.parent },
            onSave = { name, parentId, color ->
                viewModel.saveCategory(null, name, kind, parentId, color)
                creating = false
            },
            onDismiss = { creating = false }
        )
    }

    editing?.let { category ->
        CategoryEditorDialog(
            existing = category,
            kind = category.kind,
            // A category cannot be nested under itself, and this app keeps the tree exactly two
            // levels deep, so an existing parent has no parent options of its own.
            parents = groups.map { it.parent }.filter { it.id != category.id },
            onSave = { name, parentId, color ->
                viewModel.saveCategory(category, name, category.kind, parentId, color)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { category ->
        ConfirmDialog(
            title = "Delete ${category.name}?",
            body = "Transactions filed under it stay, but become uncategorised. Any " +
                "subcategories move up to the top level.",
            onConfirm = { viewModel.deleteCategory(category); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun KindToggle(selected: CategoryKind, onSelect: (CategoryKind) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CategoryKind.entries.forEach { kind ->
            val isSelected = kind == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) Accent.copy(alpha = 0.20f) else Color.Transparent)
                    .clickable { onSelect(kind) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (kind == CategoryKind.EXPENSE) "Spending" else "Income",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryGroupCard(
    group: CategoryGroup,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onArchive: (Category) -> Unit
) {
    SectionCard {
        CategoryLine(
            category = group.parent,
            indented = false,
            onEdit = onEdit,
            onDelete = onDelete,
            onArchive = onArchive
        )
        group.children.forEach { child ->
            CategoryLine(
                category = child,
                indented = true,
                onEdit = onEdit,
                onDelete = onDelete,
                onArchive = onArchive
            )
        }
    }
}

@Composable
private fun CategoryLine(
    category: Category,
    indented: Boolean,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onArchive: (Category) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = if (indented) 20.dp else 0.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryDot(category.colorArgb, size = if (indented) 8 else 11)
            Spacer(Modifier.width(12.dp))
            Text(
                category.name,
                style = if (indented) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.titleSmall,
                color = if (category.isArchived) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (category.isArchived) {
                Text(
                    "archived",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (indented) 20.dp else 0.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onEdit(category) }) { Text("Edit") }
                TextButton(onClick = { onArchive(category) }) {
                    Text(if (category.isArchived) "Unarchive" else "Archive")
                }
                TextButton(onClick = { onDelete(category) }) {
                    Text("Delete", color = Negative)
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    existing: Category?,
    kind: CategoryKind,
    parents: List<Category>,
    onSave: (String, Long?, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var parentId by remember { mutableStateOf(existing?.parentId) }
    var color by remember { mutableStateOf(existing?.colorArgb ?: PALETTE.first()) }
    var showParentPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) {
                    if (kind == CategoryKind.EXPENSE) "New spending category" else "New income category"
                } else "Edit category"
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PickerRow(
                    label = "Parent",
                    value = parents.firstOrNull { it.id == parentId }?.name ?: "None (top level)"
                ) { showParentPicker = true }

                Spacer(Modifier.height(6.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                ColorPickerRow(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, parentId, color) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showParentPicker) {
        val options = remember(parents) { listOf<Category?>(null) + parents }
        OptionPickerDialog(
            title = "Parent category",
            options = options,
            selected = options.firstOrNull { it?.id == parentId },
            label = { it?.name ?: "None (top level)" },
            leadingColor = { it?.colorArgb },
            onSelect = { parentId = it?.id; showParentPicker = false },
            onDismiss = { showParentPicker = false }
        )
    }
}
