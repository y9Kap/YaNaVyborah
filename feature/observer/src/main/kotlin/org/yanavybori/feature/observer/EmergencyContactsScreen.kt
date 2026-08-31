package org.yanavybori.feature.observer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.yanavybori.core.model.EmergencyContact
import org.yanavybori.core.model.EmergencyContactType
import org.yanavybori.core.ui.DemoBanner

private data class RegionalContactPlaceholder(
    val role: String,
    val explanation: String,
)

@Composable
internal fun EmergencyContactsScreen(state: ObserverUiState, modifier: Modifier) {
    val context = LocalContext.current
    val region = state.activeSession?.region?.ifBlank { null } ?: "текущего региона"
    val packContacts = state.manifest?.emergencyContacts.orEmpty().filter { contact ->
        contact.region == null || contact.region.equals(state.activeSession?.region, ignoreCase = true)
    }
    val configuredContacts = if (packContacts.none { it.phone == "112" }) {
        listOf(
            EmergencyContact(
                id = "built-in-emergency-112",
                title = "Экстренные службы",
                phone = "112",
                description = "Единый номер экстренных служб",
                type = EmergencyContactType.EMERGENCY_SERVICE,
            ),
        ) + packContacts
    } else {
        packContacts
    }
    val regionalContacts = buildList {
        if (configuredContacts.none { it.type == EmergencyContactType.HEADQUARTERS }) {
            add(
                RegionalContactPlaceholder(
                    "Начальник штаба",
                    "Номер должен быть добавлен в проверенный Election Pack для региона «$region».",
                ),
            )
        }
        if (configuredContacts.none { it.type == EmergencyContactType.LAWYER }) {
            add(
                RegionalContactPlaceholder(
                    "Юрист или адвокат «Яблока»",
                    "Используйте только контакт, заранее подтверждённый штабом.",
                ),
            )
        }
        if (configuredContacts.none { it.type == EmergencyContactType.COORDINATOR }) {
            add(
                RegionalContactPlaceholder(
                    "Региональный координатор наблюдателей",
                    "В DEMO-пакете номер не указан.",
                ),
            )
        }
    }

    LazyColumn(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        item {
            Text(
                "Проверьте все штабные номера до выезда на участок. Приложение не подставляет непроверенные контакты.",
            )
        }
        items(configuredContacts, key = { it.id }) { contact ->
            ContactCard(
                contact = contact,
                onDial = { openDialer(context, contact.phone) },
            )
        }
        items(regionalContacts, key = { it.role }) { contact ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contact.role, fontWeight = FontWeight.Bold)
                    Text("Номер не указан", style = MaterialTheme.typography.titleMedium)
                    Text(contact.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ContactCard(contact: EmergencyContact, onDial: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(contact.title, fontWeight = FontWeight.Bold)
            Text(contact.phone, style = MaterialTheme.typography.headlineMedium)
            if (contact.description.isNotBlank()) Text(contact.description)
            Button(onClick = onDial, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть номер в телефоне")
            }
        }
    }
}

private fun openDialer(context: Context, phone: String) {
    val dialablePhone = phone.filter { character ->
        character.isDigit() || character == '+' || character == '*' || character == '#'
    }
    if (dialablePhone.isBlank()) {
        Toast.makeText(context, "В контакте не указан номер телефона", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", dialablePhone, null))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "На устройстве не найдено приложение телефона", Toast.LENGTH_SHORT).show()
    }
}
