package com.sumayya.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumayya.wallet.data.AccountType
import com.sumayya.wallet.data.CategoryKind
import com.sumayya.wallet.data.TxType
import com.sumayya.wallet.data.WalletTransaction

/**
 * Add a new entry, or edit an existing one when [editing] is given.
 */
@Composable
fun AddTransactionDialog(
    initialType: TxType,
    vm: WalletViewModel,
    onDismiss: () -> Unit,
    editing: WalletTransaction? = null
) {
    val balances by vm.balances.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    var type by remember { mutableStateOf(editing?.type ?: initialType) }
    var amountText by remember { mutableStateOf(editing?.amount?.toString() ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var date by remember { mutableLongStateOf(editing?.date ?: System.currentTimeMillis()) }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var fromId by remember { mutableStateOf(editing?.accountId) }
    var toId by remember { mutableStateOf(editing?.toAccountId) }

    val availableAcc = balances.firstOrNull { it.type == AccountType.AVAILABLE }
    val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categoryOptions = categories.filter { it.kind == kind }

    // Effective selections fall back to sensible defaults until the user picks something.
    val category = categoryOptions.firstOrNull { it.id == categoryId } ?: categoryOptions.firstOrNull()
    val fromAccount = balances.firstOrNull { it.id == fromId } ?: availableAcc
    val toOptions = balances.filter { it.id != fromAccount?.id }
    val toAccount = toOptions.firstOrNull { it.id == toId }
        ?: toOptions.firstOrNull { it.type == AccountType.SAVINGS }
        ?: toOptions.firstOrNull()

    val amount = amountText.toLongOrNull() ?: 0L
    val spendsFrom = if (type == TxType.INCOME) null else fromAccount
    // When editing, the old amount is already taken out of the balance, so give it back for the check.
    val editGiveBack = if (
        editing != null && spendsFrom != null && editing.accountId == spendsFrom.id &&
        (editing.type == TxType.EXPENSE || editing.type == TxType.TRANSFER)
    ) editing.amount else 0L
    val spendable = (spendsFrom?.balance ?: 0L) + editGiveBack
    val insufficient = spendsFrom != null && amount > spendable
    val canSave = amount > 0 && when (type) {
        TxType.INCOME -> availableAcc != null
        TxType.EXPENSE -> fromAccount != null
        TxType.TRANSFER -> fromAccount != null && toAccount != null
    }

    // The optional "what was it for" box, placed right under the section so it is easy to see.
    val noteField: @Composable () -> Unit = {
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = {
                Text(
                    when (type) {
                        TxType.EXPENSE -> "What was it for? (optional)"
                        TxType.INCOME -> "Details (optional)"
                        TxType.TRANSFER -> "Note (optional)"
                    }
                )
            },
            supportingText = {
                if (type == TxType.EXPENSE) Text("Just for you, e.g. LASIK check-up, Abu's phone case")
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (editing == null) "Add entry" else "Edit entry",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxType.entries.forEach { t ->
                        FilterChip(
                            selected = t == type,
                            onClick = { type = t; categoryId = null },
                            label = {
                                Text(
                                    when (t) {
                                        TxType.INCOME -> "Income"
                                        TxType.EXPENSE -> "Expense"
                                        TxType.TRANSFER -> "Transfer"
                                    }
                                )
                            }
                        )
                    }
                }

                AmountField(amountText, { amountText = it }, Modifier.fillMaxWidth())

                when (type) {
                    TxType.INCOME -> {
                        DropdownField(
                            label = "Income source",
                            options = categoryOptions,
                            selected = category,
                            optionLabel = { it.name },
                            onSelect = { categoryId = it.id },
                            modifier = Modifier.fillMaxWidth()
                        )
                        noteField()
                        Text(
                            "Lands in Available money.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TxType.EXPENSE -> {
                        DropdownField(
                            label = "Section",
                            options = categoryOptions,
                            selected = category,
                            optionLabel = { it.name },
                            onSelect = { categoryId = it.id },
                            modifier = Modifier.fillMaxWidth()
                        )
                        noteField()
                        DropdownField(
                            label = "Paid from",
                            options = balances,
                            selected = fromAccount,
                            optionLabel = { "${it.name} (${Fmt.money(it.balance)})" },
                            onSelect = { fromId = it.id },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    TxType.TRANSFER -> {
                        DropdownField(
                            label = "From",
                            options = balances,
                            selected = fromAccount,
                            optionLabel = { "${it.name} (${Fmt.money(it.balance)})" },
                            onSelect = { fromId = it.id; toId = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownField(
                            label = "To",
                            options = toOptions,
                            selected = toAccount,
                            optionLabel = { "${it.name} (${Fmt.money(it.balance)})" },
                            onSelect = { toId = it.id },
                            modifier = Modifier.fillMaxWidth()
                        )
                        noteField()
                    }
                }

                if (insufficient) {
                    Text(
                        "Heads up: ${spendsFrom?.name} only has ${Fmt.money(spendable)}. " +
                            "You can still save, but the balance will go negative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseRed
                    )
                }

                DateField(date, { date = it }, Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        if (editing == null) {
                            when (type) {
                                TxType.INCOME -> vm.addIncome(amount, date, note.trim(), category?.id)
                                TxType.EXPENSE -> vm.addExpense(
                                    amount, date, note.trim(), category?.id, fromAccount!!.id
                                )
                                TxType.TRANSFER -> vm.addTransfer(
                                    amount, date, note.trim(), fromAccount!!.id, toAccount!!.id
                                )
                            }
                        } else {
                            val accountId = when (type) {
                                TxType.INCOME ->
                                    if (editing.type == TxType.INCOME) editing.accountId else availableAcc!!.id
                                else -> fromAccount!!.id
                            }
                            vm.updateTransaction(
                                WalletTransaction(
                                    id = editing.id,
                                    type = type,
                                    amount = amount,
                                    date = date,
                                    note = note.trim(),
                                    categoryId = if (type == TxType.TRANSFER) null else category?.id,
                                    accountId = accountId,
                                    toAccountId = if (type == TxType.TRANSFER) toAccount!!.id else null
                                )
                            )
                        }
                        onDismiss()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (editing == null) "Save" else "Save changes") }
            }
        }
    }
}
