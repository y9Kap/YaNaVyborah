package org.yanavybori.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.ui.DemoBanner

@Composable
fun SettingsScreen(manifest: ElectionPackManifest?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки и пакет", style = MaterialTheme.typography.headlineMedium)
        if (manifest?.isDemo == true) DemoBanner()
        Text("Пакет: ${manifest?.name ?: "не установлен"}")
        manifest?.let {
            Text("Версия: ${it.version} / content ${it.contentVersion}")
            Text("Источник: ${it.publisher}")
            Text("Схема: ${it.schemaVersion}; локаль: ${it.locale}")
        }
        Text("Сеть, аналитика и автоматическая отправка в этой сборке отсутствуют.")
        Button(onClick = onBack) { Text("Назад") }
    }
}
