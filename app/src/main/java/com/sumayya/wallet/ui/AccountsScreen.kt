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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Available money plus the savings accounts, with starting balances and goals. */
@Composable
fun AccountsScreen(
    vm: WalletViewModel,
    padding: PaddingValues,
    onMoveMoney: () -> Unit
) {
    val balances by vm.balances.collectAsStateWithLifecycle()
    val available = balances.firstOrNull { it.type == AccountType.AVAILABLE }
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
                "Already have money saved? Tap \"Edit\" on an account and enter its starting balance. " +
                    "To spend from savings (LASIK, a phone...), add an Expense and choose the savings " +
                    "account under \"Paid from\".",
                style = MaterialTheme.typography.bodySmall
            )
        }
        available?.let { acc ->
            item(key = "available") { AccountCard(acc, onEdit = { editing = acc }) }
        }
        items(savings, key = { it.id }) { acc ->
            AccountCard(acc, onEdit = { editing = acc })
        }
    }

    editing?.let { acc ->
        AccountDialog(
            title = "Edit ${acc.name}",
            isAvailable = acc.type == AccountType.AVAILABLE,
            showName = false,
            initialOpening = acc.openingBalance,
            initialGoalAmount = acc.goalAmount,
            initialGoalDate = acc.goalDate,
            onDismiss = { editing = null },
            onSave = { _, opening, goal, date ->
                vm.updateAccountDetails(acc.id, opening, goal, date)
                editing = null
            }
        )
    }
    if (adding) {
        AccountDialog(
            title = "New savings account",
            isAvailable = false,
            showName = true,
            initialOpening = 0L,
            initialGoalAmount = null,
            initialGoalDate = null,
            onDismiss = { adding = false },
            onSave = { name, opening, goal, date ->
                if (name.isNotBlank()) vm.addSavingsAccount(name, opening, goal, date)
                adding = false
            }
        )
    }
}

@Composable
private fun AccountCard(acc: AccountBalance, onEdit: () -> Unit) {
    val isAvailable = acc.type == AccountType.AVAILABLE
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isAvailable) "Available money" else acc.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                Fmt.money(acc.balance),
                style = MaterialTheme.typography.headlineSmall,
                color = if (acc.balance < 0) ExpenseRed else androidx.compose.ui.graphics.Color.Unspecified
            )
            if (acc.openingBalance != 0L) {
                Text("Started with ${Fmt.money(acc.openingBalance)}", style = MaterialTheme.typography.bodySmall)
            }

            if (!isAvailable) {
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
            }
            TextButton(onClick = onEdit) {
                Text(if (isAvailable) "Edit starting balance" else "Edit balance & goal")
            }
        }
    }
}

@Composable
private fun AccountDialog(
    title: String,
    isAvailable: Boolean,
    showName: Boolean,
    initialOpening: Long,
    initialGoalAmount: Long?,
    initialGoalDate: Long?,
    onDismiss: () -> Unit,
    onSave: (name: String, opening: Long, goalAmount: Long?, goalDate: Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var openingText by remember { mutableStateOf(if (initialOpening != 0L) initialOpening.toString() else "") }
    var goalText by remember { mutableStateOf(initialGoalAmount?.toString() ?: "") }
    var hasDate by remember { mutableStateOf(initialGoalDate != null) }
    var date by remember { mutableLongStateOf(initialGoalDate ?: System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                }
                AmountField(
                    openingText,
                    { openingText = it },
                    label = if (isAvailable) "Money you already have to spend" else "Money already saved here"
                )
                Text(
                    "This is money you had before using the app. It counts toward the balance, " +
                        "but not as income.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isAvailable) {
                    AmountField(goalText, { goalText = it }, label = "Goal amount (optional)")
                    if (hasDate) {
                        DateField(date, { date = it }, Modifier.fillMaxWidth(), label = "Ends")
                        TextButton(onClick = { hasDate = false }) { Text("Remove end date") }
                    } else {
                        OutlinedButton(onClick = { hasDate = true }) { Text("Set end date") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name,
                        openingText.toLongOrNull() ?: 0L,
                        if (isAvailable) null else goalText.toLongOrNull()?.takeIf { it > 0 },
                        if (!isAvailable && hasDate) date else null
                    )
                },
                enabled = !showName || name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
