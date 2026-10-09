package com.sumayya.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumayya.wallet.data.Category
import com.sumayya.wallet.data.CategoryKind

@Composable
fun SectionsScreen(vm: WalletViewModel, padding: PaddingValues) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<CategoryKind?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    val income = categories.filter { it.kind == CategoryKind.INCOME }
    val expense = categories.filter { it.kind == CategoryKind.EXPENSE }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { BackupCard(vm) }
        item { SectionTitle("Income sources") }
        items(income, key = { "i${it.id}" }) { c -> CategoryRow(c) { pendingDelete = c } }
        item { Button(onClick = { adding = CategoryKind.INCOME }) { Text("Add income source") } }

        item { SectionTitle("Expense sections") }
        items(expense, key = { "e${it.id}" }) { c -> CategoryRow(c) { pendingDelete = c } }
        item { Button(onClick = { adding = CategoryKind.EXPENSE }) { Text("Add expense section") } }
    }

    adding?.let { kind ->
        var name by remember(kind) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(if (kind == CategoryKind.INCOME) "New income source" else "New expense section") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.addCategory(name, kind); adding = null },
                    enabled = name.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${c.name}\"?") },
            text = { Text("Existing entries keep their amounts but will show as Uncategorized.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCategory(c); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                category.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete section")
            }
        }
    }
}
