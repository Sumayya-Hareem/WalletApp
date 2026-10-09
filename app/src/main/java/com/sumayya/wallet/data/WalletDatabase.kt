package com.sumayya.wallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import java.time.ZoneId

class Converters {
    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun categoryKindToString(v: CategoryKind): String = v.name
    @TypeConverter fun stringToCategoryKind(v: String): CategoryKind = CategoryKind.valueOf(v)

    @TypeConverter fun txTypeToString(v: TxType): String = v.name
    @TypeConverter fun stringToTxType(v: String): TxType = TxType.valueOf(v)

    @TypeConverter fun debtDirectionToString(v: DebtDirection): String = v.name
    @TypeConverter fun stringToDebtDirection(v: String): DebtDirection = DebtDirection.valueOf(v)
}

/** v1 -> v2: debts can now go either way, and borrowed money can be added to an account. Keeps all data. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE debts ADD COLUMN direction TEXT NOT NULL DEFAULT 'OWED_TO_ME'")
        db.execSQL("ALTER TABLE debts ADD COLUMN receivedAccountId INTEGER")
    }
}

@Database(
    entities = [Account::class, Category::class, WalletTransaction::class, Debt::class, DebtPayment::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao

    companion object {
        @Volatile private var instance: WalletDatabase? = null

        fun get(context: Context): WalletDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): WalletDatabase =
            Room.databaseBuilder(context, WalletDatabase::class.java, "wallet.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedCallback)
                .build()
    }
}

/** First-run data: the three accounts and a starter set of sections. All editable later. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        // Easypaisa goal ends in April (next April after Oct 2026 -> April 2027).
        val easypaisaGoal = LocalDate.of(2027, 4, 30)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        db.execSQL("INSERT INTO accounts (name, type, openingBalance) VALUES ('Available', 'AVAILABLE', 0)")
        db.execSQL(
            "INSERT INTO accounts (name, type, openingBalance, goalDate) " +
                "VALUES ('Easypaisa Savings', 'SAVINGS', 0, $easypaisaGoal)"
        )
        db.execSQL("INSERT INTO accounts (name, type, openingBalance) VALUES ('Mashreq Savings', 'SAVINGS', 0)")

        listOf("Salary", "Freelance", "Gift", "Other income").forEach {
            db.execSQL("INSERT INTO categories (name, kind) VALUES ('$it', 'INCOME')")
        }
        listOf("Food", "Transport", "Bills", "Health", "Family", "Shopping", "Ayashi", "Other expense").forEach {
            db.execSQL("INSERT INTO categories (name, kind) VALUES ('$it', 'EXPENSE')")
        }
    }
}
