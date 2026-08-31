package org.yanavybori.feature.voter

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
fun VoterScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Избиратель", style = MaterialTheme.typography.headlineMedium)
        DemoBanner()
        Text("Архитектурная точка расширения: главная, справочник, дерево ситуаций и журнал.")
        Text("Функциональный сценарий первой контрольной точки реализован в модуле наблюдателя.")
        Button(onClick = onBack) { Text("Назад") }
    }
}
