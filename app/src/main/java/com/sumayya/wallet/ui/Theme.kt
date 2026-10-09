package com.sumayya.wallet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00796B)
private val TealLight = Color(0xFF4DB6AC)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Color(0xFF546E7A),
    tertiary = Color(0xFFF9A825)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    secondary = Color(0xFF90A4AE),
    tertiary = Color(0xFFFFCA28)
)

val IncomeGreen = Color(0xFF2E7D32)
val ExpenseRed = Color(0xFFC62828)
val TransferBlue = Color(0xFF1565C0)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
