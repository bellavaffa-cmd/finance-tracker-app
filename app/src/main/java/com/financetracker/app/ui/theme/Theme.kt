package com.financetracker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val IncomeGreen = Color(0xFF2BB673)
val ExpenseRed = Color(0xFFE5484D)
val UncategorisedGrey = Color(0xFF9AA3AE)

private val Light = lightColorScheme(
    primary = Color(0xFF2F6FE0),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FAEFF),
    background = Color(0xFF0F1218),
    surface = Color(0xFF0F1218),
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
