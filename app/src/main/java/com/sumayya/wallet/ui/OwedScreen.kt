package com.sumayya.wallet.ui

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumayya.wallet.data.Debt
import com.sumayya.wallet.data.DebtPayment
import com.sumayya.wallet.data.DebtSummary

@Composable
fun OwedScreen(vm: WalletViewModel, padding: PaddingValues) {
    val debts by vm.debts.collectAsStateWithLifecycle()
    var addingPerson by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DebtSummary?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = { addingPerson = true }) { Text("Add person who owes you") }
        }
        item {
            Text(
                "Repayments go into Available money, but are not counted as income.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(debts, key = { it.id }) { debt ->
            DebtCard(vm, debt, onDelete = { pendingDelete = debt })
        }
    }

    if (addingPerson) {
        AddDebtDialog(
            onDismiss = { addingPerson = false },
            onSave = { name, total, note ->
                vm.addDebt(name, total, note)
                addingPerson = false
            }
        )
    }

    pendingDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${d.personName}?") },
            text = {
                Text(
                    "This removes the record and all its repayments. " +
                        "Money already received stays in Available only if you keep the entries - " +
                        "deleting repayments lowers your Available balance."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDebt(Debt(d.id, d.personName, d.totalAmount, d.note, 0L))
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DebtCard(vm: WalletViewModel, debt: DebtSummary, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(debt.personName, style = MaterialTheme.typography.titleMedium)
                    if (debt.note.isNotBlank()) Text(debt.note, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete person")
                }
            }

            val progress = if (debt.totalAmount > 0) {
                (debt.paid.toFloat() / debt.totalAmount).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            LabelValueRow("Total owed", Fmt.money(debt.totalAmount))
            LabelValueRow("Paid so far", Fmt.money(debt.paid), valueColor = IncomeGreen)
            LabelValueRow("Remaining", Fmt.money(debt.remaining), valueColor = ExpenseRed)

            if (debt.remaining == 0L && debt.totalAmount > 0) {
                Text("Fully paid back.", style = MaterialTheme.typography.bodyMedium, color = IncomeGreen)
            }

            // Quick entry: tap 500 / 1000 or type any amount.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { amountText = "500" }, label = { Text("500") })
                AssistChip(onClick = { amountText = "1000" }, label = { Text("1000") })
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AmountField(amountText, { amountText = it }, Modifier.weight(1f), label = "Received (Rs)")
                Button(
                    onClick = {
                        val amount = amountText.toLongOrNull() ?: return@Button
                        vm.addDebtPayment(debt.id, amount, date, "")
                        amountText = ""
                        date = System.currentTimeMillis()
                    },
                    enabled = (amountText.toLongOrNull() ?: 0L) > 0
                ) { Text("Add") }
            }
            DateField(date, { date = it }, Modifier.fillMaxWidth(), label = "Received on")

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide payments" else "Show payments")
            }
            if (expanded) PaymentList(vm, debt.id)
        }
    }
}

@Composable
private fun PaymentList(vm: WalletViewModel, debtId: Long) {
    val flow = remember(debtId) { vm.debtPayments(debtId) }
    val payments by flow.collectAsState(initial = emptyList<DebtPayment>())

    if (payments.isEmpty()) {
        Text("No payments yet.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        payments.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Fmt.date(p.date)}   ${Fmt.money(p.amount)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = { vm.deleteDebtPayment(p) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete payment")
                }
            }
        }
    }
}

@Composable
private fun AddDebtDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, total: Long, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val total = totalText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who owes you?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                AmountField(totalText, { totalText = it }, label = "Total owed (Rs)")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, total, note.trim()) },
                enabled = name.isNotBlank() && total > 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
