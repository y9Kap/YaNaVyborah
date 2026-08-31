package org.yanavybori.feature.workpressure

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
import org.yanavybori.core.ui.DemoBanner

@Composable
fun WorkPressureScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Давление на работе", style = MaterialTheme.typography.headlineMedium)
        DemoBanner()
        Text("Каркас использует общие Journal, Media и Complaint; отдельной инфраструктуры хранения нет.")
        Text("Будущие разделы: сценарии, журнал, импорт сообщения/скриншота и подготовка обращения.")
        Button(onClick = onBack) { Text("Назад") }
    }
}
