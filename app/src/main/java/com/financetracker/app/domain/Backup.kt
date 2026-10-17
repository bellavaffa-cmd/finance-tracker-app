package com.financetracker.app.domain

import com.financetracker.app.data.Account
import com.financetracker.app.data.AccountType
import com.financetracker.app.data.Budget
import com.financetracker.app.data.Category
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.data.Txn
import com.financetracker.app.data.TxnType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class Snapshot(
    val currency: String,
    val accounts: List<Account>,
    val categories: List<Category>,
    val txns: List<Txn>,
    val budgets: List<Budget>,
)

/** Full JSON backup. A restore replaces the ledger; [decode] validates everything first. */
object Backup {

    const val FORMAT = "finance-tracker-backup"
    const val VERSION = 1

    fun encode(s: Snapshot): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("currency", s.currency)
        .put("accounts", JSONArray(s.accounts.map {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name)
                .put("openingBalance", it.openingBalance).put("archived", it.archived)
        }))
        .put("categories", JSONArray(s.categories.map {
            JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind.name).put("color", it.color)
        }))
        .put("transactions", JSONArray(s.txns.map {
            JSONObject().put("id", it.id).put("type", it.type.name).put("amount", it.amount)
                .put("accountId", it.accountId).put("toAccountId", it.toAccountId ?: JSONObject.NULL)
                .put("categoryId", it.categoryId ?: JSONObject.NULL).put("date", it.date)
                .put("payee", it.payee).put("note", it.note)
        }))
        .put("budgets", JSONArray(s.budgets.map {
            JSONObject().put("categoryId", it.categoryId).put("monthlyLimit", it.monthlyLimit)
        }))
        .toString(2)

    /** @throws IllegalArgumentException with a readable reason if the file is not a usable backup. */
    fun decode(text: String): Snapshot {
        val root = try { JSONObject(text) } catch (e: JSONException) {
            throw IllegalArgumentException("This is not a backup file.")
        }
        require(root.optString("format") == FORMAT) { "This is not a Finance Tracker 2 backup." }
        require(root.optInt("version") in 1..VERSION) { "This backup was made by a newer version of the app." }
        try {
            val currency = root.getString("currency")
            require(Money.isValidCurrency(currency)) { "The backup has an unknown currency \"$currency\"." }
            val accounts = root.getJSONArray("accounts").objects().map {
                Account(it.getLong("id"), it.getString("name"), AccountType.valueOf(it.getString("type")),
                    it.getLong("openingBalance"), it.optBoolean("archived"))
            }
            val categories = root.getJSONArray("categories").objects().map {
                Category(it.getLong("id"), it.getString("name"), CategoryKind.valueOf(it.getString("kind")), it.getInt("color"))
            }
            val txns = root.getJSONArray("transactions").objects().map {
                Txn(
                    id = it.getLong("id"), type = TxnType.valueOf(it.getString("type")), amount = it.getLong("amount"),
                    accountId = it.getLong("accountId"),
                    toAccountId = if (it.isNull("toAccountId")) null else it.getLong("toAccountId"),
                    categoryId = if (it.isNull("categoryId")) null else it.getLong("categoryId"),
                    date = it.getLong("date"), payee = it.optString("payee"), note = it.optString("note"),
                )
            }
            val budgets = root.getJSONArray("budgets").objects().map {
                Budget(it.getLong("categoryId"), it.getLong("monthlyLimit"))
            }
            val accountIds = accounts.map { it.id }.toSet()
            val categoryIds = categories.map { it.id }.toSet()
            require(accountIds.size == accounts.size && categoryIds.size == categories.size) { "The backup has duplicate ids." }
            for (t in txns) {
                require(t.amount > 0) { "Transaction ${t.id} has a non-positive amount." }
                require(t.accountId in accountIds) { "Transaction ${t.id} points at a missing account." }
                require(t.toAccountId == null || t.toAccountId in accountIds) { "Transaction ${t.id} points at a missing account." }
                require((t.type == TxnType.TRANSFER) == (t.toAccountId != null)) { "Transaction ${t.id} is a malformed transfer." }
                require(t.categoryId == null || t.categoryId in categoryIds) { "Transaction ${t.id} points at a missing category." }
            }
            for (b in budgets) require(b.categoryId in categoryIds) { "A budget points at a missing category." }
            return Snapshot(currency, accounts, categories, txns, budgets)
        } catch (e: JSONException) {
            throw IllegalArgumentException("The backup is incomplete or damaged: ${e.message}")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
