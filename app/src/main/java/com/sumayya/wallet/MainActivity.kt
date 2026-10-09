package com.sumayya.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumayya.wallet.data.TxType
import com.sumayya.wallet.data.WalletTransaction
import com.sumayya.wallet.ui.AddTransactionDialog
import com.sumayya.wallet.ui.DashboardScreen
import com.sumayya.wallet.ui.HistoryScreen
import com.sumayya.wallet.ui.DebtsScreen
import com.sumayya.wallet.ui.AccountsScreen
import com.sumayya.wallet.ui.SectionsScreen
import com.sumayya.wallet.ui.WalletTheme
import com.sumayya.wallet.ui.WalletViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as WalletApplication).repository
        setContent {
            WalletTheme {
                val vm: WalletViewModel = viewModel(factory = WalletViewModel.Factory(repository))
                WalletApp(vm)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    HISTORY("History", Icons.Filled.Receipt),
    ACCOUNTS("Accounts", Icons.Filled.Savings),
    DEBTS("Debts", Icons.Filled.People),
    MORE("More", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletApp(vm: WalletViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    // Non-null while the add-entry dialog is open; holds the type it opens on.
    var addDialogType by remember { mutableStateOf<TxType?>(null) }
    // Non-null while an existing entry is being edited.
    var editingTx by remember { mutableStateOf<WalletTransaction?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.HISTORY) {
                FloatingActionButton(onClick = { addDialogType = TxType.EXPENSE }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add entry")
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.HOME -> DashboardScreen(vm, padding)
            Tab.HISTORY -> HistoryScreen(vm, padding, onEdit = { editingTx = it })
            Tab.ACCOUNTS -> AccountsScreen(vm, padding, onMoveMoney = { addDialogType = TxType.TRANSFER })
            Tab.DEBTS -> DebtsScreen(vm, padding)
            Tab.MORE -> SectionsScreen(vm, padding)
        }
    }

    addDialogType?.let { initial ->
        AddTransactionDialog(
            initialType = initial,
            vm = vm,
            onDismiss = { addDialogType = null }
        )
    }

    editingTx?.let { tx ->
        AddTransactionDialog(
            initialType = tx.type,
            vm = vm,
            editing = tx,
            onDismiss = { editingTx = null }
        )
    }
}
