package com.sumayya.wallet

import android.app.Application
import com.sumayya.wallet.data.WalletDatabase
import com.sumayya.wallet.data.WalletRepository

class WalletApplication : Application() {
    val repository: WalletRepository by lazy { WalletRepository(WalletDatabase.get(this)) }
}
