package org.yanavybori.feature.observer

import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.EventSeverity
import org.yanavybori.core.model.JournalCategory
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.MediaSource
import org.yanavybori.core.navigation.ObserverRoute
import org.yanavybori.core.ui.StatusPill

@Composable
internal fun ChecklistScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
    navigate: (ObserverRoute) -> Unit,
) {
    val dayId = state.activeSession?.currentVotingDay ?: return
    val itemsById = state.checklistItems.associateBy { it.id }
    val visibleDefinitions = state.checklistDefinitions
        .filter { dayId in it.votingDayIds }
        .sortedBy { definition ->
            definition.itemIds.mapNotNull(itemsById::get).minOfOrNull(ChecklistItem::order) ?: Int.MAX_VALUE
        }
    val statuses = state.checklistStates.associateBy { it.checklistItemId }
    LazyColumn(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Выберите статус и нажмите «Зафиксировать». «Не выполнено» останется незавершённым; остальные статусы также добавят запись в журнал.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (visibleDefinitions.any { definition ->
                definition.itemIds.mapNotNull(itemsById::get).any { it.sourceDocumentId == "reference-roadmap" }
            }) {
            item {
                Text(
                    "Приоритетный источник: «Дорожная карта наблюдателя», 18–20 сентября 2026 года, три дня, ручной подсчёт.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        visibleDefinitions.forEach { definition ->
            item(key = "section:${definition.id}") {
                Text(definition.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val sectionItems = definition.itemIds.mapNotNull(itemsById::get).sortedBy { it.order }
            items(sectionItems, key = { it.id }) { item ->
                ChecklistItemCard(
                    item = item,
                    status = statuses[item.id]?.status ?: ChecklistStatus.NOT_CHECKED,
                    onStatus = { viewModel.setChecklistState(item, it) },
                    onOpenLaws = { navigate(ObserverRoute.LAWS) },
                )
            }
        }
        if (visibleDefinitions.isEmpty()) item { Text("Для этого дня в пакете нет пунктов.") }
    }
}

@Composable
private fun ChecklistItemCard(
    item: ChecklistItem,
    status: ChecklistStatus,
    onStatus: (ChecklistStatus) -> Unit,
    onOpenLaws: () -> Unit,
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var pendingStatusName by rememberSaveable(item.id, status.name) { mutableStateOf(status.name) }
    val pendingStatus = ChecklistStatus.valueOf(pendingStatusName)
    val hasUnconfirmedStatus = pendingStatus != status
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(item.shortExplanation)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ChecklistStatus.entries) { option ->
                    FilterChip(
                        selected = pendingStatus == option,
                        onClick = { pendingStatusName = option.name },
                        label = { Text(option.label()) },
                    )
                }
            }
            Text(
                if (hasUnconfirmedStatus) {
                    "Выбран новый статус: ${pendingStatus.label()}"
                } else {
                    "Текущий статус: ${status.label()}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                Text("Когда: ${item.whenToCheck}", fontWeight = FontWeight.SemiBold)
                Text("Что проверить", fontWeight = FontWeight.SemiBold)
                item.whatToCheck.forEach { Text("• $it") }
                if (item.possibleProblems.isNotEmpty()) {
                    Text("Возможные проблемы", fontWeight = FontWeight.SemiBold)
                    item.possibleProblems.forEach { Text("• $it") }
                }
                if (item.legalBasis.isNotBlank()) {
                    Text("Правовое основание", fontWeight = FontWeight.SemiBold)
                    Text(item.legalBasis)
                }
                if (item.liability.isNotBlank()) {
                    Text("Норма об ответственности", fontWeight = FontWeight.SemiBold)
                    Text(item.liability)
                }
                if (item.sourceDocumentId != null) {
                    Text(
                        "Источник: Дорожная карта наблюдателя" +
                            item.sourcePage?.let { ", страница $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (item.lawReferenceIds.isNotEmpty()) {
                    TextButton(onClick = onOpenLaws) { Text("Открыть связанные справки") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(if (expanded) "Свернуть" else "Подробнее")
                }
                Button(
                    onClick = { onStatus(pendingStatus) },
                    enabled = hasUnconfirmedStatus,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Зафиксировать")
                }
            }
        }
    }
}

@Composable
internal fun JournalScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
) {
    val session = state.activeSession ?: return
    val dayId = session.currentVotingDay
    var currentDayOnly by rememberSaveable { mutableStateOf(true) }
    var editorEvent by remember { mutableStateOf<JournalEvent?>(null) }
    var newEventDraft by remember { mutableStateOf<JournalEvent?>(null) }
    var exportStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val events = state.journalEvents.filter { !currentDayOnly || it.votingDayId == dayId }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dayTitles = state.votingDays.associate { it.id to it.title }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val csv = buildJournalCsv(session, dayTitles, events)
                        val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                            "Не удалось открыть выбранный файл"
                        }
                        stream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(csv) }
                    }
                }
                exportStatus = result.fold(
                    onSuccess = { "Журнал сохранён: ${events.size} записей" },
                    onFailure = { error -> "Не удалось сохранить журнал: ${error.message ?: "неизвестная ошибка"}" },
                )
            }
        }
    }
    LazyColumn(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(
                onClick = {
                    newEventDraft = viewModel.newEventDraft()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Добавить событие или нарушение")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(currentDayOnly, { currentDayOnly = true }, { Text("Текущий день") })
                FilterChip(!currentDayOnly, { currentDayOnly = false }, { Text("Все дни") })
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    exportStatus = null
                    exportLauncher.launch(journalExportFileName(session, System.currentTimeMillis()))
                },
                enabled = events.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить выбранные записи в CSV")
            }
        }
        item {
            Text(
                "CSV будет сохранён вне защищённого хранилища приложения.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        exportStatus?.let { message ->
            item { Text(message, style = MaterialTheme.typography.bodySmall) }
        }
        items(events, key = { it.id }) { event ->
            Card(
                onClick = {
                    editorEvent = event
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(event.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(event.category.label(), MaterialTheme.colorScheme.primary)
                    }
                    Text(formatTimestamp(event.timestamp), style = MaterialTheme.typography.labelMedium)
                    if (event.description.isNotBlank()) Text(event.description, maxLines = 3)
                    event.relatedChecklistItemId?.let { Text("Связано с чек-листом", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (events.isEmpty()) item { Text("Записей пока нет.") }
    }
    newEventDraft?.let { draft ->
        NewJournalEventFlow(
            initial = draft,
            viewModel = viewModel,
            onDismiss = { newEventDraft = null },
            onSave = {
                viewModel.createEvent(it)
                newEventDraft = null
            },
        )
    }
    editorEvent?.let { event ->
        EventEditorDialog(
            initial = event,
            isNew = false,
            onDismiss = { editorEvent = null },
            onSave = {
                viewModel.updateEvent(it)
                editorEvent = null
            },
        )
    }
}

private enum class NewEventStep { TYPE, CAPTURE, EDITOR }

@Composable
internal fun NewJournalEventFlow(
    initial: JournalEvent,
    viewModel: ObserverViewModel,
    onDismiss: () -> Unit,
    onSave: (JournalEvent) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var step by rememberSaveable(initial.id) { mutableStateOf(NewEventStep.TYPE) }
    var importedMediaId by remember(initial.id) { mutableStateOf<String?>(null) }
    var cameraError by rememberSaveable(initial.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.importMedia(uri.toString(), MediaSource.PHOTO_PICKER) { asset ->
                importedMediaId = asset.id
                draft = draft.copy(mediaIds = draft.mediaIds + asset.id)
                step = NewEventStep.EDITOR
            }
        }
    }

    fun dismissAndCleanUp() {
        importedMediaId?.let(viewModel::deleteMedia)
        onDismiss()
    }

    when (step) {
        NewEventStep.TYPE -> Dialog(onDismissRequest = ::dismissAndCleanUp) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Что добавить?", style = MaterialTheme.typography.titleLarge)
                    Text("Для предполагаемого нарушения сначала откроется шаг фото/видеофиксации.")
                    Button(
                        onClick = {
                            draft = draft.copy(
                                category = JournalCategory.NORMAL,
                                severity = EventSeverity.INFO,
                                title = "Событие",
                            )
                            step = NewEventStep.EDITOR
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Обычное событие") }
                    OutlinedButton(
                        onClick = {
                            draft = draft.copy(
                                category = JournalCategory.SUSPECTED_VIOLATION,
                                severity = EventSeverity.ATTENTION,
                                title = "Предполагаемое нарушение",
                            )
                            step = NewEventStep.CAPTURE
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Предполагаемое нарушение") }
                    TextButton(onClick = ::dismissAndCleanUp, modifier = Modifier.fillMaxWidth()) {
                        Text("Отмена")
                    }
                }
            }
        }

        NewEventStep.CAPTURE -> Dialog(onDismissRequest = ::dismissAndCleanUp) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Сначала зафиксируйте факт", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Если это безопасно и допустимо, сначала снимите происходящее, дождитесь сохранения фото или видео, затем выберите файл и заполните отчёт.",
                    )
                    Text(
                        "Не нарушайте тайну голосования и не снимайте документы или персональные данные без необходимости.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            cameraError = null
                            runCatching {
                                context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                            }.onFailure {
                                cameraError = "Камеру открыть не удалось. Откройте системную камеру вручную."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Открыть камеру") }
                    Button(
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Выбрать сохранённое фото или видео") }
                    OutlinedButton(
                        onClick = { step = NewEventStep.EDITOR },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Продолжить без медиа") }
                    Text(
                        "Используйте этот вариант, если съёмка невозможна, небезопасна или недопустима.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = ::dismissAndCleanUp, modifier = Modifier.fillMaxWidth()) {
                        Text("Отмена")
                    }
                }
            }
        }

        NewEventStep.EDITOR -> EventEditorDialog(
            initial = draft,
            isNew = true,
            onDismiss = ::dismissAndCleanUp,
            onSave = {
                importedMediaId = null
                onSave(it)
            },
        )
    }
}

@Composable
internal fun EventEditorDialog(
    initial: JournalEvent,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (JournalEvent) -> Unit,
) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var participants by remember(initial.id) { mutableStateOf(initial.participantNotes) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            LazyColumn(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        if (isNew) "Новое событие" else "Редактировать событие",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                item { Text(formatTimestamp(initial.timestamp)) }
                if (category == JournalCategory.SUSPECTED_VIOLATION) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                if (initial.mediaIds.isEmpty()) {
                                    "Отчёт создаётся без медиафиксации. Укажите в описании точное время, место, наблюдаемые факты и свидетелей."
                                } else {
                                    "Фото/видео сохранено в защищённом хранилище приложения и будет связано с этой записью."
                                },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(title, { title = it }, label = { Text("Заголовок") },
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(description, { description = it }, label = { Text("Описание") },
                        modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                item {
                    OutlinedTextField(participants, { participants = it },
                        label = { Text("Участники / примечания") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val categories = if (initial.category != JournalCategory.SUSPECTED_VIOLATION) {
                            JournalCategory.entries.filterNot { it == JournalCategory.SUSPECTED_VIOLATION }
                        } else {
                            JournalCategory.entries
                        }
                        items(categories) { option ->
                            FilterChip(category == option, { category = option }, { Text(option.label()) })
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                        Button(
                            onClick = {
                                onSave(
                                    initial.copy(
                                        title = title.trim(),
                                        description = description.trim(),
                                        participantNotes = participants.trim(),
                                        category = category,
                                    ),
                                )
                            },
                            enabled = title.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text(if (isNew) "Добавить" else "Сохранить") }
                    }
                }
            }
        }
    }
}

private fun ChecklistStatus.label(): String = when (this) {
    ChecklistStatus.NOT_CHECKED -> "Не выполнено"
    ChecklistStatus.OK -> "Всё нормально"
    ChecklistStatus.PROBLEM -> "Проблема"
    ChecklistStatus.NOT_APPLICABLE -> "Не применимо"
}

internal fun JournalCategory.label(): String = when (this) {
    JournalCategory.NORMAL -> "Обычное"
    JournalCategory.CONTROL -> "Контроль"
    JournalCategory.PROBLEM -> "Проблема"
    JournalCategory.SUSPECTED_VIOLATION -> "Предполагаемое нарушение"
    JournalCategory.DOCUMENT -> "Документ"
    JournalCategory.COMPLAINT -> "Жалоба"
    JournalCategory.POLICE -> "Полиция"
    JournalCategory.COUNTING -> "Подсчёт"
    JournalCategory.PROTOCOL -> "Протокол"
    JournalCategory.CUSTOM -> "Другое"
}
