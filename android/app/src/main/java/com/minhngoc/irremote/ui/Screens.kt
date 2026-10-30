package com.minhngoc.irremote.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minhngoc.irremote.data.Brand
import com.minhngoc.irremote.data.Category
import com.minhngoc.irremote.data.UserDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    nav: Navigator?,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (nav != null) IconButton(onClick = nav::pop) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = { actions() },
            )
        },
        floatingActionButton = fab,
        content = content,
    )
}

@Composable
fun Loading(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun NoEmitterBanner() {
    if (LocalDeps.current.ir.available) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(
            "Điện thoại này không có cổng hồng ngoại (IR blaster). Bạn vẫn có thể xem dữ liệu nhưng không phát được lệnh.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

// --- Home ----------------------------------------------------------------------

@Composable
fun HomeScreen(nav: Navigator) {
    val deps = LocalDeps.current
    var version by remember { mutableIntStateOf(0) }
    val devices by loadAsync(version) { deps.devices.all() }
    var deleting by remember { mutableStateOf<UserDevice?>(null) }
    val scope = rememberCoroutineScope()

    AppScaffold(
        title = "Thiết bị của tôi",
        nav = null,
        actions = { IconButton(onClick = { nav.push(Screen.About) }) { Icon(Icons.Filled.Info, "Giới thiệu") } },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.push(Screen.Categories) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Thêm thiết bị") },
            )
        },
    ) { padding ->
        val list = devices ?: return@AppScaffold Loading(padding)
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { NoEmitterBanner() }
            if (list.isEmpty()) item {
                Text(
                    "Chưa có thiết bị nào.\nBấm \"Thêm thiết bị\" để chọn loại, hãng và dò bộ mã phù hợp.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            items(list, key = { it.id }) { d ->
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text("${d.brandName} · ${categoryName(d.categoryKey)}") },
                    trailingContent = {
                        IconButton(onClick = { deleting = d }) { Icon(Icons.Filled.Delete, "Xóa") }
                    },
                    modifier = Modifier.clickable { nav.push(Screen.Remote(d)) },
                )
                HorizontalDivider()
            }
        }
    }

    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Xóa \"${d.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { deps.devices.delete(d.id) }
                        deleting = null
                        version++
                    }
                }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Hủy") } },
        )
    }
}

private val CATEGORY_NAMES = mapOf(
    "tv" to "TV", "ac" to "Điều hòa", "fan" to "Quạt", "stb" to "Đầu thu / TV box", "projector" to "Máy chiếu",
    "soundbar" to "Loa / Soundbar", "audio" to "Amply / Receiver", "player" to "Đầu đĩa / Media", "light" to "Đèn",
    "air_purifier" to "Lọc khí / Tạo ẩm", "heater" to "Máy sưởi", "camera" to "Camera", "monitor" to "Màn hình",
    "switcher" to "Bộ chuyển tín hiệu", "car" to "Âm thanh ô tô", "vacuum" to "Robot hút bụi", "other" to "Khác",
)

fun categoryName(key: String): String = CATEGORY_NAMES[key] ?: key

// --- Category / Brand pickers -----------------------------------------------------

@Composable
fun CategoryScreen(nav: Navigator) {
    val deps = LocalDeps.current
    val categories by loadAsync { deps.db.categories().filter { it.codesetCount > 0 } }
    AppScaffold("Chọn loại thiết bị", nav) { padding ->
        val list = categories ?: return@AppScaffold Loading(padding)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(list, key = { it.id }) { c ->
                Card(onClick = { nav.push(Screen.Brands(c)) }) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text("${c.codesetCount} bộ mã", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun BrandScreen(nav: Navigator, category: Category) {
    val deps = LocalDeps.current
    var query by remember { mutableStateOf("") }
    val brands by loadAsync(category.id, query) { deps.db.brands(category.id, query) }
    AppScaffold("${category.name}: chọn hãng", nav) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Tìm hãng…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(brands.orEmpty(), key = { it.id }) { b ->
                    ListItem(
                        headlineContent = { Text(b.name) },
                        trailingContent = { Text("${b.codesetCount}") },
                        modifier = Modifier.clickable { nav.push(Screen.Match(category, b)) },
                    )
                }
            }
        }
    }
}

// --- Matching wizard -------------------------------------------------------------------

@Composable
fun MatchScreen(nav: Navigator, category: Category, brand: Brand) {
    val deps = LocalDeps.current
    val codesets by loadAsync(category.id, brand.id) { deps.db.codesets(category.id, brand.id) }
    var index by remember { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppScaffold("${brand.name} · ${category.name}", nav) { padding ->
        val list = codesets ?: return@AppScaffold Loading(padding)
        if (list.isEmpty()) return@AppScaffold Text("Không có bộ mã.", Modifier.padding(padding).padding(16.dp))
        val current = list[index.coerceIn(0, list.lastIndex)]
        val buttons by loadAsync(current.id) { deps.db.buttons(current.id) }

        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoEmitterBanner()
            Text("Bộ mã ${index + 1} / ${list.size}", style = MaterialTheme.typography.headlineSmall)
            Text("${current.name} · ${current.buttonCount} nút · nguồn: ${current.source}", style = MaterialTheme.typography.bodySmall)
            Text("Hướng điện thoại về thiết bị và bấm thử các nút dưới đây:")

            val all = buttons.orEmpty()
            val acTests = listOf(AcState().key, "power=0").mapNotNull { p -> all.firstOrNull { it.params == p } }
            val tests = acTests.ifEmpty {
                (TEST_KEYS.mapNotNull { k -> all.firstOrNull { it.funcKey == k } } + all).distinct().take(4)
            }
            tests.forEach { b ->
                RemoteKey(b, text = b.label, modifier = Modifier.fillMaxWidth().height(56.dp))
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) { Text("Thiết bị phản hồi đúng — lưu") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("◀ Bộ trước") }
                OutlinedButton(onClick = { index++ }, enabled = index < list.lastIndex, modifier = Modifier.weight(1f)) { Text("Bộ tiếp ▶") }
            }
        }

        if (naming) {
            var name by remember { mutableStateOf("${brand.name} ${category.name}") }
            AlertDialog(
                onDismissRequest = { naming = false },
                title = { Text("Đặt tên thiết bị") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
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
                    }) { Text("Lưu") }
                },
                dismissButton = { TextButton(onClick = { naming = false }) { Text("Hủy") } },
            )
        }
    }
}

// --- About -----------------------------------------------------------------------------

@Composable
fun AboutScreen(nav: Navigator) {
    val deps = LocalDeps.current
    val sources by loadAsync { deps.db.sources() }
    AppScaffold("Giới thiệu & nguồn dữ liệu", nav) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    "Mã hồng ngoại được tổng hợp từ các cơ sở dữ liệu mở, chuẩn hóa và phân loại tự động. " +
                        "Một số bộ mã có loại thiết bị được suy đoán từ tên nút nên có thể chưa chính xác.",
                )
                Spacer(Modifier.height(16.dp))
            }
            items(sources.orEmpty()) { s ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    Text(s.url, style = MaterialTheme.typography.bodySmall)
                    Text("Giấy phép: ${s.license}", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Contains/accesses irdb by Simon Peter and contributors, used under permission. " +
                        "For licensing details and for information on how to contribute to the database, see " +
                        "https://github.com/probonopd/irdb",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
