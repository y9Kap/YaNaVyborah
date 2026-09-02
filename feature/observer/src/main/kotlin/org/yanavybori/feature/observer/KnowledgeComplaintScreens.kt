package org.yanavybori.feature.observer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.yanavybori.core.model.Complaint
import org.yanavybori.core.model.ComplaintStatus
import org.yanavybori.core.model.ComplaintTemplate
import org.yanavybori.core.model.LawReference
import org.yanavybori.core.model.MediaSource
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.SituationCategory
import org.yanavybori.core.navigation.ObserverRoute
import org.yanavybori.core.ui.DemoBanner
import org.yanavybori.core.ui.StatusPill

@Composable
internal fun SituationNavigatorScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
    navigate: (ObserverRoute) -> Unit,
    openReferenceDocument: (String) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.situations.firstOrNull { it.id == selectedId }
    val children = selected?.let { parent -> state.situations.filter { it.parentId == parent.id } }.orEmpty()
    BackHandler(enabled = selected != null) {
        selectedId = selected?.parentId
    }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        if (selected == null) {
            item { Text("Выберите ситуацию", style = MaterialTheme.typography.titleLarge) }
            items(state.situations.filter { it.parentId == null }, key = { it.id }) { situation ->
                Card(onClick = { selectedId = situation.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(situation.title, fontWeight = FontWeight.Bold)
                        Text(situation.summary)
                    }
                }
            }
        } else {
            item { TextButton(onClick = { selectedId = selected.parentId }) { Text("← Назад к вариантам") } }
            item { SituationDetail(selected, state.laws) }
            val linkedDocuments = state.referenceDocuments.filter { it.id in selected.referenceDocumentIds }
            if (linkedDocuments.isNotEmpty()) {
                item { Text("Примеры документов", style = MaterialTheme.typography.titleMedium) }
                items(linkedDocuments, key = { "situation-document:${it.id}" }) { document ->
                    OutlinedButton(
                        onClick = { openReferenceDocument(document.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(document.title) }
                }
            }
            if (children.isNotEmpty()) {
                item { Text("Уточните", style = MaterialTheme.typography.titleMedium) }
                items(children, key = { it.id }) { child ->
                    Button(onClick = { selectedId = child.id }, modifier = Modifier.fillMaxWidth()) {
                        Text(child.answerLabel ?: child.title)
                    }
                }
            } else {
                item {
                    Button(
                        onClick = { viewModel.createEventFromSituation(selected) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Зафиксировать в журнале") }
                }
                selected.complaintTemplateId?.let {
                    item {
                        OutlinedButton(
                            onClick = {
                                viewModel.createComplaintForSituation(selected)
                                navigate(ObserverRoute.COMPLAINTS)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Подготовить жалобу") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SituationDetail(situation: Situation, laws: List<LawReference>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(situation.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(situation.summary)
        DetailList("Что проверить", situation.factsToCheck)
        DetailList("Что делать", situation.recommendedActions)
        DetailList("Что записать", situation.dataToRecord)
        val linkedLaws = laws.filter { it.id in situation.lawReferenceIds }
        if (linkedLaws.isNotEmpty()) {
            Text("Правовое основание", style = MaterialTheme.typography.titleMedium)
            linkedLaws.forEach { law ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Text(law.title, fontWeight = FontWeight.Bold)
                        Text(law.summary)
                        Text("Источник: ${law.source}; версия: ${law.sourceVersion}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailList(title: String, values: List<String>) {
    if (values.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    values.forEach { Text("• $it") }
}

@Composable
internal fun PoliceScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
    navigate: (ObserverRoute) -> Unit,
    openReferenceDocument: (String) -> Unit,
) {
    val situations = state.situations.filter { it.category == SituationCategory.POLICE_INTERACTION }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = situations.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        item {
            Text(
                "Цель раздела — спокойно уточнить и зафиксировать факты. Он не призывает к конфликту или сопротивлению.",
            )
        }
        if (selected == null) {
            items(situations, key = { it.id }) { situation ->
                Card(onClick = { selectedId = situation.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(situation.title, fontWeight = FontWeight.Bold)
                        Text(situation.summary)
                    }
                }
            }
        } else {
            item { TextButton(onClick = { selectedId = null }) { Text("← К списку") } }
            item { SituationDetail(selected, state.laws) }
            val linkedDocuments = state.referenceDocuments.filter {
                it.id in selected.referenceDocumentIds
            }
            if (linkedDocuments.isNotEmpty()) {
                item { Text("Примеры документов", style = MaterialTheme.typography.titleMedium) }
                items(linkedDocuments, key = { "police-document:${it.id}" }) { document ->
                    OutlinedButton(
                        onClick = { openReferenceDocument(document.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(document.title) }
                }
            }
            item {
                Button(onClick = { viewModel.createEventFromSituation(selected) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Зафиксировать")
                }
            }
            if (selected.complaintTemplateId != null) {
                item {
                    OutlinedButton(
                        onClick = {
                            viewModel.createComplaintForSituation(selected)
                            navigate(ObserverRoute.COMPLAINTS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Создать жалобу") }
                }
            }
        }
    }
}

@Composable
internal fun LawsScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
    navigate: (ObserverRoute) -> Unit,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(modifier.imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        item {
            Text(
                "Нажмите на карточку, чтобы открыть развёрнутый текст всех применимых пунктов нормы, " +
                    "на которые ссылаются материалы приложения.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedButton(
                onClick = { navigate(ObserverRoute.REFERENCES) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Открыть полные документы и памятки") }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск по ситуациям, справкам и шаблонам") },
                singleLine = true,
            )
        }
        item {
            Button(onClick = { viewModel.search(query) }, modifier = Modifier.fillMaxWidth()) {
                Text("Найти локально")
            }
        }
        if (query.isNotBlank()) {
            items(state.searchResults, key = { "search:${it.type}:${it.id}" }) { result ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Text(result.title, fontWeight = FontWeight.Bold)
                        Text(result.summary)
                        Text(result.type.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.searchResults.isEmpty()) item { Text("Совпадений пока нет. Нажмите «Найти локально».") }
        }
        items(state.laws.sortedBy { if ("приоритетный" in it.tags) 0 else 1 }, key = { it.id }) { law ->
            Card(onClick = { expandedId = if (expandedId == law.id) null else law.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(law.title, fontWeight = FontWeight.Bold)
                    Text(law.citation)
                    Text(law.summary)
                    if (expandedId == law.id) {
                        Text("Применимые положения", fontWeight = FontWeight.SemiBold)
                        Text(law.text)
                    }
                    Text("Источник: ${law.source}", style = MaterialTheme.typography.bodySmall)
                    Text("Версия пакета: ${law.sourceVersion}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun ComplaintsScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
) {
    var editing by remember { mutableStateOf<Complaint?>(null) }
    var rewriting by remember { mutableStateOf<Complaint?>(null) }
    var photoTarget by remember { mutableStateOf<Complaint?>(null) }
    var showPhotoWarning by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = photoTarget
        if (uri != null && target != null) {
            viewModel.importMedia(uri.toString(), MediaSource.PHOTO_PICKER) { asset ->
                viewModel.attachAcceptedCopy(target, asset.id)
            }
        }
        photoTarget = null
    }

    LaunchedEffect(
        state.complaintToRevealId,
        state.complaints,
        state.complaintTemplates,
        state.manifest?.isDemo,
    ) {
        val complaintId = state.complaintToRevealId ?: return@LaunchedEffect
        val complaintIndex = state.complaints.indexOfFirst { it.id == complaintId }
        if (complaintIndex < 0) return@LaunchedEffect

        val complaintListStartIndex =
            (if (state.manifest?.isDemo == true) 1 else 0) +
                1 + state.complaintTemplates.size + 1
        listState.animateScrollToItem(complaintListStartIndex + complaintIndex)
        viewModel.consumeComplaintReveal(complaintId)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        item { Text("Создать из шаблона", style = MaterialTheme.typography.titleMedium) }
        items(
            state.complaintTemplates.sortedBy { if (it.id.startsWith("complaint-roadmap-")) 0 else 1 },
            key = { "template:${it.id}" },
        ) { template ->
            OutlinedButton(
                onClick = {
                    viewModel.createComplaint(template)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(template.title)
            }
        }
        item { Text("Мои жалобы", style = MaterialTheme.typography.titleLarge) }
        items(state.complaints, key = { it.id }) { complaint ->
            Card(onClick = { editing = complaint }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(complaint.recipient.ifBlank { "Адресат не указан" }, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        StatusPill(complaint.status.label(), MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        highlightTemplatePlaceholders(complaint.text, MaterialTheme.colorScheme.error),
                        maxLines = 3,
                    )
                    complaint.registrationNumber?.let { Text("Рег. номер: $it") }
                    if (complaint.acceptedCopyMediaId != null) Text("Фото принятой копии сохранено")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { rewriting = complaint }, modifier = Modifier.weight(1f)) {
                            Text("Полный текст")
                        }
                        if (complaint.status == ComplaintStatus.DRAFT) {
                            Button(onClick = { viewModel.updateComplaintStatus(complaint, ComplaintStatus.READY) },
                                modifier = Modifier.weight(1f)) { Text("Готова") }
                        } else if (complaint.status == ComplaintStatus.READY) {
                            Button(onClick = { viewModel.updateComplaintStatus(complaint, ComplaintStatus.SUBMITTED) },
                                modifier = Modifier.weight(1f)) { Text("Жалоба подана") }
                        }
                    }
                    if (complaint.status == ComplaintStatus.SUBMITTED || complaint.status == ComplaintStatus.ACCEPTED) {
                        OutlinedButton(
                            onClick = {
                                photoTarget = complaint
                                showPhotoWarning = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Сфотографировать / выбрать принятую копию") }
                    }
                }
            }
        }
        if (state.complaints.isEmpty()) item { Text("Черновиков пока нет.") }
    }

    editing?.let { complaint ->
        ComplaintEditorDialog(
            initial = complaint,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateComplaint(it)
                editing = null
            },
            onStatus = {
                viewModel.updateComplaintStatus(complaint, it)
                editing = null
            },
        )
    }
    rewriting?.let { complaint ->
        RewriteComplaintDialog(complaint) { rewriting = null }
    }
    if (showPhotoWarning) {
        AlertDialog(
            onDismissRequest = { showPhotoWarning = false; photoTarget = null },
            title = { Text("Перед съёмкой") },
            text = {
                Text("Проверьте, не попадают ли в кадр списки избирателей, документы и другие персональные данные.")
            },
            confirmButton = {
                Button(onClick = {
                    showPhotoWarning = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Выбрать изображение") }
            },
            dismissButton = { TextButton(onClick = { showPhotoWarning = false; photoTarget = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ComplaintEditorDialog(
    initial: Complaint,
    onDismiss: () -> Unit,
    onSave: (Complaint) -> Unit,
    onStatus: (ComplaintStatus) -> Unit,
) {
    var recipient by remember(initial.id) { mutableStateOf(initial.recipient) }
    var text by remember(initial.id) { mutableStateOf(initial.text) }
    var registration by remember(initial.id) { mutableStateOf(initial.registrationNumber.orEmpty()) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            LazyColumn(
                Modifier.fillMaxWidth().imePadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text("Редактировать жалобу", style = MaterialTheme.typography.titleLarge) }
                item {
                    OutlinedTextField(
                        recipient,
                        { recipient = it },
                        label = { Text("Кому передана") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = TemplatePlaceholderVisualTransformation(MaterialTheme.colorScheme.error),
                    )
                }
                item {
                    OutlinedTextField(
                        text,
                        { text = it },
                        label = { Text("Текст") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8,
                        visualTransformation = TemplatePlaceholderVisualTransformation(MaterialTheme.colorScheme.error),
                    )
                }
                item { OutlinedTextField(registration, { registration = it }, label = { Text("Регистрационный номер") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth()) }
                if (initial.status == ComplaintStatus.SUBMITTED) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(false, { onStatus(ComplaintStatus.ACCEPTED) }, { Text("Принята") })
                            FilterChip(false, { onStatus(ComplaintStatus.REJECTED) }, { Text("Отклонена") })
                            FilterChip(false, { onStatus(ComplaintStatus.UNKNOWN) }, { Text("Неизвестно") })
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                        Button(
                            onClick = {
                                onSave(initial.copy(recipient = recipient, text = text,
                                    registrationNumber = registration.ifBlank { null }, notes = notes))
                            },
                            enabled = text.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Сохранить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RewriteComplaintDialog(complaint: Complaint, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                item { Text("Для переписывания", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                item {
                    Text(
                        highlightTemplatePlaceholders(
                            "Кому: ${complaint.recipient}",
                            MaterialTheme.colorScheme.error,
                        ),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                complaint.text.split("\n").filter { it.isNotBlank() }.forEach { paragraph ->
                    item {
                        Text(
                            highlightTemplatePlaceholders(paragraph, MaterialTheme.colorScheme.error),
                            fontSize = 24.sp,
                            lineHeight = 34.sp,
                        )
                    }
                }
                item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") } }
            }
        }
    }
}

@Composable
internal fun ComplaintTemplatePickerDialog(
    templates: List<ComplaintTemplate>,
    onDismiss: () -> Unit,
    onSelect: (ComplaintTemplate) -> Unit,
    title: String = "Выберите шаблон жалобы",
) {
    val sortedTemplates = templates.sortedBy { if (it.id.startsWith("complaint-roadmap-")) 0 else 1 }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            LazyColumn(
                Modifier.fillMaxWidth().imePadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text(title, style = MaterialTheme.typography.titleLarge) }
                item {
                    Text(
                        "После выбора будет создан черновик, который можно отредактировать в конструкторе.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                items(sortedTemplates, key = { "picker:${it.id}" }) { template ->
                    OutlinedButton(
                        onClick = { onSelect(template) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(template.title) }
                }
                item {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

private fun ComplaintStatus.label(): String = when (this) {
    ComplaintStatus.DRAFT -> "Черновик"
    ComplaintStatus.READY -> "Готова"
    ComplaintStatus.SUBMITTED -> "Подана"
    ComplaintStatus.ACCEPTED -> "Принята"
    ComplaintStatus.REJECTED -> "Отклонена"
    ComplaintStatus.UNKNOWN -> "Неизвестно"
}
