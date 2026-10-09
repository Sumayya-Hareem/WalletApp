package com.sumayya.wallet.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Saves everything to a single JSON file and restores it again, so the data can survive
 * a lost phone or a reinstall. Also exports the entries as a CSV for Excel / Google Sheets.
 */
class BackupManager(private val db: WalletDatabase) {
    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val debts = db.debtDao()

    // ---------- JSON backup ----------

    suspend fun buildBackupJson(): String {
        val root = JSONObject()
        root.put("app", "wallet")
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("accounts", JSONArray().apply {
            accounts.getAll().forEach { a ->
                put(
                    JSONObject()
                        .put("id", a.id)
                        .put("name", a.name)
                        .put("type", a.type.name)
                        .put("openingBalance", a.openingBalance)
                        .put("goalAmount", a.goalAmount ?: JSONObject.NULL)
                        .put("goalDate", a.goalDate ?: JSONObject.NULL)
                )
            }
        })
        root.put("categories", JSONArray().apply {
            categories.getAll().forEach { c ->
                put(JSONObject().put("id", c.id).put("name", c.name).put("kind", c.kind.name))
            }
        })
        root.put("transactions", JSONArray().apply {
            transactions.getAll().forEach { t ->
                put(
                    JSONObject()
                        .put("id", t.id)
                        .put("type", t.type.name)
                        .put("amount", t.amount)
                        .put("date", t.date)
                        .put("note", t.note)
                        .put("categoryId", t.categoryId ?: JSONObject.NULL)
                        .put("accountId", t.accountId)
                        .put("toAccountId", t.toAccountId ?: JSONObject.NULL)
                )
            }
        })
        root.put("debts", JSONArray().apply {
            debts.getAllDebts().forEach { d ->
                put(
                    JSONObject()
                        .put("id", d.id)
                        .put("personName", d.personName)
                        .put("totalAmount", d.totalAmount)
                        .put("note", d.note)
                        .put("createdAt", d.createdAt)
                        .put("direction", d.direction.name)
                        .put("receivedAccountId", d.receivedAccountId ?: JSONObject.NULL)
                )
            }
        })
        root.put("debtPayments", JSONArray().apply {
            debts.getAllPayments().forEach { p ->
                put(
                    JSONObject()
                        .put("id", p.id)
                        .put("debtId", p.debtId)
                        .put("amount", p.amount)
                        .put("date", p.date)
                        .put("note", p.note)
                        .put("accountId", p.accountId)
                )
            }
        })
        return root.toString(2)
    }

    /**
     * Replaces ALL current data with the backup. The whole file is parsed first, so a bad or
     * foreign file throws before anything is deleted.
     */
    suspend fun restoreFromJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "wallet") { "This is not a Wallet backup file." }
        require(root.optInt("formatVersion", 0) in 1..FORMAT_VERSION) {
            "This backup was made by a newer version of the app."
        }

        val accountList = root.getJSONArray("accounts").objects().map { o ->
            Account(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = AccountType.valueOf(o.getString("type")),
                openingBalance = o.getLong("openingBalance"),
                goalAmount = o.longOrNull("goalAmount"),
                goalDate = o.longOrNull("goalDate")
            )
        }
        val categoryList = root.getJSONArray("categories").objects().map { o ->
            Category(
                id = o.getLong("id"),
                name = o.getString("name"),
                kind = CategoryKind.valueOf(o.getString("kind"))
            )
        }
        val transactionList = root.getJSONArray("transactions").objects().map { o ->
            WalletTransaction(
                id = o.getLong("id"),
                type = TxType.valueOf(o.getString("type")),
                amount = o.getLong("amount"),
                date = o.getLong("date"),
                note = o.optString("note", ""),
                categoryId = o.longOrNull("categoryId"),
                accountId = o.getLong("accountId"),
                toAccountId = o.longOrNull("toAccountId")
            )
        }
        val debtList = root.getJSONArray("debts").objects().map { o ->
            Debt(
                id = o.getLong("id"),
                personName = o.getString("personName"),
                totalAmount = o.getLong("totalAmount"),
                note = o.optString("note", ""),
                createdAt = o.getLong("createdAt"),
                direction = DebtDirection.valueOf(o.optString("direction", DebtDirection.OWED_TO_ME.name)),
                receivedAccountId = o.longOrNull("receivedAccountId")
            )
        }
        val paymentList = root.getJSONArray("debtPayments").objects().map { o ->
            DebtPayment(
                id = o.getLong("id"),
                debtId = o.getLong("debtId"),
                amount = o.getLong("amount"),
                date = o.getLong("date"),
                note = o.optString("note", ""),
                accountId = o.getLong("accountId")
            )
        }
        require(accountList.any { it.type == AccountType.AVAILABLE }) {
            "The backup has no Available account, so it can't be restored."
        }

        db.withTransaction {
            debts.deleteAllPayments()
            debts.deleteAllDebts()
            transactions.deleteAll()
            categories.deleteAll()
            accounts.deleteAll()

            accounts.insertAll(accountList)
            categories.insertAll(categoryList)
            debts.insertDebts(debtList)
            debts.insertPayments(paymentList)
            transactions.insertAll(transactionList)
        }
    }

    // ---------- CSV (for viewing in Excel / Sheets) ----------

    suspend fun buildTransactionsCsv(): String {
        val accountNames = accounts.getAll().associate { it.id to it.name }
        val categoryNames = categories.getAll().associate { it.id to it.name }
        val zone = ZoneId.systemDefault()

        val sb = StringBuilder("﻿") // BOM so Excel reads UTF-8 correctly
        sb.append("Date,Type,Amount (Rs),Section,Account,To account,Note\n")
        transactions.getAll().sortedBy { it.date }.forEach { t ->
            val date = Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate().toString()
            sb.append(date).append(',')
                .append(t.type.name.lowercase()).append(',')
                .append(t.amount).append(',')
                .append(csv(t.categoryId?.let { categoryNames[it] } ?: "")).append(',')
                .append(csv(accountNames[t.accountId] ?: "")).append(',')
                .append(csv(t.toAccountId?.let { accountNames[it] } ?: "")).append(',')
                .append(csv(t.note)).append('\n')
        }
        return sb.toString()
    }

    // ---------- File helpers ----------

    suspend fun writeText(resolver: ContentResolver, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = resolver.openOutputStream(uri, "wt") ?: error("Could not open the file for writing.")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun readText(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: error("Could not open the file.")
        input.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    companion object {
        const val FORMAT_VERSION = 1
    }
}
