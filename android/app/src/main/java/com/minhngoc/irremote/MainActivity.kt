package com.minhngoc.irremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.minhngoc.irremote.ui.IrApp
import com.minhngoc.irremote.ui.theme.IrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IrTheme { IrApp() }
        }
    }
}
