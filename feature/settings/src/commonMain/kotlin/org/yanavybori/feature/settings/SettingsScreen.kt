package org.yanavybori.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yanavybori.core.common.SystemClock
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.ui.AppThemeMode
import org.yanavybori.core.ui.BackHandler
import org.yanavybori.core.ui.DemoBanner
import org.yanavybori.core.ui.DocumentRequest
import org.yanavybori.core.ui.LocalPlatformUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    manifest: ElectionPackManifest?,
    applicationVersion: String,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    exportFileName: (Long) -> String,
    buildExport: suspend (Long) -> ByteArray,
    onBack: () -> Unit,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAbout) { showAbout = false }
    if (showAbout) {
        AboutApplicationScreen(manifest, applicationVersion) { showAbout = false }
        return
    }

    val platform = LocalPlatformUi.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exporting by remember { mutableStateOf(false) }
    var exportTimestamp by remember { mutableStateOf(0L) }
    val documentCreator = platform.rememberDocumentCreator { handle ->
        if (handle != null) scope.launch {
            exporting = true
            runCatching {
                platform.writeDocument(handle, buildExport(exportTimestamp))
            }.fold(
                onSuccess = { snackbar.showSnackbar("Данные выгружены") },
                onFailure = { snackbar.showSnackbar(it.message ?: "Не удалось выгрузить данные") },
            )
            exporting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionTitle("Общие") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    ThemeChoice(
                        title = "Светлая тема",
                        icon = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
                        selected = themeMode == AppThemeMode.LIGHT,
                        onClick = { onThemeChange(AppThemeMode.LIGHT) },
                    )
                    HorizontalDivider()
                    ThemeChoice(
                        title = "Тёмная тема",
                        icon = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                        selected = themeMode == AppThemeMode.DARK,
                        onClick = { onThemeChange(AppThemeMode.DARK) },
                    )
                }
            }

            item { SectionTitle("Мои данные") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text("Выгрузить все данные", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Один JSON-файл со всеми вашими записями и вложениями. Он может содержать персональные данные.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                exportTimestamp = SystemClock.now()
                                documentCreator.launch(
                                    DocumentRequest(exportFileName(exportTimestamp), "application/json"),
                                )
                            },
                            enabled = !exporting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 10.dp).size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(if (exporting) "Подготавливаем файл…" else "Выгрузить данные")
                        }
                    }
                }
            }

            item { SectionTitle("О приложении") }
            item {
                Card(Modifier.fillMaxWidth().clickable { showAbout = true }) {
                    ListItem(
                        headlineContent = { Text("Информация о приложении") },
                        supportingContent = { Text("Версия $applicationVersion") },
                        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Открыть")
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ThemeChoice(
    title: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        leadingContent = icon,
        trailingContent = { RadioButton(selected = selected, onClick = onClick) },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutApplicationScreen(
    manifest: ElectionPackManifest?,
    applicationVersion: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация о приложении") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Я на выборах", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Версия $applicationVersion", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (manifest?.isDemo == true) item { DemoBanner() }
            item { SectionTitle("Пакет материалов") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoLine("Название", manifest?.name ?: "Не установлен")
                        manifest?.let {
                            InfoLine("Версия пакета", it.version)
                            InfoLine("Версия содержимого", it.contentVersion.toString())
                            InfoLine("Источник", it.publisher)
                            InfoLine("Схема", it.schemaVersion.toString())
                            InfoLine("Локаль", it.locale)
                        }
                    }
                }
            }
            item {
                Text(
                    "Сеть, аналитика и автоматическая отправка данных в этой сборке отсутствуют.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
