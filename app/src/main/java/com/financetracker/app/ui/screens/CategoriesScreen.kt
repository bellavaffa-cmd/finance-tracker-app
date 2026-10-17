package com.financetracker.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Category
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.domain.Defaults
import com.financetracker.app.ui.ColorDot
import com.financetracker.app.ui.LedgerState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(state: LedgerState, onSave: (Category) -> Unit, onDelete: (Long) -> Unit, onBack: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val shown = state.categories.filter { it.kind == kind }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Category(name = "", kind = kind, color = Defaults.colorFor(state.categories.size)) }) {
                Icon(Icons.Default.Add, "Add category")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = kind.ordinal) {
                Tab(kind == CategoryKind.EXPENSE, { kind = CategoryKind.EXPENSE }, text = { Text("Spending") })
                Tab(kind == CategoryKind.INCOME, { kind = CategoryKind.INCOME }, text = { Text("Income") })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(shown, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { editing = c }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(Color(c.color), 14)
                        Spacer(Modifier.width(14.dp))
                        Text(c.name)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { category ->
        val isNew = category.id == 0L
        var name by remember(category) { mutableStateOf(category.name) }
        var color by remember(category) { mutableStateOf(category.color) }
        var problem by remember(category) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isNew) "New category" else "Edit category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it; problem = null }, label = { Text("Name") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Defaults.palette.forEach { c ->
                            Box(
                                Modifier.size(32.dp)
                                    .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .padding(4.dp)
                                    .clickable { color = c },
                                contentAlignment = Alignment.Center,
                            ) { ColorDot(Color(c), 24) }
                        }
                    }
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!isNew) TextButton(onClick = { deleting = category; editing = null }) { Text("Delete category") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clash = state.categories.any {
                        it.id != category.id && it.kind == category.kind && it.name.equals(name.trim(), ignoreCase = true)
                    }
                    problem = when {
                        name.isBlank() -> "Give the category a name."
                        clash -> "There's already a category with that name."
                        else -> null
                    }
                    if (problem == null) { onSave(category.copy(name = name.trim(), color = color)); editing = null }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { category ->
        val used = state.txns.count { it.categoryId == category.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${category.name}\"?") },
            text = {
                Text(
                    if (used == 0) "Nothing uses this category."
                    else "$used transaction(s) will become uncategorised. They are not deleted. Its budget, if any, is removed."
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(category.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
