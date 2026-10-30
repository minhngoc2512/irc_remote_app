package com.minhngoc.irremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.minhngoc.irremote.data.Brand
import com.minhngoc.irremote.data.Category
import com.minhngoc.irremote.data.IrDatabase
import com.minhngoc.irremote.data.UserDevice
import com.minhngoc.irremote.data.UserDevices
import com.minhngoc.irremote.ir.IrTransmitter
import com.minhngoc.irremote.ui.remote.RemoteScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Categories : Screen
    data class Brands(val category: Category) : Screen
    data class Match(val category: Category, val brand: Brand) : Screen
    data class Remote(val device: UserDevice) : Screen
    data object About : Screen
}

class AppDeps(val db: IrDatabase, val devices: UserDevices, val ir: IrTransmitter)

val LocalDeps: ProvidableCompositionLocal<AppDeps> = staticCompositionLocalOf { error("AppDeps not provided") }

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun resetTo(vararg screens: Screen) {
        stack.clear()
        stack.addAll(screens)
    }
}

/** Runs [block] on the IO dispatcher whenever [keys] change; null while loading. */
@Composable
fun <T> loadAsync(vararg keys: Any?, block: suspend () -> T): State<T?> =
    produceState<T?>(initialValue = null, *keys) { value = withContext(Dispatchers.IO) { block() } }

@Composable
fun IrApp() {
    val context = LocalContext.current
    val deps = remember {
        AppDeps(IrDatabase.get(context), UserDevices.get(context), IrTransmitter(context))
    }
    val nav = remember { Navigator() }
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    CompositionLocalProvider(LocalDeps provides deps) {
        when (val screen = nav.stack.last()) {
            Screen.Home -> HomeScreen(nav)
            Screen.Categories -> CategoryScreen(nav)
            is Screen.Brands -> BrandScreen(nav, screen.category)
            is Screen.Match -> MatchScreen(nav, screen.category, screen.brand)
            is Screen.Remote -> RemoteScreen(nav, screen.device)
            Screen.About -> AboutScreen(nav)
        }
    }
}
