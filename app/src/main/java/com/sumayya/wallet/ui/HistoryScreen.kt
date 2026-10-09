package com.sumayya.wallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.sumayya.wallet.data.TxType
import com.sumayya.wallet.data.WalletTransaction

@Composable
fun HistoryScreen(
    vm: WalletViewModel,
    padding: PaddingValues,
    onEdit: (WalletTransaction) -> Unit
) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<WalletTransaction?>(null) }

    val categoryNames = categories.associate { it.id to it.name }
    val accountNames = balances.associate { it.id to it.name }

    if (transactions.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text("No entries yet. Tap + to add your first income or expense.")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Tap an entry to edit it.", style = MaterialTheme.typography.bodySmall) }
            items(transactions, key = { it.id }) { tx ->
                TransactionRow(
                    tx = tx,
                    title = when (tx.type) {
                        TxType.TRANSFER ->
                            "${accountNames[tx.accountId] ?: "?"} -> ${accountNames[tx.toAccountId] ?: "?"}"
                        else -> tx.categoryId?.let { categoryNames[it] } ?: "Uncategorized"
                    },
                    accountName = accountNames[tx.accountId] ?: "?",
                    onEdit = { onEdit(tx) },
                    onDelete = { pendingDelete = tx }
                )
            }
        }
    }

    pendingDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("${Fmt.money(tx.amount)} on ${Fmt.date(tx.date)} will be removed and balances will update.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTransaction(tx); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TransactionRow(
    tx: WalletTransaction,
    title: String,
    accountName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (sign, color) = when (tx.type) {
        TxType.INCOME -> "+" to IncomeGreen
        TxType.EXPENSE -> "-" to ExpenseRed
        TxType.TRANSFER -> "" to TransferBlue
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                val detail = buildString {
                    append(Fmt.date(tx.date))
                    if (tx.type != TxType.TRANSFER) append(" · ").append(accountName)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall)
                if (tx.note.isNotBlank()) {
                    Text(tx.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "$sign${Fmt.money(tx.amount)}",
                style = MaterialTheme.typography.titleSmall,
                color = color
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete entry")
            }
        }
    }
}
