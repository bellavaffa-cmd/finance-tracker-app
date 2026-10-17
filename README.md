# Finance Tracker 2

A personal finance tracker for Android, rebuilt from scratch. Local-first, offline, no account.

Native Kotlin + Jetpack Compose (Material 3), Room for storage. The app declares **no permissions
at all**: there is no network access, and files are read and written through the system file
picker, so no storage permission is needed either.

It keeps the 1.x application id, `com.financetracker.app`, and replaces 1.x. The 2.0 database is
new, so 1.x data does not carry over by itself: export a CSV from 1.x first and import it into 2.0.
The importer reads the 1.x export directly, taking the base-currency amount from each row.

## Features

**Ledger**
- Accounts (cash, bank, credit card, savings) with an opening balance; archive an account you no
  longer use without losing its history
- Expenses, income, and transfers between your own accounts
- Editable categories with colours; a default set is created on first launch
- Monthly ledger with search across payee, note, category and account, grouped by day
- Typing a payee you have used before fills in the category you used last time

**Budgets**
- A monthly limit per spending category, with progress bars that turn amber at 80% and red when over
- Totals across all budgeted categories; the home screen flags any budget at 80% or more

**Reports**
- Donut chart of spending or income by category for any month, with share of total
- Income vs spending for the last six months, plus each month's net

**Import / export**
- CSV export of the whole ledger
- CSV import of that same format, or of a typical bank export (`Date`, `Description`, signed
  `Amount`). Missing accounts and categories are created. Rows already in the ledger are skipped,
  and rows that can't be read are listed by line number with a reason
- Full JSON backup, and restore from one

## Design notes

- **Money is a `Long` count of minor units, never floating point.** The number of minor digits
  comes from the currency (EUR 2, JPY 0, KWD 3).
- **Transfers are excluded from every income and expense total.** Moving your own money between your
  own accounts is not spending.
- **Amounts are parsed in either decimal convention** (`1,234.56` and `1.234,56`), and a value with
  more decimals than the currency allows is rejected rather than rounded.
- **CSV dates are read with one rule for the whole file.** `03/04/2026` cannot be read differently
  on two lines; if every date is ambiguous the import says how it read them.
- **Duplicates are counted, not just matched.** Two identical coffees on the same day both import
  unless the ledger already has both.
- **Deletes don't destroy history.** Deleting a category leaves its transactions uncategorised; an
  account with transactions can only be archived.
- **Restore replaces, it does not merge, and it is validated first.** A backup with dangling
  references is rejected before anything is touched, and the replacement runs in one database
  transaction.
- **Changing currency relabels, it never converts.** A switch to a currency with a different number
  of decimals is blocked once there are transactions, since every stored amount would be misread.

## Layout

```
app/src/main/java/com/financetracker/app/
  data/     Room entities, DAO, database (with first-launch seed), repository
  domain/   Plain Kotlin: money, CSV, ledger maths, backup codec. No Android imports
  ui/       ViewModel, theme, shared components, one file per screen in ui/screens
app/src/test/  JVM unit tests for the domain layer
```

## Building

Requires JDK 17 and the Android SDK.

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # domain tests
```

This is a personal build with no release signing key, so use `assembleDebug`.

- minSdk 26, targetSdk 34, compileSdk 35
- AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00, Room 2.6.1
