package com.financetracker.app.data.backup

import com.financetracker.app.data.account.Account
import com.financetracker.app.data.account.AccountType
import com.financetracker.app.data.budget.Budget
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.data.debt.Debt
import com.financetracker.app.data.debt.DebtKind
import com.financetracker.app.data.goal.Goal
import com.financetracker.app.data.recurring.Frequency
import com.financetracker.app.data.recurring.RecurringRule
import com.financetracker.app.data.rules.MatchType
import com.financetracker.app.data.rules.PayeeRule
import com.financetracker.app.data.settings.CurrencyRate
import com.financetracker.app.data.settings.SettingsSnapshot
import com.financetracker.app.data.tag.Tag
import com.financetracker.app.data.tag.TxnTag
import com.financetracker.app.data.txn.TxnSplit
import com.financetracker.app.data.txn.Transaction
import com.financetracker.app.data.txn.TxnType
import org.json.JSONArray
import org.json.JSONObject

/** Everything needed to rebuild the app's state from nothing. */
data class BackupPayload(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val rules: List<RecurringRule>,
    val rates: List<CurrencyRate>,
    val tags: List<Tag>,
    val tagLinks: List<TxnTag>,
    val splits: List<TxnSplit>,
    val goals: List<Goal>,
    val debts: List<Debt>,
    val payeeRules: List<PayeeRule>,
    val settings: SettingsSnapshot
) {
    val transactionCount: Int get() = transactions.count { it.deletedAtMillis == null }
}

class BackupFormatException(message: String) : Exception(message)

/**
 * Reads and writes the JSON backup file.
 *
 * Written by hand against `org.json` rather than through a serialization library, for two reasons:
 * the file format is a compatibility contract that should not silently change when a data class is
 * refactored, and rows are decoded field by field with explicit defaults so a backup taken by an
 * older version still restores after new columns are added.
 *
 * Primary keys are preserved, because transactions reference accounts and categories by id - a
 * restore that let Room reassign ids would scramble every relationship in the file.
 */
object BackupCodec {

    const val FORMAT_VERSION = 5
    private const val KEY_FORMAT = "formatVersion"

    fun encode(payload: BackupPayload, appVersion: String, nowMillis: Long): String {
        val root = JSONObject()
        root.put(KEY_FORMAT, FORMAT_VERSION)
        root.put("app", "Finance Tracker")
        root.put("appVersion", appVersion)
        root.put("exportedAtMillis", nowMillis)

        root.put("settings", JSONObject().apply {
            put("baseCurrency", payload.settings.baseCurrency)
            put("monthStartDay", payload.settings.monthStartDay)
            put("hideBalances", payload.settings.hideBalances)
            put("appLockEnabled", payload.settings.appLockEnabled)
        })

        root.put("accounts", payload.accounts.jsonArray { account ->
            JSONObject().apply {
                put("id", account.id)
                put("name", account.name)
                put("type", account.type.name)
                put("currencyCode", account.currencyCode)
                put("openingBalanceMinor", account.openingBalanceMinor)
                put("colorArgb", account.colorArgb)
                put("includeInNetWorth", account.includeInNetWorth)
                put("isArchived", account.isArchived)
                put("sortOrder", account.sortOrder)
            }
        })

        root.put("categories", payload.categories.jsonArray { category ->
            JSONObject().apply {
                put("id", category.id)
                put("name", category.name)
                put("kind", category.kind.name)
                putOrNull("parentId", category.parentId)
                put("iconKey", category.iconKey)
                put("colorArgb", category.colorArgb)
                put("sortOrder", category.sortOrder)
                put("isArchived", category.isArchived)
            }
        })

        root.put("transactions", payload.transactions.jsonArray { txn ->
            JSONObject().apply {
                put("id", txn.id)
                put("type", txn.type.name)
                put("dateMillis", txn.dateMillis)
                put("accountId", txn.accountId)
                putOrNull("toAccountId", txn.toAccountId)
                putOrNull("categoryId", txn.categoryId)
                put("amountMinor", txn.amountMinor)
                putOrNull("toAmountMinor", txn.toAmountMinor)
                put("fxRateToBase", txn.fxRateToBase)
                put("payee", txn.payee)
                put("note", txn.note)
                putOrNull("recurringRuleId", txn.recurringRuleId)
                put("createdAtMillis", txn.createdAtMillis)
                // The file name only. Embedding the images would turn a small text backup into
                // hundreds of megabytes of base64.
                put("attachmentName", txn.attachmentName ?: JSONObject.NULL)
                putOrNull("deletedAtMillis", txn.deletedAtMillis)
            }
        })

        root.put("budgets", payload.budgets.jsonArray { budget ->
            JSONObject().apply {
                put("id", budget.id)
                putOrNull("categoryId", budget.categoryId)
                put("amountMinorBase", budget.amountMinorBase)
                put("rollover", budget.rollover)
                put("startMonthMillis", budget.startMonthMillis)
                put("isActive", budget.isActive)
            }
        })

        root.put("recurringRules", payload.rules.jsonArray { rule ->
            JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("type", rule.type.name)
                put("accountId", rule.accountId)
                putOrNull("toAccountId", rule.toAccountId)
                putOrNull("categoryId", rule.categoryId)
                put("amountMinor", rule.amountMinor)
                putOrNull("toAmountMinor", rule.toAmountMinor)
                put("payee", rule.payee)
                put("note", rule.note)
                put("frequency", rule.frequency.name)
                put("interval", rule.interval)
                putOrNull("anchorDayOfMonth", rule.anchorDayOfMonth)
                put("nextDueMillis", rule.nextDueMillis)
                putOrNull("endDateMillis", rule.endDateMillis)
                put("autoPost", rule.autoPost)
                put("isActive", rule.isActive)
                putOrNull("lastPostedMillis", rule.lastPostedMillis)
            }
        })

        root.put("currencyRates", payload.rates.jsonArray { rate ->
            JSONObject().apply {
                put("code", rate.code)
                put("rateToBase", rate.rateToBase)
                put("updatedAtMillis", rate.updatedAtMillis)
            }
        })

        root.put("tags", payload.tags.jsonArray { tag ->
            JSONObject().apply {
                put("id", tag.id)
                put("name", tag.name)
                put("colorArgb", tag.colorArgb)
            }
        })

        root.put("tagLinks", payload.tagLinks.jsonArray { link ->
            JSONObject().apply {
                put("txnId", link.txnId)
                put("tagId", link.tagId)
            }
        })

        root.put("splits", payload.splits.jsonArray { split ->
            JSONObject().apply {
                put("id", split.id)
                put("txnId", split.txnId)
                putOrNull("categoryId", split.categoryId)
                put("amountMinor", split.amountMinor)
                put("note", split.note)
            }
        })

        root.put("goals", payload.goals.jsonArray { goal ->
            JSONObject().apply {
                put("id", goal.id)
                put("name", goal.name)
                put("accountId", goal.accountId)
                put("targetMinor", goal.targetMinor)
                put("startingBalanceMinor", goal.startingBalanceMinor)
                putOrNull("targetDateMillis", goal.targetDateMillis)
                put("colorArgb", goal.colorArgb)
                put("note", goal.note)
                put("isArchived", goal.isArchived)
                put("createdAtMillis", goal.createdAtMillis)
            }
        })

        root.put("debts", payload.debts.jsonArray { debt ->
            JSONObject().apply {
                put("id", debt.id)
                put("name", debt.name)
                put("kind", debt.kind.name)
                put("balanceMinor", debt.balanceMinor)
                put("currencyCode", debt.currencyCode)
                put("annualRatePercent", debt.annualRatePercent)
                put("minimumPaymentMinor", debt.minimumPaymentMinor)
                put("colorArgb", debt.colorArgb)
                put("note", debt.note)
                put("isActive", debt.isActive)
                put("createdAtMillis", debt.createdAtMillis)
            }
        })

        root.put("payeeRules", payload.payeeRules.jsonArray { rule ->
            JSONObject().apply {
                put("id", rule.id)
                put("pattern", rule.pattern)
                put("matchType", rule.matchType.name)
                putOrNull("categoryId", rule.categoryId)
                putOrNull("accountId", rule.accountId)
                put("renameTo", rule.renameTo ?: JSONObject.NULL)
                put("priority", rule.priority)
                put("isActive", rule.isActive)
                put("createdAtMillis", rule.createdAtMillis)
            }
        })

        return root.toString(2)
    }

    fun decode(text: String): BackupPayload {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupFormatException("This file is not a Finance Tracker backup.")
        }

        val version = root.optInt(KEY_FORMAT, -1)
        if (version < 1) {
            throw BackupFormatException("This file is not a Finance Tracker backup.")
        }
        if (version > FORMAT_VERSION) {
            throw BackupFormatException(
                "This backup was written by a newer version of the app (format $version). " +
                    "Update Finance Tracker and try again."
            )
        }

        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val settings = SettingsSnapshot(
            baseCurrency = settingsJson.optString("baseCurrency", "EUR"),
            monthStartDay = settingsJson.optInt("monthStartDay", 1),
            hideBalances = settingsJson.optBoolean("hideBalances", false),
            appLockEnabled = settingsJson.optBoolean("appLockEnabled", false)
        )

        return BackupPayload(
            accounts = root.list("accounts") { o ->
                Account(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    type = enumOf(o.optString("type"), AccountType.BANK),
                    currencyCode = o.optString("currencyCode", "EUR"),
                    openingBalanceMinor = o.optLong("openingBalanceMinor", 0),
                    colorArgb = o.optInt("colorArgb", DEFAULT_COLOR),
                    includeInNetWorth = o.optBoolean("includeInNetWorth", true),
                    isArchived = o.optBoolean("isArchived", false),
                    sortOrder = o.optInt("sortOrder", 0)
                )
            },
            categories = root.list("categories") { o ->
                Category(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    kind = enumOf(o.optString("kind"), CategoryKind.EXPENSE),
                    parentId = o.longOrNull("parentId"),
                    iconKey = o.optString("iconKey", "category"),
                    colorArgb = o.optInt("colorArgb", DEFAULT_COLOR),
                    sortOrder = o.optInt("sortOrder", 0),
                    isArchived = o.optBoolean("isArchived", false)
                )
            },
            transactions = root.list("transactions") { o ->
                Transaction(
                    id = o.getLong("id"),
                    type = enumOf(o.optString("type"), TxnType.EXPENSE),
                    dateMillis = o.getLong("dateMillis"),
                    accountId = o.getLong("accountId"),
                    toAccountId = o.longOrNull("toAccountId"),
                    categoryId = o.longOrNull("categoryId"),
                    amountMinor = o.optLong("amountMinor", 0),
                    toAmountMinor = o.longOrNull("toAmountMinor"),
                    fxRateToBase = o.optDouble("fxRateToBase", 1.0),
                    payee = o.optString("payee", ""),
                    note = o.optString("note", ""),
                    recurringRuleId = o.longOrNull("recurringRuleId"),
                    createdAtMillis = o.optLong("createdAtMillis", o.getLong("dateMillis")),
                    attachmentName = if (o.isNull("attachmentName")) null
                    else o.optString("attachmentName").ifBlank { null },
                    deletedAtMillis = o.longOrNull("deletedAtMillis")
                )
            },
            budgets = root.list("budgets") { o ->
                Budget(
                    id = o.getLong("id"),
                    categoryId = o.longOrNull("categoryId"),
                    amountMinorBase = o.optLong("amountMinorBase", 0),
                    rollover = o.optBoolean("rollover", false),
                    startMonthMillis = o.optLong("startMonthMillis", 0),
                    isActive = o.optBoolean("isActive", true)
                )
            },
            rules = root.list("recurringRules") { o ->
                RecurringRule(
                    id = o.getLong("id"),
                    name = o.optString("name", ""),
                    type = enumOf(o.optString("type"), TxnType.EXPENSE),
                    accountId = o.getLong("accountId"),
                    toAccountId = o.longOrNull("toAccountId"),
                    categoryId = o.longOrNull("categoryId"),
                    amountMinor = o.optLong("amountMinor", 0),
                    toAmountMinor = o.longOrNull("toAmountMinor"),
                    payee = o.optString("payee", ""),
                    note = o.optString("note", ""),
                    frequency = enumOf(o.optString("frequency"), Frequency.MONTHLY),
                    interval = o.optInt("interval", 1),
                    anchorDayOfMonth = o.intOrNull("anchorDayOfMonth"),
                    nextDueMillis = o.optLong("nextDueMillis", 0),
                    endDateMillis = o.longOrNull("endDateMillis"),
                    autoPost = o.optBoolean("autoPost", true),
                    isActive = o.optBoolean("isActive", true),
                    lastPostedMillis = o.longOrNull("lastPostedMillis")
                )
            },
            rates = root.list("currencyRates") { o ->
                CurrencyRate(
                    code = o.getString("code"),
                    rateToBase = o.optDouble("rateToBase", 1.0),
                    updatedAtMillis = o.optLong("updatedAtMillis", 0)
                )
            },
            tags = root.list("tags") { o ->
                Tag(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    colorArgb = o.optInt("colorArgb", DEFAULT_COLOR)
                )
            },
            tagLinks = root.list("tagLinks") { o ->
                TxnTag(txnId = o.getLong("txnId"), tagId = o.getLong("tagId"))
            },
            splits = root.list("splits") { o ->
                TxnSplit(
                    id = o.getLong("id"),
                    txnId = o.getLong("txnId"),
                    categoryId = o.longOrNull("categoryId"),
                    amountMinor = o.optLong("amountMinor", 0),
                    note = o.optString("note", "")
                )
            },
            goals = root.list("goals") { o ->
                Goal(
                    id = o.getLong("id"),
                    name = o.optString("name", ""),
                    accountId = o.getLong("accountId"),
                    targetMinor = o.optLong("targetMinor", 0),
                    startingBalanceMinor = o.optLong("startingBalanceMinor", 0),
                    targetDateMillis = o.longOrNull("targetDateMillis"),
                    colorArgb = o.optInt("colorArgb", DEFAULT_COLOR),
                    note = o.optString("note", ""),
                    isArchived = o.optBoolean("isArchived", false),
                    createdAtMillis = o.optLong("createdAtMillis", 0)
                )
            },
            debts = root.list("debts") { o ->
                Debt(
                    id = o.getLong("id"),
                    name = o.optString("name", ""),
                    kind = enumOf(o.optString("kind"), DebtKind.OWED_BY_ME),
                    balanceMinor = o.optLong("balanceMinor", 0),
                    currencyCode = o.optString("currencyCode", "EUR"),
                    annualRatePercent = o.optDouble("annualRatePercent", 0.0),
                    minimumPaymentMinor = o.optLong("minimumPaymentMinor", 0),
                    colorArgb = o.optInt("colorArgb", DEFAULT_COLOR),
                    note = o.optString("note", ""),
                    isActive = o.optBoolean("isActive", true),
                    createdAtMillis = o.optLong("createdAtMillis", 0)
                )
            },
            payeeRules = root.list("payeeRules") { o ->
                PayeeRule(
                    id = o.getLong("id"),
                    pattern = o.optString("pattern", ""),
                    matchType = enumOf(o.optString("matchType"), MatchType.CONTAINS),
                    categoryId = o.longOrNull("categoryId"),
                    accountId = o.longOrNull("accountId"),
                    renameTo = if (o.isNull("renameTo")) null else o.optString("renameTo").ifBlank { null },
                    priority = o.optInt("priority", 0),
                    isActive = o.optBoolean("isActive", true),
                    createdAtMillis = o.optLong("createdAtMillis", 0)
                )
            },
            settings = settings
        )
    }

    private const val DEFAULT_COLOR = 0xFF6B7688.toInt()

    /** Unknown enum names fall back rather than throwing, so one bad row cannot fail a restore. */
    private inline fun <reified T : Enum<T>> enumOf(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    private fun <T> List<T>.jsonArray(map: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(map(it)) }
        return array
    }

    private fun <T> JSONObject.list(key: String, map: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            try {
                map(item)
            } catch (e: Exception) {
                // A single malformed row is skipped rather than aborting the whole restore -
                // recovering most of a damaged backup beats recovering none of it.
                null
            }
        }
    }

    private fun JSONObject.putOrNull(key: String, value: Long?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.putOrNull(key: String, value: Int?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key).takeIf { has(key) }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key).takeIf { has(key) }
}
