package com.minhngoc.irremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.minhngoc.irremote.data.IrDatabase
import com.minhngoc.irremote.data.UserDevices
import com.minhngoc.irremote.ir.IrTransmitter
import com.minhngoc.irremote.ui.AppDeps
import com.minhngoc.irremote.ui.IrApp
import com.minhngoc.irremote.ui.theme.IrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var deps by mutableStateOf<AppDeps?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The first launch after an install copies the ~60 MB code database out of
        // the APK; keep the splash up instead of freezing a blank window.
        splash.setKeepOnScreenCondition { deps == null }
        lifecycleScope.launch {
            deps = withContext(Dispatchers.IO) {
                AppDeps(IrDatabase.get(application), UserDevices.get(application), IrTransmitter(application))
            }
        }
        setContent {
            IrTheme { deps?.let { IrApp(it) } }
        }
    }
}
