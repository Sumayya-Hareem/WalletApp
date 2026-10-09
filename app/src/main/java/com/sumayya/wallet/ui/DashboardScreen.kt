package com.sumayya.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumayya.wallet.data.AccountType
import com.sumayya.wallet.data.CategoryTotal
import com.sumayya.wallet.data.DebtDirection
import com.sumayya.wallet.data.Period

@Composable
fun DashboardScreen(vm: WalletViewModel, padding: PaddingValues) {
    val period by vm.selectedPeriod.collectAsStateWithLifecycle()
    val stats by vm.dashboard.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()

    val available = balances.firstOrNull { it.type == AccountType.AVAILABLE }?.balance ?: 0L
    val savingsTotal = balances.filter { it.type == AccountType.SAVINGS }.sumOf { it.balance }
    val owedToMe = debts.filter { it.direction == DebtDirection.OWED_TO_ME }.sumOf { it.remaining }
    val iOwe = debts.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.remaining }
    val periodWord = if (period == Period.LIFETIME) "in total" else "this ${period.label.lowercase()}"
    val categoryNames = categories.associate { it.id to it.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Headline: what is available to spend right now.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Available to spend", style = MaterialTheme.typography.labelLarge)
                Text(
                    Fmt.money(available),
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (available < 0) ExpenseRed else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Total in savings: ${Fmt.money(savingsTotal)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Period selector.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                FilterChip(
                    selected = p == period,
                    onClick = { vm.selectPeriod(p) },
                    label = { Text(p.label) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Income", stats.income, IncomeGreen, Modifier.weight(1f))
            StatCard("Expenses", stats.expenses, ExpenseRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Moved to savings", stats.saved, TransferBlue, Modifier.weight(1f))
            StatCard(
                "Income - expenses",
                stats.income - stats.expenses,
                if (stats.income - stats.expenses < 0) ExpenseRed else IncomeGreen,
                Modifier.weight(1f)
            )
        }

        // Debts are kept apart from income and expenses on purpose.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Money owed to you", style = MaterialTheme.typography.labelLarge)
                LabelValueRow("Still to receive", Fmt.money(owedToMe))
                LabelValueRow("Received $periodWord", Fmt.money(stats.repaid))
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Money you owe", style = MaterialTheme.typography.labelLarge)
                LabelValueRow("Still to pay back", Fmt.money(iOwe))
                LabelValueRow("Paid back $periodWord", Fmt.money(stats.paidOut))
            }
        }

        BreakdownCard("Expenses by section", stats.expenseByCategory, categoryNames, stats.expenses)
        BreakdownCard("Income by source", stats.incomeByCategory, categoryNames, stats.income)
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    rows: List<CategoryTotal>,
    names: Map<Long, String>,
    total: Long
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (rows.isEmpty()) {
                Text("Nothing yet for this period", style = MaterialTheme.typography.bodyMedium)
            } else {
                rows.forEach { row ->
                    val name = row.categoryId?.let { names[it] } ?: "Uncategorized"
                    val percent = if (total > 0) " (${row.total * 100 / total}%)" else ""
                    LabelValueRow(name, Fmt.money(row.total) + percent)
                }
            }
        }
    }
}
