package com.financetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.financetracker.app.ui.nav.FinanceNavHost
import com.financetracker.app.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FinanceApplication

        setContent {
            FinanceTheme {
                FinanceNavHost(application = app)
            }
        }
    }
}
