package com.minhngoc.irremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import android.view.KeyEvent
import com.minhngoc.irremote.atv.AtvRemote
import com.minhngoc.irremote.ui.remote.AcMemory
import com.minhngoc.irremote.ui.remote.acButtonFor
import com.minhngoc.irremote.ui.remote.acModeLabel
import com.minhngoc.irremote.ui.remote.acStateButtons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.Brand
import com.minhngoc.irremote.data.Category
import com.minhngoc.irremote.data.UserDevice
import com.minhngoc.irremote.ui.remote.AcState
import com.minhngoc.irremote.ui.remote.IrKey
import com.minhngoc.irremote.ui.remote.KeyFace
import com.minhngoc.irremote.ui.remote.prettyLabel
import com.minhngoc.irremote.ui.theme.DeviceStyle
import com.minhngoc.irremote.ui.theme.LocalPalette
import com.minhngoc.irremote.ui.theme.deviceStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val VI = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi"))

/** Screen frame: room-colored background, back arrow, title with an optional category badge. */
@Composable
fun AppScaffold(
    title: String,
    nav: Navigator?,
    subtitle: String? = null,
    style: DeviceStyle? = null,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val palette = LocalPalette.current
    Scaffold(
        containerColor = palette.room,
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                if (nav != null) {
                    IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                } else {
                    Spacer(Modifier.size(16.dp))
                }
                style?.let { CategoryBadge(it, 36.dp) }
                Column(Modifier.weight(1f).padding(start = if (style != null) 12.dp else 0.dp)) {
                    Text(title, style = if (nav == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSoft) }
                }
                actions()
            }
        },
        floatingActionButton = fab,
        content = content,
    )
}

@Composable
fun CategoryBadge(style: DeviceStyle, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(style.accent), contentAlignment = Alignment.Center) {
        Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun Loading(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun NoEmitterBanner() {
    if (LocalDeps.current.ir.available) return
    Text(
        "Điện thoại này không có cổng hồng ngoại. Bạn vẫn xem được bộ mã nhưng không phát được lệnh.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(14.dp),
    )
}

/** A light card on the room background, used for tiles and rows. */
private fun Modifier.tile(shape: RoundedCornerShape = RoundedCornerShape(26.dp)): Modifier = this
    .clip(shape)

// --- Home ----------------------------------------------------------------------------------

@Composable
fun HomeScreen(nav: Navigator) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    var version by remember { mutableIntStateOf(0) }
    val devices by loadAsync(version) { deps.devices.all() }
    var deleting by remember { mutableStateOf<UserDevice?>(null) }
    var renaming by remember { mutableStateOf<UserDevice?>(null) }
    val scope = rememberCoroutineScope()

    AppScaffold(
        title = "Thiết bị của tôi",
        nav = null,
        subtitle = devices?.takeIf { it.isNotEmpty() }?.let { "${it.size} thiết bị" },
        actions = { IconButton(onClick = { nav.push(Screen.About) }) { Icon(Icons.Outlined.Info, "Nguồn dữ liệu") } },
        fab = {
            if (devices?.isNotEmpty() == true) {
                ExtendedFloatingActionButton(
                    onClick = { nav.push(Screen.Categories) },
                    containerColor = palette.ink, contentColor = palette.body,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Thêm thiết bị") },
                )
            }
        },
    ) { padding ->
        val list = devices ?: return@AppScaffold Loading(padding)
        if (list.isEmpty()) {
            EmptyHome(padding) { nav.push(Screen.Categories) }
            return@AppScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { NoEmitterBanner() }
            items(list, key = { it.id }) { d ->
                DeviceCard(d, onOpen = { nav.push(Screen.Remote(d)) }, onRename = { renaming = d }, onDelete = { deleting = d })
            }
        }
    }

    renaming?.let { d ->
        var name by remember(d.id) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Đổi tên thiết bị") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { deps.devices.rename(d.id, name.trim()) }
                        renaming = null
                        version++
                    }
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Hủy") } },
        )
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Xóa ${d.name}?") },
            text = { Text("Bộ mã vẫn còn trong ứng dụng, bạn có thể thêm lại bất cứ lúc nào.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { deps.devices.delete(d.id) }
                        deleting = null
                        version++
                    }
                }) { Text("Xóa", color = palette.power) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Giữ lại") } },
        )
    }
}

/**
 * One saved device: tap to open its remote, power key to switch it without
 * opening. A/C cards also show the state last sent to the unit.
 */
@Composable
private fun DeviceCard(device: UserDevice, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val context = LocalContext.current
    val style = deviceStyle(if (device.isNetwork) device.kind else device.categoryKey)
    val buttons by loadAsync(device.id) {
        if (device.isNetwork) emptyList() else deps.db.buttons(device.codesetUid?.let { deps.db.codesetId(it) } ?: device.codesetId)
    }
    val atv by loadAsync(device.id) {
        device.host?.takeIf { device.kind == UserDevice.KIND_ATV }?.let { AtvRemote.of(it, deps.atvIdentity.value) }
    }
    val acButtons = remember(buttons) { acStateButtons(buttons.orEmpty()).associateBy { it.params!! } }
    var ac by remember(device.id) { mutableStateOf(AcMemory.load(context, device.id)) }
    var menu by remember { mutableStateOf(false) }

    val power: (() -> Unit)? = when {
        device.isNetwork -> atv?.let { client -> { client.sendKey(KeyEvent.KEYCODE_POWER); Unit } }
        acButtons.isNotEmpty() -> {
            {
                val next = ac.copy(power = !ac.power)
                acButtonFor(acButtons, next)?.let { b ->
                    if (deps.ir.send(b)) {
                        ac = next
                        AcMemory.save(context, device.id, next)
                    }
                }
            }
        }
        else -> buttons?.let { all ->
            (all.firstOrNull { it.funcKey == "POWER" } ?: all.firstOrNull { it.funcKey == "POWER_ON" })?.let { b -> { deps.ir.send(b); Unit } }
        }
    }
    val isAc = acButtons.isNotEmpty()
    val status = when {
        isAc && ac.power -> "Đang bật · ${acModeLabel(ac.mode)}${if (ac.mode != "fan") " ${ac.temp}°" else ""}"
        isAc -> "Đang tắt · ${device.brandName}"
        device.isNetwork -> "Android TV · Wi-Fi"
        else -> "${device.brandName} · ${style.name}"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(26.dp), spotColor = palette.shadow, ambientColor = palette.shadow)
            .clip(RoundedCornerShape(26.dp))
            .background(palette.body)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
    ) {
        CategoryBadge(style, 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isAc && ac.power) style.accent else palette.inkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (power != null) {
            val on = isAc && ac.power
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    // Solid red only for an A/C known to be running; other devices have no known state.
                    .background(if (on) palette.power else palette.groove)
                    .clickable(onClickLabel = "Bật/tắt ${device.name}", onClick = power),
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Nguồn",
                    tint = when { on -> Color.White; isAc -> palette.inkSoft; else -> palette.power }, modifier = Modifier.size(24.dp))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Tùy chọn", tint = palette.inkSoft) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Đổi tên") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Xóa", color = palette.power) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun EmptyHome(padding: PaddingValues, onAdd: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NoEmitterBanner()
        // A fan of category badges hints at what can be controlled.
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            listOf("tv", "ac", "fan", "light", "soundbar").forEach {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(palette.room).padding(3.dp)) { CategoryBadge(deviceStyle(it), 56.dp) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Điều khiển mọi thiết bị bằng điện thoại", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Chọn loại thiết bị và hãng, bấm thử vài nút để tìm đúng bộ mã. Hơn 7.000 bộ mã TV, điều hòa, quạt, đèn, loa… có sẵn trong máy.",
            style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.body),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp)) {
            Icon(Icons.Filled.Add, null)
            Text("  Thêm thiết bị đầu tiên", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// --- Category / Brand pickers ----------------------------------------------------------------

@Composable
fun CategoryScreen(nav: Navigator) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val categories by loadAsync { deps.db.categories().filter { it.codesetCount > 0 } }
    AppScaffold("Bạn muốn điều khiển gì?", nav) { padding ->
        val list = categories ?: return@AppScaffold Loading(padding)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            // Network remote first: it is the only option on phones without an IR blaster.
            item(span = { GridItemSpan(maxLineSpan) }) {
                val atv = deviceStyle("atv")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.tile(RoundedCornerShape(22.dp)).background(atv.accent)
                        .clickable { nav.push(Screen.AtvAdd) }.padding(14.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        Icon(atv.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("Android TV qua Wi-Fi", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            if (deps.ir.available) "Mi Box, Google TV… điều khiển qua mạng, không cần hướng điện thoại"
                            else "Dùng được trên điện thoại không có cổng hồng ngoại",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            if (!deps.ir.available) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Các loại dưới đây cần cổng hồng ngoại", style = MaterialTheme.typography.labelMedium,
                        color = palette.inkSoft, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(list, key = { it.id }) { c ->
                val style = deviceStyle(c.key)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.tile(RoundedCornerShape(22.dp)).background(palette.body)
                        .clickable { nav.push(Screen.Brands(c)) }.padding(12.dp),
                ) {
                    CategoryBadge(style, 40.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${VI.format(c.codesetCount)} bộ mã", style = MaterialTheme.typography.labelSmall, color = palette.inkSoft)
                    }
                }
            }
        }
    }
}

@Composable
fun BrandScreen(nav: Navigator, category: Category) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val style = deviceStyle(category.key)
    var query by remember { mutableStateOf("") }
    val brands by loadAsync(category.id, query) { deps.db.brands(category.id, query) }
    AppScaffold("Chọn hãng", nav, subtitle = style.name, style = style) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Tìm hãng, ví dụ Samsung") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.body, unfocusedContainerColor = palette.body,
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = style.accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(brands.orEmpty(), key = { it.id }) { b ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().tile(RoundedCornerShape(16.dp)).background(palette.body)
                            .clickable { nav.push(Screen.Match(category, b)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(style.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Text(b.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = style.accent)
                        }
                        Text(b.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp).weight(1f))
                        Text("${b.codesetCount} bộ", style = MaterialTheme.typography.labelMedium, color = palette.inkSoft)
                    }
                }
            }
        }
    }
}

// --- Matching wizard ---------------------------------------------------------------------------

@Composable
fun MatchScreen(nav: Navigator, category: Category, brand: Brand) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val style = deviceStyle(category.key)
    val codesets by loadAsync(category.id, brand.id) { deps.db.codesets(category.id, brand.id) }
    var index by remember { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppScaffold("${brand.name} · ${style.name}", nav, subtitle = "Tìm bộ mã phù hợp", style = style) { padding ->
        val list = codesets ?: return@AppScaffold Loading(padding)
        if (list.isEmpty()) return@AppScaffold Text("Hãng này chưa có bộ mã.", Modifier.padding(padding).padding(16.dp))
        val current = list[index.coerceIn(0, list.lastIndex)]
        val buttons by loadAsync(current.id) { deps.db.buttons(current.id) }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NoEmitterBanner()
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Bộ mã ${index + 1}", style = MaterialTheme.typography.headlineMedium)
                Text(" / ${list.size}", style = MaterialTheme.typography.titleMedium, color = palette.inkSoft, modifier = Modifier.padding(bottom = 3.dp))
            }
            LinearProgressIndicator(
                progress = { (index + 1f) / list.size },
                color = style.accent, trackColor = palette.groove, gapSize = 0.dp, drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Text(current.name, style = MaterialTheme.typography.bodySmall, color = palette.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(30.dp), spotColor = palette.shadow)
                    .clip(RoundedCornerShape(30.dp)).background(palette.body).padding(18.dp),
            ) {
                Text("Hướng điện thoại về thiết bị rồi bấm thử", style = MaterialTheme.typography.titleMedium)
                val all = buttons.orEmpty()
                val acTests = listOf(AcState().key, AcState(power = false).key, "power=0")
                    .mapNotNull { p -> all.firstOrNull { it.params == p } }.take(2)
                val tests = acTests.ifEmpty { (TEST_KEYS.mapNotNull { k -> all.firstOrNull { it.funcKey == k } } + all).distinct().take(4) }
                tests.forEach { b ->
                    IrKey(b, Modifier.fillMaxWidth().height(60.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
                            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { KeyFace(b, iconSize = 22.dp) }
                            Text(prettyLabel(b.label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 14.dp).weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Text("Thiết bị có phản hồi không?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            Button(
                onClick = { naming = true },
                colors = ButtonDefaults.buttonColors(containerColor = style.accent),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Có, dùng bộ mã này", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = { index++ },
                enabled = index < list.lastIndex,
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (index < list.lastIndex) "Không, thử bộ tiếp theo" else "Đã hết bộ mã của hãng này", style = MaterialTheme.typography.labelLarge) }
            if (index > 0) {
                TextButton(onClick = { index-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Quay lại bộ trước") }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (naming) {
            var name by remember { mutableStateOf("${style.name} ${brand.name}") }
            AlertDialog(
                onDismissRequest = { naming = false },
                title = { Text("Đặt tên thiết bị") },
                text = {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                        placeholder = { Text("Ví dụ: Điều hòa phòng ngủ") })
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val device = withContext(Dispatchers.IO) {
                                val id = deps.devices.add(name.ifBlank { brand.name }, current)
                                deps.devices.all().first { it.id == id }
                            }
                            naming = false
                            nav.resetTo(Screen.Home, Screen.Remote(device))
                        }
                    }) { Text("Lưu và mở remote") }
                },
                dismissButton = { TextButton(onClick = { naming = false }) { Text("Hủy") } },
            )
        }
    }
}

// --- About -----------------------------------------------------------------------------------

@Composable
fun AboutScreen(nav: Navigator) {
    val deps = LocalDeps.current
    val palette = LocalPalette.current
    val sources by loadAsync { deps.db.sources() }
    AppScaffold("Nguồn dữ liệu", nav) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Mã hồng ngoại được gộp từ các cơ sở dữ liệu mở, rồi chuẩn hóa và phân loại tự động. " +
                        "Một số bộ mã có loại thiết bị được đoán từ tên nút nên có thể chưa chính xác.",
                    style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft,
                )
            }
            items(sources.orEmpty()) { s ->
                Column(Modifier.fillMaxWidth().tile(RoundedCornerShape(18.dp)).background(palette.body).padding(16.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    Text(s.url, style = MaterialTheme.typography.bodySmall, color = palette.inkSoft)
                    Text("Giấy phép: ${s.license}", style = MaterialTheme.typography.bodySmall, color = palette.inkSoft)
                }
            }
            item {
                Text(
                    "Contains/accesses irdb by Simon Peter and contributors, used under permission. " +
                        "For licensing details and for information on how to contribute to the database, see " +
                        "https://github.com/probonopd/irdb",
                    style = MaterialTheme.typography.bodySmall, color = palette.inkSoft,
                )
                Text("Font Be Vietnam Pro, giấy phép SIL Open Font License 1.1.", style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSoft, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
