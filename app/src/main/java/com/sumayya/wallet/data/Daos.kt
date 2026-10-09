package com.sumayya.wallet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Account plus its computed current balance. */
data class AccountBalance(
    val id: Long,
    val name: String,
    val type: AccountType,
    val goalAmount: Long?,
    val goalDate: Long?,
    val openingBalance: Long,
    val balance: Long
)

data class CategoryTotal(
    val categoryId: Long?,
    val total: Long
)

data class DebtSummary(
    val id: Long,
    val personName: String,
    val totalAmount: Long,
    val note: String,
    val direction: DebtDirection,
    val paid: Long
) {
    val remaining: Long get() = (totalAmount - paid).coerceAtLeast(0)
}

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE type = 'AVAILABLE' LIMIT 1")
    suspend fun getAvailable(): Account?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    // Backup / restore
    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<Account>

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(items: List<Account>)

    @Query(
        """
        SELECT a.id AS id, a.name AS name, a.type AS type, a.goalAmount AS goalAmount, a.goalDate AS goalDate,
          a.openingBalance AS openingBalance,
          a.openingBalance
          + COALESCE((SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'   AND accountId   = a.id), 0)
          - COALESCE((SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'  AND accountId   = a.id), 0)
          - COALESCE((SELECT SUM(amount) FROM transactions WHERE type = 'TRANSFER' AND accountId   = a.id), 0)
          + COALESCE((SELECT SUM(amount) FROM transactions WHERE type = 'TRANSFER' AND toAccountId = a.id), 0)
          + COALESCE((SELECT SUM(p.amount) FROM debt_payments p JOIN debts d ON d.id = p.debtId
                      WHERE d.direction = 'OWED_TO_ME' AND p.accountId = a.id), 0)
          - COALESCE((SELECT SUM(p.amount) FROM debt_payments p JOIN debts d ON d.id = p.debtId
                      WHERE d.direction = 'I_OWE' AND p.accountId = a.id), 0)
          + COALESCE((SELECT SUM(totalAmount) FROM debts
                      WHERE direction = 'I_OWE' AND receivedAccountId = a.id), 0)
          AS balance
        FROM accounts a
        ORDER BY a.id
        """
    )
    fun observeBalances(): Flow<List<AccountBalance>>
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories ORDER BY kind, name")
    fun observeAll(): Flow<List<Category>>

    // Backup / restore
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<Category>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(items: List<Category>)
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(tx: WalletTransaction): Long

    @Update
    suspend fun update(tx: WalletTransaction)

    @Delete
    suspend fun delete(tx: WalletTransaction)

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<WalletTransaction>>

    // Backup / restore
    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun getAll(): List<WalletTransaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(items: List<WalletTransaction>)

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY date DESC, id DESC")
    fun observeByCategory(categoryId: Long): Flow<List<WalletTransaction>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND date BETWEEN :from AND :to")
    fun observeSum(type: String, from: Long, to: Long): Flow<Long>

    /**
     * Net amount moved INTO savings accounts in the period (from Available),
     * minus amounts moved back out of savings to Available.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
              WHEN toa.type = 'SAVINGS' AND fa.type != 'SAVINGS' THEN t.amount
              WHEN fa.type = 'SAVINGS' AND toa.type != 'SAVINGS' THEN -t.amount
              ELSE 0
            END), 0)
        FROM transactions t
        JOIN accounts fa  ON fa.id  = t.accountId
        JOIN accounts toa ON toa.id = t.toAccountId
        WHERE t.type = 'TRANSFER' AND t.date BETWEEN :from AND :to
        """
    )
    fun observeNetSaved(from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT categoryId AS categoryId, COALESCE(SUM(amount), 0) AS total
        FROM transactions
        WHERE type = :type AND date BETWEEN :from AND :to
        GROUP BY categoryId
        ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(type: String, from: Long, to: Long): Flow<List<CategoryTotal>>
}

@Dao
interface DebtDao {
    @Insert
    suspend fun insertDebt(debt: Debt): Long

    @Update
    suspend fun updateDebt(debt: Debt)

    @Delete
    suspend fun deleteDebt(debt: Debt)

    @Insert
    suspend fun insertPayment(payment: DebtPayment): Long

    @Delete
    suspend fun deletePayment(payment: DebtPayment)

    @Query(
        """
        SELECT d.id AS id, d.personName AS personName, d.totalAmount AS totalAmount, d.note AS note, d.direction AS direction,
          COALESCE((SELECT SUM(amount) FROM debt_payments WHERE debtId = d.id), 0) AS paid
        FROM debts d
        ORDER BY d.createdAt DESC
        """
    )
    fun observeSummaries(): Flow<List<DebtSummary>>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY date DESC, id DESC")
    fun observePayments(debtId: Long): Flow<List<DebtPayment>>

    /** Total of repayments in the period for one direction ("OWED_TO_ME" = received, "I_OWE" = paid out). */
    @Query(
        """
        SELECT COALESCE(SUM(p.amount), 0)
        FROM debt_payments p
        JOIN debts d ON d.id = p.debtId
        WHERE d.direction = :direction AND p.date BETWEEN :from AND :to
        """
    )
    fun observePaymentsInPeriod(direction: String, from: Long, to: Long): Flow<Long>

    // Backup / restore
    @Query("SELECT * FROM debts ORDER BY id")
    suspend fun getAllDebts(): List<Debt>

    @Query("SELECT * FROM debt_payments ORDER BY id")
    suspend fun getAllPayments(): List<DebtPayment>

    @Query("DELETE FROM debt_payments")
    suspend fun deleteAllPayments()

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()

    @Insert
    suspend fun insertDebts(items: List<Debt>)

    @Insert
    suspend fun insertPayments(items: List<DebtPayment>)
}
