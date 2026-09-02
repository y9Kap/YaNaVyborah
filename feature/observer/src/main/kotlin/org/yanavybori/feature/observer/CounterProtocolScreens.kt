package org.yanavybori.feature.observer

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.yanavybori.core.model.MediaSource
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReconciliationInputType
import org.yanavybori.core.model.ReconciliationResult
import org.yanavybori.core.model.ReconciliationRuleType
import org.yanavybori.core.model.ReconciliationStatus
import org.yanavybori.core.navigation.ObserverRoute
import org.yanavybori.core.ui.DemoBanner
import org.yanavybori.core.ui.StatusPill

@Composable
internal fun CounterScreen(state: ObserverUiState, viewModel: ObserverViewModel, modifier: Modifier) {
    var label by rememberSaveable { mutableStateOf("") }
    LazyColumn(modifier.imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Счётчики хранят только статистические отметки, без сведений о личности.")
        }
        item {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Например: Стол №4") },
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = {
                    viewModel.createCounter(label)
                    label = ""
                },
                enabled = label.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Создать счётчик") }
        }
        items(state.counters, key = { it.id }) { counter ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(counter.label, fontWeight = FontWeight.Bold)
                            Text(counter.currentValue.toString(), style = MaterialTheme.typography.displaySmall)
                        }
                        if (counter.stoppedAt != null) StatusPill("Остановлен", MaterialTheme.colorScheme.secondary)
                    }
                    CounterLastAction(
                        mark = state.counterLastMarks[counter.id],
                        stoppedAt = counter.stoppedAt,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.decrementCounter(counter.id) },
                            enabled = counter.currentValue > 0 && counter.stoppedAt == null,
                            modifier = Modifier.weight(1f),
                        ) { Text("−1", style = MaterialTheme.typography.titleLarge) }
                        Button(
                            onClick = { viewModel.incrementCounter(counter.id) },
                            enabled = counter.stoppedAt == null,
                            modifier = Modifier.weight(1f),
                        ) { Text("+1", style = MaterialTheme.typography.titleLarge) }
                    }
                    if (counter.stoppedAt == null) {
                        TextButton(onClick = { viewModel.stopCounter(counter.id) }) { Text("Остановить") }
                    }
                }
            }
        }
        if (state.counters.isEmpty()) item { Text("Для текущего дня счётчиков пока нет.") }
    }
}

@Composable
internal fun ReconciliationScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
    navigate: (ObserverRoute) -> Unit,
) {
    val dayId = state.activeSession?.currentVotingDay ?: return
    val definitions = state.reconciliationDefinitions.filter {
        it.votingDayIds.isEmpty() || dayId in it.votingDayIds
    }
    var selectedId by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(definitions) {
        if (definitions.none { it.id == selectedId }) selectedId = definitions.firstOrNull()?.id.orEmpty()
    }
    val selected = definitions.firstOrNull { it.id == selectedId }
    val selectedIndex = definitions.indexOfFirst { it.id == selectedId }
    val values = remember(dayId, selectedId) { mutableStateMapOf<String, String>() }
    val saved = state.reconciliationSessions.firstOrNull { it.definitionId == selectedId && it.votingDayId == dayId }
    var photoMediaId by rememberSaveable(dayId, selectedId) { mutableStateOf<String?>(null) }
    var showPhotoWarning by remember { mutableStateOf(false) }
    var mismatchPrompt by remember { mutableStateOf<ReconciliationResult?>(null) }
    var complaintResult by remember { mutableStateOf<ReconciliationResult?>(null) }
    LaunchedEffect(saved?.updatedAt, selectedId) {
        if (saved != null && values.isEmpty()) values.putAll(saved.values)
        photoMediaId = saved?.photoMediaId
    }
    val valuesAreSaved = saved != null && saved.values == values.toMap() &&
        saved.photoMediaId == photoMediaId
    val appliedColor = Color(0xFF16803A)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.importMedia(uri.toString(), MediaSource.PHOTO_PICKER) { asset ->
                photoMediaId = asset.id
            }
        }
    }
    LazyColumn(modifier.imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        item {
            Text(
                "Выберите нужную сверку, заполните её поля и запустите проверку. " +
                    "Приложение покажет конкретные строки и величину расхождения.",
            )
        }
        if (selected != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Доступно сверок: ${definitions.size} · открыта ${selectedIndex + 1} из ${definitions.size}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(selected.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { selectedId = definitions[selectedIndex - 1].id },
                                enabled = selectedIndex > 0,
                                modifier = Modifier.weight(1f),
                            ) { Text("← Предыдущая") }
                            OutlinedButton(
                                onClick = { selectedId = definitions[selectedIndex + 1].id },
                                enabled = selectedIndex in 0 until definitions.lastIndex,
                                modifier = Modifier.weight(1f),
                            ) { Text("Следующая →") }
                        }
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(definitions, key = { it.id }) { definition ->
                    val index = definitions.indexOf(definition)
                    FilterChip(
                        selected = selectedId == definition.id,
                        onClick = { selectedId = definition.id },
                        label = { Text("${index + 1}. ${definition.title}") },
                    )
                }
            }
        }
        selected?.let { definition ->
            item { Text(definition.description) }
            items(definition.fields.sortedBy { it.order }, key = { "field:${it.id}" }) { field ->
                OutlinedTextField(
                    value = values[field.id].orEmpty(),
                    onValueChange = { values[field.id] = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(field.label) },
                    supportingText = { if (field.hint.isNotBlank()) Text(field.hint) },
                    keyboardOptions = KeyboardOptions(keyboardType = field.inputType.keyboardType()),
                    singleLine = true,
                )
            }
            item {
                OutlinedButton(
                    onClick = { showPhotoWarning = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (photoMediaId == null) {
                            "Приложить фотографию к сверке"
                        } else {
                            "Заменить приложенную фотографию"
                        },
                    )
                }
            }
            photoMediaId?.let { mediaId ->
                item(key = "selected-reconciliation-photo:$mediaId") {
                    SavedPhoto(mediaId, state, viewModel)
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.saveReconciliation(definition, values.toMap(), photoMediaId) { reconciliation ->
                            mismatchPrompt = reconciliation.results.firstOrNull { result ->
                                result.status == ReconciliationStatus.ERROR ||
                                    (result.status == ReconciliationStatus.WARNING &&
                                        definition.rules.firstOrNull { it.id == result.ruleId }?.type !=
                                        ReconciliationRuleType.CUSTOM_WARNING)
                            }
                        }
                    },
                    enabled = !valuesAreSaved,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (valuesAreSaved) appliedColor else MaterialTheme.colorScheme.primary,
                        disabledContainerColor = if (valuesAreSaved) appliedColor else MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = if (valuesAreSaved) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text(if (valuesAreSaved) "✓ Проверено и сохранено" else "Проверить и сохранить") }
            }
            if (saved != null && !valuesAreSaved) {
                item {
                    Text(
                        "Поля изменены. Результаты предыдущей проверки скрыты — нажмите «Проверить и сохранить» снова.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val results = if (valuesAreSaved) saved.results else emptyList()
            items(results, key = { "result:${it.ruleId}" }) { result ->
                val color = result.status.color()
                Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        StatusPill(result.status.label(), color)
                        Text(result.message, fontWeight = FontWeight.Bold)
                        Text(result.explanation)
                        Text(
                            result.sourceValues.entries.joinToString(separator = "\n") { (fieldId, value) ->
                                val fieldLabel = definition.fields.firstOrNull { it.id == fieldId }?.label
                                    ?: "Поле сверки"
                                "• $fieldLabel: ${value.ifBlank { "не заполнено" }}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (result.status == ReconciliationStatus.ERROR ||
                            (result.status == ReconciliationStatus.WARNING &&
                                definition.rules.firstOrNull { it.id == result.ruleId }?.type !=
                                ReconciliationRuleType.CUSTOM_WARNING)
                        ) {
                            OutlinedButton(
                                onClick = { complaintResult = result },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Сформировать жалобу") }
                        }
                    }
                }
            }
        }
        if (definitions.isEmpty()) item { Text("Для текущего дня форм сверки нет.") }
        val savedMaterials = state.reconciliationSessions
            .filter { it.votingDayId == dayId }
            .sortedByDescending { it.updatedAt }
        if (savedMaterials.isNotEmpty()) {
            item {
                Text("Сохранённые сверки и фотографии", style = MaterialTheme.typography.titleLarge)
            }
            items(savedMaterials, key = { "saved-reconciliation:${it.id}" }) { reconciliation ->
                val definition = state.reconciliationDefinitions.firstOrNull {
                    it.id == reconciliation.definitionId
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            definition?.title ?: "Сохранённая сверка",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Сохранено: ${formatTimestamp(reconciliation.updatedAt)}")
                        Text("Заполнено полей: ${reconciliation.values.count { it.value.isNotBlank() }}")
                        reconciliation.photoMediaId?.let { mediaId ->
                            SavedPhoto(mediaId, state, viewModel)
                        } ?: Text("Фотография не приложена", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showPhotoWarning) {
        AlertDialog(
            onDismissRequest = { showPhotoWarning = false },
            title = { Text("Проверьте кадр") },
            text = {
                Text(
                    "В кадре могут находиться персональные данные. Оригинал будет зашифрован " +
                        "и сохранён в приватном хранилище приложения.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPhotoWarning = false
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }) { Text("Выбрать фото") }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoWarning = false }) { Text("Отмена") }
            },
        )
    }
    mismatchPrompt?.let { result ->
        AlertDialog(
            onDismissRequest = { mismatchPrompt = null },
            title = { Text("Обнаружено несовпадение") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.message, fontWeight = FontWeight.Bold)
                    Text(result.explanation)
                    Text("Можно сразу создать связанный по смыслу черновик жалобы.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    complaintResult = result
                    mismatchPrompt = null
                }) { Text("Сформировать жалобу") }
            },
            dismissButton = {
                TextButton(onClick = { mismatchPrompt = null }) { Text("Продолжить сверку") }
            },
        )
    }
    complaintResult?.let { result ->
        ComplaintTemplatePickerDialog(
            templates = state.complaintTemplates,
            onDismiss = { complaintResult = null },
            onSelect = { template ->
                val definition = selected
                viewModel.createComplaint(
                    template = template,
                    contextNotes = buildString {
                        append("Сверка: ")
                        append(definition?.title.orEmpty())
                        append("\n")
                        append(result.message)
                        append("\n")
                        append(result.explanation)
                    },
                )
                complaintResult = null
                navigate(ObserverRoute.COMPLAINTS)
            },
            title = "Жалоба по результату сверки",
        )
    }
}

@Composable
internal fun ProtocolScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
    navigate: (ObserverRoute) -> Unit,
) {
    val dayId = state.activeSession?.currentVotingDay ?: return
    val definitions = state.reconciliationDefinitions.filter {
        it.votingDayIds.isEmpty() || dayId in it.votingDayIds
    }
    var selectedId by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(definitions) {
        if (definitions.none { it.id == selectedId }) selectedId = definitions.firstOrNull()?.id.orEmpty()
    }
    val selected = definitions.firstOrNull { it.id == selectedId }
    val selectedIndex = definitions.indexOfFirst { it.id == selectedId }
    val values = remember(dayId, selectedId) { mutableStateMapOf<String, String>() }
    var comments by rememberSaveable(dayId, selectedId) { mutableStateOf("") }
    var photoMediaId by rememberSaveable(dayId, selectedId) { mutableStateOf<String?>(null) }
    var showPhotoWarning by remember { mutableStateOf(false) }
    var saveApplied by rememberSaveable(dayId, selectedId) { mutableStateOf(false) }
    val appliedColor = Color(0xFF16803A)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.importMedia(uri.toString(), MediaSource.PHOTO_PICKER) {
                photoMediaId = it.id
                saveApplied = false
            }
        }
    }
    LazyColumn(modifier.imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        item {
            Text(
                "Выберите форму, перенесите данные из документа вручную и при необходимости " +
                    "приложите его фотографию. Распознавание текста не используется.",
            )
        }
        if (selected != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Форма ${selectedIndex + 1} из ${definitions.size}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            selected.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { selectedId = definitions[selectedIndex - 1].id },
                                enabled = selectedIndex > 0,
                                modifier = Modifier.weight(1f),
                            ) { Text("← Предыдущая форма") }
                            OutlinedButton(
                                onClick = { selectedId = definitions[selectedIndex + 1].id },
                                enabled = selectedIndex in 0 until definitions.lastIndex,
                                modifier = Modifier.weight(1f),
                            ) { Text("Следующая форма →") }
                        }
                    }
                }
            }
        }
        selected?.let { definition ->
            item { Text(definition.description) }
            items(definition.fields.sortedBy { it.order }, key = { "protocol:${it.id}" }) { field ->
                OutlinedTextField(
                    value = values[field.id].orEmpty(),
                    onValueChange = {
                        values[field.id] = it
                        saveApplied = false
                    },
                    label = { Text(field.label) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = field.inputType.keyboardType()),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = comments,
                    onValueChange = {
                        comments = it
                        saveApplied = false
                    },
                    label = { Text("Комментарии") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
            item {
                OutlinedButton(onClick = { showPhotoWarning = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (photoMediaId == null) "Приложить фотографию документа" else "Заменить фотографию документа")
                }
            }
            photoMediaId?.let { mediaId ->
                item(key = "selected-protocol-photo:$mediaId") {
                    SavedPhoto(mediaId, state, viewModel)
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.saveProtocol(definition, values.toMap(), comments, photoMediaId) {
                            saveApplied = true
                        }
                    },
                    enabled = !saveApplied,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (saveApplied) appliedColor else MaterialTheme.colorScheme.primary,
                        disabledContainerColor = if (saveApplied) appliedColor else MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = if (saveApplied) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text(if (saveApplied) "✓ Снимок сохранён" else "Сохранить снимок") }
            }
        }
        if (definitions.isEmpty()) item { Text("Для текущего дня форм протокола нет.") }
        item {
            TextButton(onClick = { navigate(ObserverRoute.REFERENCES) }, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть справочные материалы")
            }
        }
        item { Text("Сохранённые снимки", style = MaterialTheme.typography.titleLarge) }
        items(state.protocolSnapshots, key = { it.id }) { snapshot ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(formatTimestamp(snapshot.capturedAt), fontWeight = FontWeight.Bold)
                    val snapshotDefinition = state.reconciliationDefinitions.firstOrNull {
                        it.id == snapshot.protocolFormId
                    }
                    Text("Форма: ${snapshotDefinition?.title ?: "сохранённая форма"}")
                    val dayTitle = state.votingDays.firstOrNull { it.id == snapshot.votingDayId }?.title
                    Text("День: ${dayTitle ?: "сохранённый день"}")
                    Text(
                        snapshot.values.entries.joinToString(separator = "\n") { (fieldId, value) ->
                            val label = snapshotDefinition?.fields?.firstOrNull { it.id == fieldId }?.label
                                ?: "Поле протокола"
                            "• $label: $value"
                        },
                    )
                    snapshot.photoMediaId?.let { mediaId -> SavedPhoto(mediaId, state, viewModel) }
                    if (snapshot.comments.isNotBlank()) Text(snapshot.comments)
                }
            }
        }
    }
    if (showPhotoWarning) {
        AlertDialog(
            onDismissRequest = { showPhotoWarning = false },
            title = { Text("Проверьте кадр") },
            text = {
                Text(
                    "В кадре могут находиться персональные данные. Оригинал будет зашифрован " +
                        "и сохранён в приватном хранилище приложения.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPhotoWarning = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Выбрать фото") }
            },
            dismissButton = { TextButton(onClick = { showPhotoWarning = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SavedPhoto(
    mediaId: String,
    state: ObserverUiState,
    viewModel: ObserverViewModel,
) {
    LaunchedEffect(mediaId) { viewModel.loadMediaPreview(mediaId) }
    val asset = state.mediaAssets.firstOrNull { it.id == mediaId }
    val previewBytes = state.mediaPreviews[mediaId]
    val preview = remember(previewBytes) {
        previewBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            asset?.let { "Фото: ${it.originalName} · ${formatFileSize(it.size)}" }
                ?: "Фото сохранено и зашифровано",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            preview != null -> {
                Image(
                    bitmap = preview,
                    contentDescription = "Сохранённая фотография",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(preview.width.toFloat() / preview.height.coerceAtLeast(1))
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            mediaId in state.mediaPreviewErrors -> {
                Text("Предпросмотр фотографии недоступен", style = MaterialTheme.typography.bodySmall)
            }
            else -> Text("Загрузка фотографии…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}

@Composable
internal fun ReferenceDocumentsScreen(
    state: ObserverUiState,
    modifier: Modifier,
    requestedDocumentId: String? = null,
) {
    val documents = state.referenceDocuments.sortedBy { document ->
        when (document.id) {
            requestedDocumentId -> 0
            "reference-roadmap" -> 1
            else -> 2
        }
    }
    var expandedId by rememberSaveable { mutableStateOf<String?>(requestedDocumentId) }
    LazyColumn(modifier.imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        if (requestedDocumentId != null) {
            item {
                Text(
                    "Открыт материал, связанный с выбранной ситуацией.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(documents, key = { it.id }) { document ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(document.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(document.description)
                    if (document.previewLines.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                document.previewLines.forEachIndexed { index, line ->
                                    Text(
                                        highlightTemplatePlaceholders(line, MaterialTheme.colorScheme.error),
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                    if (document.content.isNotBlank()) {
                        TextButton(onClick = {
                            expandedId = if (expandedId == document.id) null else document.id
                        }) {
                            Text(
                                if (expandedId == document.id) {
                                    "Свернуть полный текст"
                                } else {
                                    "Открыть полный текст документа"
                                },
                            )
                        }
                        if (expandedId == document.id) {
                            Text(
                                highlightTemplatePlaceholders(
                                    document.content,
                                    MaterialTheme.colorScheme.error,
                                ),
                            )
                        }
                    }
                    document.hotspots.sortedBy { it.number }.forEach { hotspot ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(hotspot.number.toString(), color = MaterialTheme.colorScheme.onPrimary) }
                            Column(Modifier.weight(1f)) {
                                Text(hotspot.label, fontWeight = FontWeight.Bold)
                                Text(hotspot.explanation)
                            }
                        }
                    }
                    Text("Файл пакета: ${document.contentPath}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun ReconciliationInputType.keyboardType(): KeyboardType = when (this) {
    ReconciliationInputType.NON_NEGATIVE_INTEGER,
    ReconciliationInputType.INTEGER -> KeyboardType.Number
    ReconciliationInputType.TEXT -> KeyboardType.Text
}

private fun ReconciliationStatus.label(): String = when (this) {
    ReconciliationStatus.OK -> "OK"
    ReconciliationStatus.WARNING -> "Предупреждение"
    ReconciliationStatus.ERROR -> "Несовпадение"
    ReconciliationStatus.NOT_CHECKED -> "Не проверено"
}

@Composable
private fun ReconciliationStatus.color(): Color = when (this) {
    ReconciliationStatus.OK -> Color(0xFF16803A)
    ReconciliationStatus.WARNING -> Color(0xFF9A6700)
    ReconciliationStatus.ERROR -> MaterialTheme.colorScheme.error
    ReconciliationStatus.NOT_CHECKED -> MaterialTheme.colorScheme.outline
}
