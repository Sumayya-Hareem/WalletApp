package com.sumayya.wallet.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sumayya.wallet.data.AccountBalance
import com.sumayya.wallet.data.Category
import com.sumayya.wallet.data.CategoryKind
import com.sumayya.wallet.data.DashboardStats
import com.sumayya.wallet.data.DebtDirection
import com.sumayya.wallet.data.Debt
import com.sumayya.wallet.data.DebtPayment
import com.sumayya.wallet.data.DebtSummary
import com.sumayya.wallet.data.Period
import com.sumayya.wallet.data.WalletRepository
import com.sumayya.wallet.data.WalletTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModel(private val repo: WalletRepository) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val selectedPeriod = MutableStateFlow(Period.MONTH)

    val dashboard: StateFlow<DashboardStats> =
        selectedPeriod.flatMapLatest { repo.dashboard(it) }.state(DashboardStats())

    val balances: StateFlow<List<AccountBalance>> = repo.balances.state(emptyList())
    val categories: StateFlow<List<Category>> = repo.allCategories.state(emptyList())
    val transactions: StateFlow<List<WalletTransaction>> = repo.allTransactions.state(emptyList())
    val debts: StateFlow<List<DebtSummary>> = repo.debtSummaries.state(emptyList())

    fun debtPayments(debtId: Long): Flow<List<DebtPayment>> = repo.debtPayments(debtId)

    fun selectPeriod(period: Period) { selectedPeriod.value = period }

    // ---- Transactions ----
    fun addIncome(amount: Long, date: Long, note: String, categoryId: Long?) =
        viewModelScope.launch { repo.addIncome(amount, date, note, categoryId) }

    fun addExpense(amount: Long, date: Long, note: String, categoryId: Long?, fromAccountId: Long) =
        viewModelScope.launch { repo.addExpense(amount, date, note, categoryId, fromAccountId) }

    fun addTransfer(amount: Long, date: Long, note: String, fromAccountId: Long, toAccountId: Long) =
        viewModelScope.launch { repo.addTransfer(amount, date, note, fromAccountId, toAccountId) }

    fun updateTransaction(tx: WalletTransaction) = viewModelScope.launch { repo.updateTransaction(tx) }

    fun deleteTransaction(tx: WalletTransaction) = viewModelScope.launch { repo.deleteTransaction(tx) }

    // ---- Sections ----
    fun addCategory(name: String, kind: CategoryKind) =
        viewModelScope.launch { repo.addCategory(name, kind) }

    fun deleteCategory(category: Category) = viewModelScope.launch { repo.deleteCategory(category) }

    // ---- Accounts ----
    fun updateAccountDetails(accountId: Long, openingBalance: Long, goalAmount: Long?, goalDate: Long?) =
        viewModelScope.launch { repo.updateAccountDetails(accountId, openingBalance, goalAmount, goalDate) }

    fun addSavingsAccount(name: String, openingBalance: Long, goalAmount: Long?, goalDate: Long?) =
        viewModelScope.launch { repo.addSavingsAccount(name, openingBalance, goalAmount, goalDate) }

    // ---- Money owed ----
    fun addDebt(
        personName: String,
        totalAmount: Long,
        note: String,
        direction: DebtDirection,
        addToAvailable: Boolean
    ) = viewModelScope.launch {
        repo.addDebt(personName, totalAmount, note, System.currentTimeMillis(), direction, addToAvailable)
    }

    fun deleteDebt(debt: Debt) = viewModelScope.launch { repo.deleteDebt(debt) }

    fun addDebtPayment(debtId: Long, amount: Long, date: Long, note: String) =
        viewModelScope.launch { repo.addDebtPayment(debtId, amount, date, note) }

    fun deleteDebtPayment(payment: DebtPayment) = viewModelScope.launch { repo.deleteDebtPayment(payment) }

    // ---- Backup ----
    fun exportBackup(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) =
        viewModelScope.launch {
            onResult(
                runCatching {
                    repo.backup.writeText(resolver, uri, repo.backup.buildBackupJson())
                    "Backup saved."
                }.getOrElse { "Backup failed: ${it.message}" }
            )
        }

    fun exportCsv(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) =
        viewModelScope.launch {
            onResult(
                runCatching {
                    repo.backup.writeText(resolver, uri, repo.backup.buildTransactionsCsv())
                    "Entries exported."
                }.getOrElse { "Export failed: ${it.message}" }
            )
        }

    fun restoreBackup(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) =
        viewModelScope.launch {
            onResult(
                runCatching {
                    val text = repo.backup.readText(resolver, uri)
                    repo.backup.restoreFromJson(text)
                    "Backup restored."
                }.getOrElse { "Restore failed: ${it.message}" }
            )
        }

    class Factory(private val repo: WalletRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WalletViewModel(repo) as T
    }
}
