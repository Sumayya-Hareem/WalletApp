# Wallet (personal money tracker), version 0.2

Offline Android app: Kotlin, Jetpack Compose, Room. No accounts, no internet.

## Updating from version 0.1
Replace your project files with these and press Run. The app installs over the old one and your
data is kept (the database upgrades itself). Do not uninstall the app first. If Gradle asks, choose
**Sync Now**.

## Tabs
- **Home:** Available money, savings total, and week / month / year / lifetime stats.
- **History:** every entry. Tap one to edit it, or use the bin icon to delete it.
- **Accounts:** Available money, Easypaisa, Mashreq (and any new savings account). Use
  **Edit** to set a starting balance for money you already had, plus a goal and end date.
- **Debts:** switch between **Owed to me** (your brother) and **I owe** (money you borrowed).
- **More:** backup and restore, and your income sources and expense sections.

## How the money model works
- **Starting balance:** money you already had before using the app. It counts toward the balance but is
  not income and does not show in "moved to savings".
- **Income:** lands in Available money, tagged with an income source.
- **Expense:** tagged with a section, an optional "What was it for?" note, and the account it was paid from.
- **Transfer:** Available <-> savings. Not an expense.
- **Owed to me:** repayments go into Available money but are never counted as income.
- **I owe:** borrowed money can be added to Available money when you record it (untick if you already
  spent it). Payments you make come out of Available money but are never counted as expenses.
- **Backup:** More tab -> Save backup file. Restore replaces everything with the file's contents.
  "Export entries as CSV" makes a file you can open in Excel or Google Sheets (it is for viewing only).

Amounts are whole rupees. The week starts on Monday.
