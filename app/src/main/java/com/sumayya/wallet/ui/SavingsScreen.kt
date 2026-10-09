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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumayya.wallet.data.AccountBalance
import com.sumayya.wallet.data.AccountType

@Composable
fun SavingsScreen(
    vm: WalletViewModel,
    padding: PaddingValues,
    onMoveMoney: () -> Unit
) {
    val balances by vm.balances.collectAsStateWithLifecycle()
    val savings = balances.filter { it.type == AccountType.SAVINGS }
    var editing by remember { mutableStateOf<AccountBalance?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMoveMoney) { Text("Move money") }
                OutlinedButton(onClick = { adding = true }) { Text("New savings account") }
            }
        }
        item {
            Text(
                "To spend from savings (LASIK, a phone...), add an Expense and choose the savings account under \"Paid from\".",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(savings, key = { it.id }) { acc ->
            SavingsCard(acc, onEditGoal = { editing = acc })
        }
    }

    editing?.let { acc ->
        GoalDialog(
            title = "Goal for ${acc.name}",
            initialAmount = acc.goalAmount,
            initialDate = acc.goalDate,
            showName = false,
            onDismiss = { editing = null },
            onSave = { _, amount, date ->
                vm.updateGoal(acc.id, amount, date)
                editing = null
            }
        )
    }
    if (adding) {
        GoalDialog(
            title = "New savings account",
            initialAmount = null,
            initialDate = null,
            showName = true,
            onDismiss = { adding = false },
            onSave = { name, amount, date ->
                if (name.isNotBlank()) vm.addSavingsAccount(name, amount, date)
                adding = false
            }
        )
    }
}

@Composable
private fun SavingsCard(acc: AccountBalance, onEditGoal: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(acc.name, style = MaterialTheme.typography.titleMedium)
            Text(Fmt.money(acc.balance), style = MaterialTheme.typography.headlineSmall)

            acc.goalAmount?.takeIf { it > 0 }?.let { goal ->
                val progress = (acc.balance.toFloat() / goal).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                LabelValueRow("Goal", "${Fmt.money(acc.balance)} of ${Fmt.money(goal)}")
            }
            acc.goalDate?.let { date ->
                val days = Fmt.daysUntil(date)
                val label = when {
                    days > 0 -> "$days days left (ends ${Fmt.date(date)})"
                    days == 0L -> "Ends today"
                    else -> "Ended ${Fmt.date(date)}"
                }
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onEditGoal) { Text("Edit goal") }
        }
    }
}

@Composable
private fun GoalDialog(
    title: String,
    initialAmount: Long?,
    initialDate: Long?,
    showName: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Long?, date: Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf(initialAmount?.toString() ?: "") }
    var hasDate by remember { mutableStateOf(initialDate != null) }
    var date by remember { mutableLongStateOf(initialDate ?: System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                }
                AmountField(amountText, { amountText = it }, label = "Goal amount (optional)")
                if (hasDate) {
                    DateField(date, { date = it }, Modifier.fillMaxWidth(), label = "Ends")
                    TextButton(onClick = { hasDate = false }) { Text("Remove end date") }
                } else {
                    OutlinedButton(onClick = { hasDate = true }) { Text("Set end date") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, amountText.toLongOrNull()?.takeIf { it > 0 }, if (hasDate) date else null)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
