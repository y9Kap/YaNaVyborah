package org.yanavybori.feature.observer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yanavybori.core.common.SESSION_DELETION_PASSWORD_MIN_LENGTH
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.CounterMark
import org.yanavybori.core.model.CounterSession
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.ObservationSession
import org.yanavybori.core.model.VotingDayDefinition
import org.yanavybori.core.navigation.ObserverRoute
import org.yanavybori.core.ui.AppCard
import org.yanavybori.core.ui.DemoBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserverFeature(
    dependencies: ObserverDependencies,
    onBack: () -> Unit,
) {
    val viewModel: ObserverViewModel = viewModel(factory = ObserverViewModel.Factory(dependencies))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var routeHistory by rememberSaveable {
        mutableStateOf(arrayListOf(ObserverRoute.HOME.name))
    }
    var requestedReferenceDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val route = ObserverRoute.valueOf(routeHistory.last())
    val saveableStateHolder = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }

    fun navigateTo(target: ObserverRoute) {
        if (target == ObserverRoute.REFERENCES) requestedReferenceDocumentId = null
        if (target != route) {
            routeHistory = ArrayList(routeHistory).apply { add(target.name) }
        }
    }

    fun openReferenceDocument(documentId: String) {
        requestedReferenceDocumentId = documentId
        if (route != ObserverRoute.REFERENCES) {
            routeHistory = ArrayList(routeHistory).apply { add(ObserverRoute.REFERENCES.name) }
        }
    }

    fun navigateBack() {
        if (routeHistory.size > 1) {
            routeHistory = ArrayList(routeHistory.dropLast(1))
            requestedReferenceDocumentId = null
        } else {
            onBack()
        }
    }
    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbar.showSnackbar(message)
            viewModel.clearError()
        }
    }

    if (state.activeSession == null) {
        BackHandler(onBack = onBack)
        SessionSetupScreen(state, viewModel, onBack, snackbar)
        return
    }

    BackHandler(onBack = ::navigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(route.title(), maxLines = 1)
                        state.currentDay?.let { Text(it.shortTitle, style = MaterialTheme.typography.labelMedium) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::navigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            CompactCounterBar(
                counter = state.counters.firstOrNull { it.stoppedAt == null },
                lastMark = state.counters.firstOrNull { it.stoppedAt == null }
                    ?.let { counter -> state.counterLastMarks[counter.id] },
                onOpen = { navigateTo(ObserverRoute.COUNTERS) },
                onIncrement = viewModel::incrementCounter,
                onDecrement = viewModel::decrementCounter,
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding).fillMaxSize()
        saveableStateHolder.SaveableStateProvider(route.name) {
            when (route) {
                ObserverRoute.HOME -> ObserverHomeScreen(state, viewModel, contentModifier, ::navigateTo)
                ObserverRoute.CHECKLIST -> ChecklistScreen(state, viewModel, contentModifier, ::navigateTo)
                ObserverRoute.SITUATIONS -> SituationNavigatorScreen(
                    state,
                    viewModel,
                    contentModifier,
                    ::navigateTo,
                    ::openReferenceDocument,
                )
                ObserverRoute.JOURNAL -> JournalScreen(state, viewModel, contentModifier)
                ObserverRoute.COUNTERS -> CounterScreen(state, viewModel, contentModifier)
                ObserverRoute.RECONCILIATION -> ReconciliationScreen(state, viewModel, contentModifier)
                ObserverRoute.COMPLAINTS -> ComplaintsScreen(state, viewModel, contentModifier)
                ObserverRoute.LAWS -> LawsScreen(state, viewModel, contentModifier)
                ObserverRoute.POLICE -> PoliceScreen(
                    state,
                    viewModel,
                    contentModifier,
                    ::navigateTo,
                    ::openReferenceDocument,
                )
                ObserverRoute.PROTOCOL -> ProtocolScreen(state, viewModel, contentModifier, ::navigateTo)
                ObserverRoute.REFERENCES -> ReferenceDocumentsScreen(
                    state,
                    contentModifier,
                    requestedReferenceDocumentId,
                )
                ObserverRoute.EMERGENCY_CONTACTS -> EmergencyContactsScreen(state, contentModifier)
            }
        }
    }
}

@Composable
private fun SessionSetupScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var observerFullName by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var precinct by rememberSaveable { mutableStateOf("") }
    var precinctName by rememberSaveable { mutableStateOf("") }
    var commissionMembers by rememberSaveable { mutableStateOf("") }
    var deletionPassword by rememberSaveable { mutableStateOf("") }
    var deletionPasswordConfirmation by rememberSaveable { mutableStateOf("") }
    var selectedDayId by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.votingDays) {
        if (selectedDayId.isBlank()) selectedDayId = state.votingDays.minByOrNull { it.order }?.id.orEmpty()
    }
    val passwordIsValid = deletionPassword.length >= SESSION_DELETION_PASSWORD_MIN_LENGTH &&
        deletionPassword.isNotBlank() && deletionPassword == deletionPasswordConfirmation
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Новая сессия наблюдения", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
            }
            if (state.manifest?.isDemo == true) item { DemoBanner() }
            item {
                Text("Данные сохраняются только на этом устройстве. Регистрация и интернет не нужны.")
            }
            item {
                OutlinedTextField(
                    value = observerFullName,
                    onValueChange = { observerFullName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ФИО наблюдателя") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Регион") },
                    placeholder = { Text("Например: Москва") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = precinct,
                    onValueChange = { precinct = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Номер участка") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = precinctName,
                    onValueChange = { precinctName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название или адрес участка (необязательно)") },
                )
            }
            item {
                OutlinedTextField(
                    value = commissionMembers,
                    onValueChange = { commissionMembers = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ФИО членов комиссии (необязательно)") },
                    supportingText = { Text("Каждое ФИО — с новой строки") },
                    minLines = 2,
                    maxLines = 5,
                )
            }
            item { Text("Текущий день", style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.votingDays, key = { it.id }) { day ->
                        FilterChip(
                            selected = selectedDayId == day.id,
                            onClick = { selectedDayId = day.id },
                            label = { Text(day.shortTitle) },
                        )
                    }
                }
            }
            item { Text("Защита сессии", style = MaterialTheme.typography.titleMedium) }
            item {
                Text(
                    "Этот пароль понадобится для удаления сессии. Восстановить его через интернет нельзя.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = deletionPassword,
                    onValueChange = { deletionPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Пароль для удаления") },
                    supportingText = {
                        Text("Не менее $SESSION_DELETION_PASSWORD_MIN_LENGTH символов")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = deletionPasswordConfirmation,
                    onValueChange = { deletionPasswordConfirmation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Повторите пароль") },
                    supportingText = {
                        if (deletionPasswordConfirmation.isNotEmpty() &&
                            deletionPassword != deletionPasswordConfirmation
                        ) {
                            Text("Пароли не совпадают")
                        }
                    },
                    isError = deletionPasswordConfirmation.isNotEmpty() &&
                        deletionPassword != deletionPasswordConfirmation,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = {
                        viewModel.createSession(
                            observerFullName = observerFullName,
                            region = region,
                            precinctNumber = precinct,
                            precinctName = precinctName,
                            commissionMemberNames = commissionMembers.lines(),
                            deletionPassword = deletionPassword,
                            votingDayId = selectedDayId,
                        )
                    },
                    enabled = observerFullName.isNotBlank() && region.isNotBlank() &&
                        precinct.isNotBlank() && selectedDayId.isNotBlank() && passwordIsValid,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Начать наблюдение") }
            }
            item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") } }
        }
    }
}

@Composable
private fun ObserverHomeScreen(
    state: ObserverUiState,
    viewModel: ObserverViewModel,
    modifier: Modifier,
    navigate: (ObserverRoute) -> Unit,
) {
    val session = state.activeSession ?: return
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var eventDraft by remember { mutableStateOf<JournalEvent?>(null) }
    val dayDefinitionIds = state.checklistDefinitions
        .filter { session.currentVotingDay in it.votingDayIds }
        .flatMap { it.itemIds }
        .toSet()
    val done = state.checklistStates.count {
        it.checklistItemId in dayDefinitionIds && it.status != ChecklistStatus.NOT_CHECKED
    }
    val total = dayDefinitionIds.size
    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Наблюдаю за участком №${session.precinctNumber}", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            if (session.observerFullName.isNotBlank()) Text(session.observerFullName)
            if (session.region.isNotBlank()) {
                Text(session.region, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.manifest?.isDemo == true) item { DemoBanner() }
        item { DaySelector(state, viewModel) }
        item {
            Surface(
                onClick = { navigate(ObserverRoute.CHECKLIST) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Чек-лист", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Проверено: $done из $total")
                    Text(
                        "Открыть чек-лист текущего дня",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        item {
            ExtendedFloatingActionButton(
                onClick = { eventDraft = viewModel.newEventDraft() },
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Добавить событие или нарушение") },
            )
        }
        item { AppCard("Что происходит?", "Подсказки по типовым ситуациям", { navigate(ObserverRoute.SITUATIONS) }) }
        item { AppCard("Счётчики", "Несколько потоков, отметки +1/−1 и таймер", { navigate(ObserverRoute.COUNTERS) }) }
        item { AppCard("Сверки", "Поля и проверки из Election Pack", { navigate(ObserverRoute.RECONCILIATION) }) }
        item { AppCard("Жалобы", "Черновики и режим переписывания", { navigate(ObserverRoute.COMPLAINTS) }) }
        item { AppCard("Законы и справки", "Источник и версия пакета", { navigate(ObserverRoute.LAWS) }) }
        item { AppCard("Примеры документов", "Протоколы, жалобы и акты из сценариев", { navigate(ObserverRoute.REFERENCES) }) }
        item {
            AppCard(
                "Экстренные телефоны",
                "Экстренные службы и региональные контакты штаба",
                { navigate(ObserverRoute.EMERGENCY_CONTACTS) },
            )
        }
        item { AppCard("Журнал", "События и фильтр по дню", { navigate(ObserverRoute.JOURNAL) }) }
        item { AppCard("Протокол", "Ручной ввод, фото и snapshot", { navigate(ObserverRoute.PROTOCOL) }) }
        item { AppCard("Взаимодействие с полицией", "Спокойная фиксация фактов", { navigate(ObserverRoute.POLICE) }) }
        state.journalEvents.firstOrNull()?.let { event ->
            item {
                Text("Последнее событие", style = MaterialTheme.typography.titleMedium)
                Text(event.title, fontWeight = FontWeight.SemiBold)
                Text(formatTimestamp(event.timestamp), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Сессия наблюдения", fontWeight = FontWeight.Bold)
                    Text("Регион: ${session.region.ifBlank { "не указан" }}")
                    session.precinctName?.let { Text("Участок: $it") }
                    if (session.commissionMemberNames.isNotEmpty()) {
                        Text("Члены комиссии: ${session.commissionMemberNames.joinToString()}")
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Удалить сессию") }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    eventDraft?.let { draft ->
        NewJournalEventFlow(
            initial = draft,
            viewModel = viewModel,
            onDismiss = { eventDraft = null },
            onSave = {
                viewModel.createEvent(it)
                eventDraft = null
            },
        )
    }

    if (showDeleteDialog) {
        DeleteSessionDialog(
            session = session,
            onDismiss = { showDeleteDialog = false },
            onSetPassword = { password, onSet -> viewModel.setDeletionPassword(password, onSet) },
            onDelete = { password ->
                viewModel.deleteSession(password) { showDeleteDialog = false }
            },
        )
    }
}

@Composable
private fun DeleteSessionDialog(
    session: ObservationSession,
    onDismiss: () -> Unit,
    onSetPassword: (String, () -> Unit) -> Unit,
    onDelete: (String) -> Unit,
) {
    var password by rememberSaveable(session.id, session.hasDeletionPassword) { mutableStateOf("") }
    var confirmation by rememberSaveable(session.id, session.hasDeletionPassword) { mutableStateOf("") }
    val isLegacySession = !session.hasDeletionPassword
    val canSetPassword = password.length >= SESSION_DELETION_PASSWORD_MIN_LENGTH &&
        password.isNotBlank() && password == confirmation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isLegacySession) "Защитить удаление" else "Удалить сессию?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isLegacySession) {
                        "Эта сессия создана в старой версии. Сначала назначьте ей пароль; после этого удаление будет доступно только по нему."
                    } else {
                        "Будут удалены чек-листы, журнал, жалобы, счётчики, сверки, снимки протоколов и связанные медиафайлы этой сессии. Действие необратимо."
                    },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isLegacySession) "Новый пароль" else "Пароль сессии") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                if (isLegacySession) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Повторите пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = confirmation.isNotEmpty() && confirmation != password,
                        supportingText = {
                            if (confirmation.isNotEmpty() && confirmation != password) {
                                Text("Пароли не совпадают")
                            } else {
                                Text("Не менее $SESSION_DELETION_PASSWORD_MIN_LENGTH символов")
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            if (isLegacySession) {
                Button(
                    onClick = {
                        onSetPassword(password) {
                            password = ""
                            confirmation = ""
                        }
                    },
                    enabled = canSetPassword,
                ) { Text("Установить пароль") }
            } else {
                Button(
                    onClick = { onDelete(password) },
                    enabled = password.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Удалить") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun DaySelector(state: ObserverUiState, viewModel: ObserverViewModel) {
    val session = state.activeSession ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Текущий день", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.votingDays, key = { it.id }) { day ->
                FilterChip(
                    selected = session.currentVotingDay == day.id,
                    onClick = { viewModel.selectDay(day) },
                    label = { Text(day.shortTitle) },
                )
            }
        }
    }
}

@Composable
private fun CompactCounterBar(
    counter: CounterSession?,
    lastMark: CounterMark?,
    onOpen: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
) {
    Surface(shadowElevation = 8.dp, tonalElevation = 3.dp) {
        if (counter == null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Создать быстрый счётчик")
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(counter.label, style = MaterialTheme.typography.labelMedium)
                        Text(
                            counter.currentValue.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(onClick = onOpen) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Text("Настроить", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                CounterLastAction(lastMark)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onDecrement(counter.id) },
                        enabled = counter.currentValue > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("−1", style = MaterialTheme.typography.titleLarge) }
                    Button(
                        onClick = { onIncrement(counter.id) },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text("+1", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

private fun ObserverRoute.title(): String = when (this) {
    ObserverRoute.HOME -> "Наблюдатель"
    ObserverRoute.CHECKLIST -> "Чек-лист"
    ObserverRoute.SITUATIONS -> "Что происходит?"
    ObserverRoute.JOURNAL -> "Журнал"
    ObserverRoute.COUNTERS -> "Счётчики"
    ObserverRoute.RECONCILIATION -> "Сверки"
    ObserverRoute.COMPLAINTS -> "Жалобы"
    ObserverRoute.LAWS -> "Законы и справки"
    ObserverRoute.POLICE -> "Полиция"
    ObserverRoute.PROTOCOL -> "Протокол"
    ObserverRoute.REFERENCES -> "Примеры документов"
    ObserverRoute.EMERGENCY_CONTACTS -> "Экстренные телефоны"
}

internal fun formatTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
