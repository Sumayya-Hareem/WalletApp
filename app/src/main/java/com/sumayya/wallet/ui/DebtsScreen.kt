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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import com.sumayya.wallet.data.DebtDirection
import com.sumayya.wallet.data.DebtPayment
import com.sumayya.wallet.data.DebtSummary

/** Money people owe you, and money you borrowed, on one screen with a switch at the top. */
@Composable
fun DebtsScreen(vm: WalletViewModel, padding: PaddingValues) {
    val allDebts by vm.debts.collectAsStateWithLifecycle()
    var direction by remember { mutableStateOf(DebtDirection.OWED_TO_ME) }
    var addingPerson by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DebtSummary?>(null) }

    val owedToMe = direction == DebtDirection.OWED_TO_ME
    val debts = allDebts.filter { it.direction == direction }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = owedToMe,
                    onClick = { direction = DebtDirection.OWED_TO_ME },
                    label = { Text("Owed to me") }
                )
                FilterChip(
                    selected = !owedToMe,
                    onClick = { direction = DebtDirection.I_OWE },
                    label = { Text("I owe") }
                )
            }
        }
        item {
            Button(onClick = { addingPerson = true }) {
                Text(if (owedToMe) "Add person who owes you" else "Add money you borrowed")
            }
        }
        item {
            Text(
                if (owedToMe) {
                    "Repayments go into Available money, but are not counted as income."
                } else {
                    "Payments you make come out of Available money, but are not counted as expenses."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (debts.isEmpty()) {
            item {
                Text(
                    if (owedToMe) "Nobody owes you anything yet." else "You haven't recorded any borrowed money.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(debts, key = { it.id }) { debt ->
            DebtCard(vm, debt, onDelete = { pendingDelete = debt })
        }
    }

    if (addingPerson) {
        AddDebtDialog(
            direction = direction,
            onDismiss = { addingPerson = false },
            onSave = { name, total, note, addToAvailable ->
                vm.addDebt(name, total, note, direction, addToAvailable)
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
                    "This removes the record and all its payments, and your balances will change " +
                        "to match (as if it never happened)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDebt(
                        Debt(
                            id = d.id,
                            personName = d.personName,
                            totalAmount = d.totalAmount,
                            note = d.note,
                            createdAt = 0L
                        )
                    )
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DebtCard(vm: WalletViewModel, debt: DebtSummary, onDelete: () -> Unit) {
    val owedToMe = debt.direction == DebtDirection.OWED_TO_ME
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
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }

            val progress = if (debt.totalAmount > 0) {
                (debt.paid.toFloat() / debt.totalAmount).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            LabelValueRow(if (owedToMe) "Total owed to you" else "Total you owe", Fmt.money(debt.totalAmount))
            LabelValueRow(
                if (owedToMe) "Received so far" else "Paid back so far",
                Fmt.money(debt.paid),
                valueColor = IncomeGreen
            )
            LabelValueRow(
                if (owedToMe) "Still to receive" else "Still to pay",
                Fmt.money(debt.remaining),
                valueColor = ExpenseRed
            )

            if (debt.remaining == 0L && debt.totalAmount > 0) {
                Text(
                    if (owedToMe) "Fully paid back to you." else "Fully paid off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IncomeGreen
                )
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
                AmountField(
                    amountText,
                    { amountText = it },
                    Modifier.weight(1f),
                    label = if (owedToMe) "Received (Rs)" else "Paying back (Rs)"
                )
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
            DateField(
                date,
                { date = it },
                Modifier.fillMaxWidth(),
                label = if (owedToMe) "Received on" else "Paid on"
            )

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
    direction: DebtDirection,
    onDismiss: () -> Unit,
    onSave: (name: String, total: Long, note: String, addToAvailable: Boolean) -> Unit
) {
    val owedToMe = direction == DebtDirection.OWED_TO_ME
    var name by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var addToAvailable by remember { mutableStateOf(true) }
    val total = totalText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (owedToMe) "Who owes you?" else "Who did you borrow from?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                AmountField(
                    totalText,
                    { totalText = it },
                    label = if (owedToMe) "Total owed to you (Rs)" else "Amount borrowed (Rs)"
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") }
                )
                if (!owedToMe) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addToAvailable, onCheckedChange = { addToAvailable = it })
                        Text("Add this amount to my Available money now")
                    }
                    Text(
                        "Untick it if you already spent this money, or if your Available balance " +
                            "already includes it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, total, note.trim(), addToAvailable) },
                enabled = name.isNotBlank() && total > 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
