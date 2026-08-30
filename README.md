# Finance Tracker

A personal finance tracker for Android. Local-first, offline, no account.

Native Kotlin + Jetpack Compose, Room for storage. The app declares **no internet permission at
all** — there is no server, no sync, and no telemetry. Everything lives in a database on the device.

## Features

**Ledger**
- Accounts: cash, bank, credit card, savings, investment — each with its own currency, opening
  balance and colour
- Expenses, income, and transfers between your own accounts
- Two-level categories, fully editable, with a usable default set on first launch
- Multi-currency, including cross-currency transfers

**Budgets**
- A monthly limit per category, plus an optional cap on total spending
- Optional rollover: an underspent month raises next month's allowance, an overspent one lowers it
- The budgeting month boundary is configurable (1st–28th), so budgets can line up with a pay cycle
  rather than the calendar

**Recurring**
- Rent, salary, subscriptions — daily, weekly, monthly or yearly, every N periods
- Either posts itself on schedule or waits on the dashboard for you to confirm
- Catches up occurrence by occurrence if the app has not been opened for a while
- Shows what you are committed to each month, normalised across mixed frequencies

**Reports**
- Donut chart of spending (or income) by category for a month, with subcategories rolled up into
  their parent
- Month-on-month comparison
- Per-category breakdown with share of total

**Splits and tags**
- Split one payment across several categories, with a running remainder so it is always clear how
  much is still unassigned — a split that does not add up cannot be saved
- Cross-cutting tags: one holiday's total across flights, food and hotels, or everything to claim
  back. Filter the ledger by tag, and see per-tag totals in reports

**Trends**
- Net worth over the last 12 months, reconstructed from the ledger rather than from stored
  snapshots, so it works over data entered before the feature existed
- Income against spending, month by month, on one shared scale
- "Worth a look": categories sitting well away from their own recent average

**Goals and debts**
- Savings goals attached to an account, so progress is the real balance and can never drift from
  it. With a deadline, each goal shows what must go in per month to arrive on time
- Debts with rate and minimum payment, showing how long each takes on the minimum alone
- Snowball vs avalanche compared side by side on your actual numbers, including what the difference
  costs in interest and months

**Auto-categorise**
- Rules that fill in the category when a payee matches: contains / starts with / is exactly
- Applied as you enter a transaction, on every row of a CSV import, and retroactively to entries
  that have no category yet
- Can also tidy a messy bank description into one consistent payee name
- Suggests rules from payees you have already filed the same way several times

**Data & security**
- Full JSON backup, and restore from one — everything, including preferences
- CSV export of the ledger for a spreadsheet, with amounts in both the account's own currency and
  the base currency
- CSV **import** from a spreadsheet or bank export: pick the file, match its columns, see a
  preview, then commit. Handles either decimal convention, comma/semicolon/tab delimiters,
  quoted fields, and skips rows that already exist
- Optional app lock using the device keyguard: fingerprint, face, PIN, pattern or password
- Files are written through the Storage Access Framework, so you choose where they go and the app
  needs no storage permission

**Entry**
The screen the app lives or dies on. Amounts are typed on a keypad in minor units — tapping
`2 3 5 0` gives `€23.50`, so there is no decimal point to fight. Account, date and exchange rate
arrive pre-filled, and typing a payee recalls the category you last filed it under.

## Design notes

A few decisions that are load-bearing:

- **Money is stored as a `Long` count of minor units, never a floating-point number.** The number of
  minor digits comes from the currency (EUR 2, JPY 0, KWD 3), never a hardcoded 100.
- **Exchange rates are frozen onto each transaction at entry and never recalculated.** Last year's
  reports do not shift when this year's rate moves. A separate table holds current rates, used only
  for valuing net worth and pre-filling new entries. Rates are entered by hand — nothing is fetched.
- **Transfers are excluded from every income and expense total.** Moving your own money between your
  own accounts is not spending, and counting it is the standard way a homemade tracker ends up
  reporting double the expenses you actually had.
- **Deletes are soft.** Removing a category un-categorises its transactions and promotes its
  subcategories rather than destroying history; an account that has transactions can only be
  archived.
- **Restoring replaces, it does not merge.** Merging two ledgers safely needs a stable identity for
  each transaction across devices; without one, a merge would silently duplicate or drop entries —
  the two failure modes a finance app can least afford. The whole restore runs in one database
  transaction, so a failure part-way leaves the existing data untouched.
- **The app never stores a PIN of its own.** The lock delegates to the OS keyguard through
  BiometricPrompt, so the secret never reaches this process.
- **Rules are plain matches, not regular expressions.** A regex that backtracks badly can hang
  the entry screen and a wrong one fails invisibly; three match types cover essentially every
  merchant string without either risk. The first match wins, ordered by priority and then by
  pattern length, so a specific rule is never shadowed by a vaguer one written later.
- **A rule never overwrites a decision you made.** It only fills a category that is empty -
  on entry, on import, and in the retroactive pass. A category named in a CSV always beats a
  rule, because that is your own statement about the row.
- **The CSV importer never guesses silently.** Where a date could be read either way round it
  says so before anything is written; rows it cannot read are listed by line number with a
  reason rather than dropped; and transfers are reported as unimportable, because one CSV row
  cannot say what a transfer did to both accounts and guessing would invent money.
- **Net worth history is derived, never snapshotted.** Balance movements are replayed against each
  account's opening balance, so the newest point is always identical to the net worth on the
  dashboard and no background job can let the two drift apart. Past values are stated at today's
  exchange rates, because only per-transaction historical rates are kept.
- **An anomaly is measured against the category's own history, starting at its first activity.**
  Averaging in months before a category was ever used drags its norm down far enough to flag
  perfectly steady spending, which would fire on every category at once for a new install.
- **Debt amortisation runs in whole minor units, not floating point.** Interest is rounded to the
  nearest unit each month, the way a lender actually charges it; simulating in floating point and
  rounding at the end drifts enough to move the payoff date by a month.
- **A split transaction contributes its legs to reports, never itself.** Category totals come from a
  union that excludes any transaction which has splits, so a split payment cannot be counted twice.
  The parent carries no category of its own, and the legs must sum to it exactly.

## Building

Requires JDK 17 and the Android SDK.

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

For a signed release build, create `keystore.properties` in the project root with `storeFile`,
`storePassword`, `keyAlias` and `keyPassword`. It is gitignored, and the release signing config is
skipped when it is absent.

- minSdk 26, targetSdk 34, compileSdk 35
- AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00, Room 2.6.1
