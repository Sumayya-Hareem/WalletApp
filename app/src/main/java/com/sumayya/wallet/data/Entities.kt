package com.sumayya.wallet.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType { AVAILABLE, SAVINGS }

enum class CategoryKind { INCOME, EXPENSE }

enum class TxType { INCOME, EXPENSE, TRANSFER }

enum class DebtDirection { OWED_TO_ME, I_OWE }

/** Where money sits: the Available (spending) account, or a savings account like Easypaisa / Mashreq. */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalance: Long = 0,
    val goalAmount: Long? = null,
    /** Epoch millis of the savings goal end date (e.g. April for Easypaisa). */
    val goalDate: Long? = null
)

/** A "section": an income source (Salary...) or an expense category (Food...). */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind
)

/**
 * One money movement. Amounts are whole rupees.
 *
 * INCOME   -> accountId is where the money lands (normally Available)
 * EXPENSE  -> accountId is where it was paid from (Available, or a savings account)
 * TRANSFER -> accountId is the source, toAccountId is the destination
 */
@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index("categoryId")]
)
data class WalletTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amount: Long,
    val date: Long,
    val note: String = "",
    val categoryId: Long? = null,
    val accountId: Long,
    val toAccountId: Long? = null
)

/**
 * A debt in either direction:
 * OWED_TO_ME - someone owes the user (e.g. brother, 150k+)
 * I_OWE      - the user borrowed money from someone
 */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val totalAmount: Long,
    val note: String = "",
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'OWED_TO_ME'") val direction: DebtDirection = DebtDirection.OWED_TO_ME,
    /** For I_OWE: the account the borrowed money was added to (null = not added to any balance). */
    val receivedAccountId: Long? = null
)

/**
 * A repayment. For OWED_TO_ME debts the money lands in [accountId]; for I_OWE debts it leaves
 * [accountId]. Repayments are never counted as income or as expenses.
 */
@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(
            entity = Debt::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("debtId"), Index("date")]
)
data class DebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val date: Long,
    val note: String = "",
    val accountId: Long
)
