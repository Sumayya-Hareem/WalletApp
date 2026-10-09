package com.sumayya.wallet.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DashboardStats(
    val income: Long = 0,
    val expenses: Long = 0,
    /** Net moved into savings accounts during the period. */
    val saved: Long = 0,
    /** Money received back from people who owe you during the period (NOT counted in income). */
    val repaid: Long = 0,
    /** Money you paid back on borrowed money during the period (NOT counted in expenses). */
    val paidOut: Long = 0,
    val expenseByCategory: List<CategoryTotal> = emptyList(),
    val incomeByCategory: List<CategoryTotal> = emptyList()
)

class WalletRepository(private val db: WalletDatabase) {
    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val debts = db.debtDao()

    // ---- Observables ----
    val balances: Flow<List<AccountBalance>> = accounts.observeBalances()
    val allCategories: Flow<List<Category>> = categories.observeAll()
    val allTransactions: Flow<List<WalletTransaction>> = transactions.observeAll()
    val debtSummaries: Flow<List<DebtSummary>> = debts.observeSummaries()

    val backup = BackupManager(db)

    fun debtPayments(debtId: Long): Flow<List<DebtPayment>> = debts.observePayments(debtId)

    fun dashboard(period: Period): Flow<DashboardStats> {
        val (from, to) = period.range()
        val incomeAndExpense = combine(
            transactions.observeSum(TxType.INCOME.name, from, to),
            transactions.observeSum(TxType.EXPENSE.name, from, to)
        ) { income, expenses -> income to expenses }

        val savedAndDebts = combine(
            transactions.observeNetSaved(from, to),
            debts.observePaymentsInPeriod(DebtDirection.OWED_TO_ME.name, from, to),
            debts.observePaymentsInPeriod(DebtDirection.I_OWE.name, from, to)
        ) { saved, received, paidOut -> Triple(saved, received, paidOut) }

        val breakdowns = combine(
            transactions.observeCategoryTotals(TxType.EXPENSE.name, from, to),
            transactions.observeCategoryTotals(TxType.INCOME.name, from, to)
        ) { expense, income -> expense to income }

        return combine(incomeAndExpense, savedAndDebts, breakdowns) { ie, sr, bd ->
            DashboardStats(
                income = ie.first,
                expenses = ie.second,
                saved = sr.first,
                repaid = sr.second,
                paidOut = sr.third,
                expenseByCategory = bd.first,
                incomeByCategory = bd.second
            )
        }
    }

    // ---- Transactions ----
    suspend fun addIncome(amount: Long, date: Long, note: String, categoryId: Long?, accountId: Long? = null) {
        val target = accountId ?: availableId()
        transactions.insert(
            WalletTransaction(
                type = TxType.INCOME, amount = amount, date = date,
                note = note, categoryId = categoryId, accountId = target
            )
        )
    }

    suspend fun addExpense(amount: Long, date: Long, note: String, categoryId: Long?, fromAccountId: Long) {
        transactions.insert(
            WalletTransaction(
                type = TxType.EXPENSE, amount = amount, date = date,
                note = note, categoryId = categoryId, accountId = fromAccountId
            )
        )
    }

    suspend fun addTransfer(amount: Long, date: Long, note: String, fromAccountId: Long, toAccountId: Long) {
        require(fromAccountId != toAccountId) { "Choose two different accounts" }
        transactions.insert(
            WalletTransaction(
                type = TxType.TRANSFER, amount = amount, date = date,
                note = note, accountId = fromAccountId, toAccountId = toAccountId
            )
        )
    }

    suspend fun updateTransaction(tx: WalletTransaction) = transactions.update(tx)

    suspend fun deleteTransaction(tx: WalletTransaction) = transactions.delete(tx)

    // ---- Categories (sections) ----
    suspend fun addCategory(name: String, kind: CategoryKind) {
        categories.insert(Category(name = name.trim(), kind = kind))
    }

    suspend fun deleteCategory(category: Category) = categories.delete(category)

    // ---- Accounts ----
    /** [openingBalance] is money that was already in the account before the app was used. */
    suspend fun updateAccountDetails(accountId: Long, openingBalance: Long, goalAmount: Long?, goalDate: Long?) {
        val account = accounts.getById(accountId) ?: return
        accounts.update(
            account.copy(openingBalance = openingBalance, goalAmount = goalAmount, goalDate = goalDate)
        )
    }

    suspend fun addSavingsAccount(name: String, openingBalance: Long, goalAmount: Long?, goalDate: Long?) {
        accounts.insert(
            Account(
                name = name.trim(), type = AccountType.SAVINGS, openingBalance = openingBalance,
                goalAmount = goalAmount, goalDate = goalDate
            )
        )
    }

    // ---- Money owed to the user ----
    /**
     * [addToAvailable] only applies to borrowed money (I_OWE): the amount is added to Available money,
     * because that is where the borrowed cash arrived.
     */
    suspend fun addDebt(
        personName: String,
        totalAmount: Long,
        note: String,
        createdAt: Long,
        direction: DebtDirection,
        addToAvailable: Boolean
    ) {
        val receivedInto = if (direction == DebtDirection.I_OWE && addToAvailable) availableId() else null
        debts.insertDebt(
            Debt(
                personName = personName.trim(), totalAmount = totalAmount, note = note, createdAt = createdAt,
                direction = direction, receivedAccountId = receivedInto
            )
        )
    }

    suspend fun deleteDebt(debt: Debt) = debts.deleteDebt(debt)

    /** A repayment goes into Available money but stays out of income stats. */
    suspend fun addDebtPayment(debtId: Long, amount: Long, date: Long, note: String) {
        debts.insertPayment(
            DebtPayment(debtId = debtId, amount = amount, date = date, note = note, accountId = availableId())
        )
    }

    suspend fun deleteDebtPayment(payment: DebtPayment) = debts.deletePayment(payment)

    private suspend fun availableId(): Long =
        accounts.getAvailable()?.id ?: error("Available account is missing")
}
