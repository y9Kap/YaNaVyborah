package org.yanavybori.feature.voter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yanavybori.core.crypto.Sha256
import org.yanavybori.core.ui.AppHelpButton
import org.yanavybori.core.ui.BackHandler
import org.yanavybori.core.ui.DocumentRequest
import org.yanavybori.core.ui.LocalPlatformUi

private enum class VoterSection(val label: String) {
    RECOMMENDATIONS("Рекомендации"),
    MY_PLAN("Мой выбор"),
    BALLOT_ARCHIVE("Мой бюллетень"),
    GUIDE("Безопасность и права"),
}

private enum class ImportMode(val label: String) {
    FILE("Файл JSON"),
    URL("HTTPS-ссылка"),
}

internal data class VoterGuideSection(val title: String, val points: List<String>)

internal val voterGuideSections = listOf(
    VoterGuideSection(
        "Подготовьтесь заранее",
        listOf(
            "Уточните свой избирательный участок и возьмите паспорт или заменяющий его документ.",
            "Зарядите телефон, возьмите пауэрбанк, обновите систему и приложения; используйте длинный пароль, а не только отпечаток или лицо.",
            "Выберите доверенного человека и заранее договоритесь, что он делает, если вы перестанете выходить на связь.",
            "При высоком риске задержания заранее найдите защитника; основной телефон безопаснее выключить и оставить дома.",
            "Возьмите необходимые лекарства, воду и небольшой перекус.",
        ),
    ),
    VoterGuideSection(
        "Ваши права на участке",
        listOf(
            "Участие добровольное, а голосование тайное. Никто, включая работодателя и полицию, не вправе требовать показать выбор, фото бюллетеня или скриншот электронного голосования.",
            "Если вы имеете право голосовать на участке, комиссия выдаёт бюллетень под подпись. Даже при наличии терминала можно попросить бумажный бюллетень.",
            "Случайно испорченный бюллетень можно заменить: старый должна погасить комиссия, класть его в ящик не нужно.",
            "На участке должен быть нейтральный информационный стенд о кандидатах; агитация в помещении для голосования запрещена.",
            "Ошибку или отказ во включении в список можно потребовать исправить и оформить письменно, а решение — обжаловать.",
        ),
    ),
    VoterGuideSection(
        "Если право нарушают",
        listOf(
            "Спокойно обратитесь к члену комиссии. Если это не помогло, позовите председателя и подайте письменную жалобу с описанием времени и обстоятельств.",
            "Попросите зарегистрировать жалобу и оставьте себе копию с отметкой о принятии.",
            "Обратитесь к независимому наблюдателю, если он есть, либо подайте жалобу в вышестоящую комиссию или сразу в суд.",
            "Запишите ход событий, данные участников и свидетелей. Съёмку прекращайте, если она повышает риск для вас.",
            "Телефонный звонок на горячую линию сам по себе не заменяет формально поданную жалобу.",
        ),
    ),
    VoterGuideSection(
        "Полиция и задержание",
        listOf(
            "Говорите спокойно, не оказывайте физического сопротивления и не прикасайтесь к сотрудникам.",
            "Попросите сотрудника представиться, показать удостоверение и объяснить причину обращения. Если вам не дают уйти, спросите, задержаны ли вы.",
            "При первой возможности сообщите доверенному человеку, где вы, и зафиксируйте фактическое время задержания.",
            "При изъятии телефона просите указать основание, составить протокол и выдать его копию. Вы вправе не сообщать пароль и воспользоваться статьёй 51 Конституции.",
            "Прочитайте протокол до подписи, внесите замечания и потребуйте копию. Просите допустить выбранного защитника.",
        ),
    ),
    VoterGuideSection(
        "Фото и бюллетень",
        listOf(
            "Не снимайте чужой заполненный бюллетень и голосование другого человека в кабинке; не мешайте проходу и работе комиссии.",
            "Публикацию фото собственного заполненного бюллетеня в дни голосования могут попытаться квалифицировать как незаконную агитацию.",
            "Сделать свой бюллетень недействительным само по себе не запрещено, но надпись или рисунок могут создать отдельный правовой риск.",
            "Не повреждайте ящик и чужие бюллетени: за это возможна административная или уголовная ответственность.",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoterScreen(onBack: () -> Unit, onWorkPressure: () -> Unit) {
    BackHandler(onBack = onBack)
    val platform = LocalPlatformUi.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val loadedState = remember(platform) {
        runCatching { RecommendationJson.decodeState(platform.loadPrivateText(VOTER_STATE_KEY)) }
    }
    var state by remember { mutableStateOf(loadedState.getOrDefault(VoterLocalState())) }
    var selectedName by rememberSaveable { mutableStateOf(VoterSection.RECOMMENDATIONS.name) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var importModeName by rememberSaveable { mutableStateOf(ImportMode.FILE.name) }
    var importDisplayName by rememberSaveable { mutableStateOf("") }
    var importUrl by rememberSaveable { mutableStateOf("") }
    var importSha256 by rememberSaveable { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var deleteSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBallotDraft by remember { mutableStateOf<BallotDraft?>(null) }
    var savingBallot by remember { mutableStateOf(false) }
    var ballotStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var exportBallotId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteBallotId by rememberSaveable { mutableStateOf<String?>(null) }

    var region by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var precinct by rememberSaveable { mutableStateOf("") }
    var ballotType by rememberSaveable { mutableStateOf("") }
    var selectedSetIdsText by rememberSaveable { mutableStateOf("") }
    var interactionModeName by rememberSaveable {
        mutableStateOf(RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY.name)
    }
    var prioritySetId by rememberSaveable { mutableStateOf("") }
    var interactionSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var appliedRegion by rememberSaveable { mutableStateOf("") }
    var appliedCity by rememberSaveable { mutableStateOf("") }
    var appliedDistrict by rememberSaveable { mutableStateOf("") }
    var appliedPrecinct by rememberSaveable { mutableStateOf("") }
    var appliedBallotType by rememberSaveable { mutableStateOf("") }
    var appliedSetIdsText by rememberSaveable { mutableStateOf("") }
    var appliedInteractionModeName by rememberSaveable {
        mutableStateOf(RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY.name)
    }
    var appliedPrioritySetId by rememberSaveable { mutableStateOf("") }
    var searchRequest by rememberSaveable { mutableStateOf(0) }
    val listState = rememberLazyListState()

    val commit: (VoterLocalState) -> Unit = { next ->
        runCatching { platform.savePrivateText(VOTER_STATE_KEY, RecommendationJson.encodeState(next)) }
            .onSuccess { state = next }
            .onFailure { error -> scope.launch { snackbar.showSnackbar(error.userMessage("Не удалось сохранить данные")) } }
    }

    fun addImported(raw: String, source: String, displayName: String, expectedHash: String) {
        runCatching { RecommendationJson.import(raw, displayName, source, expectedHash) }
            .onSuccess { imported ->
                val next = state.copy(
                    recommendationSets = state.recommendationSets.filterNot { it.id == imported.id } + imported,
                )
                commit(next)
                showImport = false
                scope.launch { snackbar.showSnackbar("Набор «${imported.displayName}» добавлен") }
            }
            .onFailure { error -> scope.launch { snackbar.showSnackbar(error.userMessage("Не удалось импортировать JSON")) } }
        importing = false
    }

    val jsonPicker = platform.rememberJsonDocumentPicker { handle ->
        if (handle != null) {
            importing = true
            scope.launch {
                runCatching { platform.readPickedDocument(handle, MAX_RECOMMENDATION_JSON_BYTES) }
                    .onSuccess { document ->
                        runCatching { document.bytes.decodeToString(throwOnInvalidSequence = true) }
                            .onSuccess { raw ->
                                addImported(raw, "Файл: ${document.name}", importDisplayName, importSha256)
                            }
                            .onFailure { error ->
                                importing = false
                                snackbar.showSnackbar(error.userMessage("Файл должен быть в UTF-8"))
                            }
                    }
                    .onFailure { error ->
                        importing = false
                        snackbar.showSnackbar(error.userMessage("Не удалось прочитать файл"))
                    }
            }
        }
    }

    val ballotPhotoPicker = platform.rememberMediaPicker { handle ->
        val draft = pendingBallotDraft
        pendingBallotDraft = null
        if (handle != null && draft != null) scope.launch {
            savingBallot = true
            ballotStatus = null
            var savedStorageKey: String? = null
            val result = runCatching {
                val selected = platform.readPickedDocument(handle, MAX_BALLOT_PHOTO_SOURCE_BYTES)
                val photo = platform.sanitizeImage(selected.bytes, BALLOT_PHOTO_MAX_DIMENSION)
                require(photo.size <= MAX_BALLOT_PHOTO_STORED_BYTES) {
                    "После очистки фотография всё ещё слишком большая"
                }
                val payload = BallotArchiveJson.normalizedPayload(draft, Sha256.digest(photo))
                val signature = platform.signAnonymously(BallotArchiveJson.signingBytes(payload))
                val record = BallotArchiveJson.createRecord(payload, photo.size, signature)
                platform.savePrivateBytes(record.photoStorageKey, photo)
                savedStorageKey = record.photoStorageKey
                val next = state.copy(ballotRecords = state.ballotRecords.filterNot { it.id == record.id } + record)
                platform.savePrivateText(VOTER_STATE_KEY, RecommendationJson.encodeState(next))
                state = next
                record
            }
            result.onSuccess {
                ballotStatus = "Фото очищено от метаданных, подписано и сохранено локально"
            }.onFailure { error ->
                savedStorageKey?.takeIf { key -> state.ballotRecords.none { it.photoStorageKey == key } }
                    ?.let { key -> runCatching { platform.deletePrivateBytes(key) } }
                ballotStatus = error.userMessage("Не удалось сохранить бюллетень")
            }
            savingBallot = false
        }
    }

    val ballotExportLauncher = platform.rememberDocumentCreator { handle ->
        val record = state.ballotRecords.firstOrNull { it.id == exportBallotId }
        exportBallotId = null
        if (handle != null && record != null) scope.launch {
            val result = runCatching {
                val photo = requireNotNull(platform.loadPrivateBytes(record.photoStorageKey)) {
                    "Сохранённая фотография не найдена"
                }
                platform.writeDocument(handle, BallotArchiveJson.export(record, photo))
            }
            ballotStatus = result.fold(
                onSuccess = { "Анонимный подписанный пакет сохранён" },
                onFailure = { it.userMessage("Не удалось выгрузить пакет") },
            )
        }
    }

    LaunchedEffect(loadedState.isFailure) {
        if (loadedState.isFailure) snackbar.showSnackbar("Локальные списки повреждены и не были открыты")
    }

    LaunchedEffect(platform, state.bundledRecommendationVersion) {
        if (state.bundledRecommendationVersion < BUNDLED_RECOMMENDATION_VERSION) {
            runCatching {
                val rawFiles = BUNDLED_RECOMMENDATION_PATHS.map { path ->
                    platform.readBundledFile(path).decodeToString(throwOnInvalidSequence = true)
                }
                RecommendationJson.installBundled(rawFiles, state)
            }.onSuccess { next -> commit(next) }.onFailure { error ->
                snackbar.showSnackbar(error.userMessage("Не удалось открыть встроенные списки"))
            }
        }
    }

    LaunchedEffect(searchRequest) {
        if (searchRequest > 0 && selectedName == VoterSection.RECOMMENDATIONS.name) {
            listState.animateScrollToItem(RECOMMENDATION_RESULTS_ITEM_INDEX)
        }
    }

    if (showImport) {
        ImportDialog(
            mode = ImportMode.valueOf(importModeName),
            displayName = importDisplayName,
            url = importUrl,
            sha256 = importSha256,
            importing = importing,
            onModeChange = { importModeName = it.name },
            onDisplayNameChange = { importDisplayName = it },
            onUrlChange = { importUrl = it },
            onSha256Change = { importSha256 = it },
            onDismiss = { if (!importing) showImport = false },
            onPickFile = { jsonPicker.launch() },
            onDownload = {
                importing = true
                scope.launch {
                    runCatching { platform.fetchHttpsText(importUrl, MAX_RECOMMENDATION_JSON_BYTES) }
                        .onSuccess { raw -> addImported(raw, importUrl.trim(), importDisplayName, importSha256) }
                        .onFailure { error ->
                            importing = false
                            snackbar.showSnackbar(error.userMessage("Не удалось скачать JSON"))
                        }
                }
            },
        )
    }

    deleteSetId?.let { id ->
        val set = state.recommendationSets.firstOrNull { it.id == id }
        if (set != null) {
            AlertDialog(
                onDismissRequest = { deleteSetId = null },
                title = { Text("Удалить набор?") },
                text = { Text("«${set.displayName}» будет удалён только с этого устройства.") },
                confirmButton = {
                    TextButton(onClick = {
                        commit(state.copy(recommendationSets = state.recommendationSets.filterNot { it.id == id }))
                        deleteSetId = null
                    }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { deleteSetId = null }) { Text("Отмена") } },
            )
        }
    }


    deleteBallotId?.let { id ->
        val record = state.ballotRecords.firstOrNull { it.id == id }
        if (record != null) {
            AlertDialog(
                onDismissRequest = { deleteBallotId = null },
                title = { Text("Удалить фото бюллетеня?") },
                text = { Text("Локальная запись и фотография будут удалены с этого устройства.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteBallotId = null
                        scope.launch {
                            runCatching {
                                platform.deletePrivateBytes(record.photoStorageKey)
                                val next = state.copy(ballotRecords = state.ballotRecords.filterNot { it.id == id })
                                platform.savePrivateText(VOTER_STATE_KEY, RecommendationJson.encodeState(next))
                                state = next
                            }.onFailure { error ->
                                snackbar.showSnackbar(error.userMessage("Не удалось удалить запись"))
                            }
                        }
                    }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { deleteBallotId = null }) { Text("Отмена") } },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Избиратель") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { AppHelpButton() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LocalOnlyNotice() }
            item {
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VoterSection.entries.forEach { section ->
                        FilterChip(
                            selected = selectedName == section.name,
                            onClick = { selectedName = section.name },
                            label = { Text(section.label) },
                        )
                    }
                }
            }

            when (VoterSection.valueOf(selectedName)) {
                VoterSection.RECOMMENDATIONS -> recommendationsContent(
                    state = state,
                    region = region,
                    city = city,
                    district = district,
                    precinct = precinct,
                    ballotType = ballotType,
                    selectedSetIds = selectedSetIdsText.toIdSet(),
                    interactionMode = RecommendationInteractionMode.valueOf(interactionModeName),
                    prioritySetId = prioritySetId,
                    interactionSettingsExpanded = interactionSettingsExpanded,
                    appliedRegion = appliedRegion,
                    appliedCity = appliedCity,
                    appliedDistrict = appliedDistrict,
                    appliedPrecinct = appliedPrecinct,
                    appliedBallotType = appliedBallotType,
                    appliedSetIds = appliedSetIdsText.toIdSet(),
                    appliedInteractionMode = RecommendationInteractionMode.valueOf(appliedInteractionModeName),
                    appliedPrioritySetId = appliedPrioritySetId,
                    hasSearched = searchRequest > 0,
                    onRegionChange = { region = it },
                    onCityChange = { city = it },
                    onDistrictChange = { district = it },
                    onPrecinctChange = { precinct = it },
                    onBallotTypeChange = { ballotType = it },
                    onSelectedSetIdsChange = { selectedSetIdsText = it.toIdText() },
                    onInteractionModeChange = { interactionModeName = it.name },
                    onPrioritySetIdChange = { prioritySetId = it },
                    onInteractionSettingsExpandedChange = { interactionSettingsExpanded = it },
                    onSearch = {
                        appliedRegion = region
                        appliedCity = city
                        appliedDistrict = district
                        appliedPrecinct = precinct
                        appliedBallotType = ballotType
                        appliedSetIdsText = selectedSetIdsText
                        appliedInteractionModeName = interactionModeName
                        appliedPrioritySetId = prioritySetId
                        searchRequest += 1
                    },
                    onImport = {
                        importDisplayName = ""
                        importUrl = ""
                        importSha256 = ""
                        showImport = true
                    },
                    onDelete = { deleteSetId = it },
                    onAddToPlan = { set, recommendation ->
                        val choice = PersonalVoteChoice(
                            id = personalChoiceId(recommendation, set.displayName),
                            region = recommendation.region,
                            city = recommendation.city,
                            district = recommendation.district,
                            districtNumber = recommendation.districtNumber,
                            ballotType = recommendation.ballotType,
                            choice = recommendation.choice,
                            note = listOfNotNull(
                                "Из набора «${set.displayName}»",
                                recommendation.note,
                            ).joinToString(". "),
                        )
                        commit(state.copy(personalChoices = state.personalChoices.filterNot { it.id == choice.id } + choice))
                        scope.launch { snackbar.showSnackbar("Добавлено в «Мой выбор»") }
                    },
                )

                VoterSection.MY_PLAN -> personalPlanContent(
                    state = state,
                    defaultRegion = region,
                    defaultCity = city,
                    defaultDistrict = district,
                    defaultBallotType = ballotType,
                    onAdd = { choice -> commit(state.copy(personalChoices = state.personalChoices.filterNot { it.id == choice.id } + choice)) },
                    onDelete = { id -> commit(state.copy(personalChoices = state.personalChoices.filterNot { it.id == id })) },
                )

                VoterSection.BALLOT_ARCHIVE -> ballotArchiveContent(
                    records = state.ballotRecords,
                    saving = savingBallot,
                    status = ballotStatus,
                    defaultRegion = region,
                    defaultPrecinct = precinct,
                    defaultBallotType = ballotType,
                    onPressureHelp = onWorkPressure,
                    onAdd = { draft ->
                        pendingBallotDraft = draft
                        ballotPhotoPicker.launch(imagesOnly = true)
                    },
                    onExport = { record ->
                        exportBallotId = record.id
                        ballotExportLauncher.launch(
                            DocumentRequest(ballotExportFileName(record), "application/json"),
                        )
                    },
                    onDelete = { deleteBallotId = it.id },
                )

                VoterSection.GUIDE -> guideContent(platform::openExternalLink)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recommendationsContent(
    state: VoterLocalState,
    region: String,
    city: String,
    district: String,
    precinct: String,
    ballotType: String,
    selectedSetIds: Set<String>,
    interactionMode: RecommendationInteractionMode,
    prioritySetId: String,
    interactionSettingsExpanded: Boolean,
    appliedRegion: String,
    appliedCity: String,
    appliedDistrict: String,
    appliedPrecinct: String,
    appliedBallotType: String,
    appliedSetIds: Set<String>,
    appliedInteractionMode: RecommendationInteractionMode,
    appliedPrioritySetId: String,
    hasSearched: Boolean,
    onRegionChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onDistrictChange: (String) -> Unit,
    onPrecinctChange: (String) -> Unit,
    onBallotTypeChange: (String) -> Unit,
    onSelectedSetIdsChange: (Set<String>) -> Unit,
    onInteractionModeChange: (RecommendationInteractionMode) -> Unit,
    onPrioritySetIdChange: (String) -> Unit,
    onInteractionSettingsExpandedChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onAddToPlan: (ImportedRecommendationSet, VoteRecommendation) -> Unit,
) {
    item {
        Text(
            "Сравнивайте предложения разных политических акторов. Приложение не рекомендует источник и не выбирает за вас.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    item {
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Добавить список", Modifier.padding(start = 8.dp))
        }
    }
    item {
        LocationFilters(
            region,
            city,
            district,
            precinct,
            ballotType,
            state.recommendationSets,
            selectedSetIds,
            interactionMode,
            prioritySetId,
            interactionSettingsExpanded,
            onRegionChange,
            onCityChange,
            onDistrictChange,
            onPrecinctChange,
            onBallotTypeChange,
            onSelectedSetIdsChange,
            onInteractionModeChange,
            onPrioritySetIdChange,
            onInteractionSettingsExpandedChange,
            onSearch,
        )
    }
    if (state.recommendationSets.isEmpty()) {
        item {
            InfoCard(
                "Списков пока нет",
                "Загрузите JSON, который вы уже проверили, или вставьте HTTPS-ссылку. Загрузка начнётся только после нажатия кнопки.",
            )
        }
    } else if (!hasSearched) {
        item {
            InfoCard(
                "Укажите параметры поиска",
                "По умолчанию поиск выполняется по всем спискам. Заполните известные поля и нажмите «Найти».",
            )
        }
    } else {
        val matchingSelectedSets = state.recommendationSets.filter { it.id in appliedSetIds }
        val selectedSets = if (appliedSetIds.isEmpty() || matchingSelectedSets.isEmpty()) {
            state.recommendationSets
        } else {
            matchingSelectedSets
        }
        val effectivePrioritySetId = appliedPrioritySetId
            .takeIf { id -> selectedSets.any { it.id == id } }
            ?: defaultRecommendationPrioritySetId(selectedSets)
        val resolvedMatches = resolveRecommendationMatches(
            sets = selectedSets,
            regionQuery = appliedRegion,
            cityQuery = appliedCity,
            districtQuery = appliedDistrict,
            precinctQuery = appliedPrecinct,
            ballotTypeQuery = appliedBallotType,
            interactionMode = appliedInteractionMode,
            prioritySetId = effectivePrioritySetId,
        )
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (hasSearched) "Результаты поиска" else "Все рекомендации",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Найдено ${resolvedMatches.sumOf { it.recommendations.size }}; " +
                        "источников с результатами ${resolvedMatches.count { it.recommendations.isNotEmpty() }} из ${selectedSets.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (appliedInteractionMode == RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY) {
                    Text(
                        "Совпадения из дополняющих списков скрыты, если приоритетный список уже заполнил тот же бюллетень и округ.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(resolvedMatches, key = { result -> result.set.id }) { result ->
            val set = result.set
            val interactionNote = when {
                appliedInteractionMode == RecommendationInteractionMode.COMPARE_ALL -> null
                set.id == effectivePrioritySetId -> "Приоритетный список"
                result.recommendations.isNotEmpty() -> "Дополняет отсутствующие округа и типы бюллетеней"
                result.suppressedByPriority > 0 -> "Все совпадения уже покрыты списками с более высоким приоритетом"
                else -> "Для выбранных фильтров дополнений нет"
            }
            RecommendationSetCard(
                set = set,
                filtered = result.recommendations,
                containerColor = recommendationSetColor(state.recommendationSets.indexOf(set)),
                interactionNote = interactionNote,
                onDelete = { onDelete(set.id) },
                onAddToPlan = { onAddToPlan(set, it) },
            )
        }
    }
}

@Composable
private fun LocalOnlyNotice() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Решаете только вы", fontWeight = FontWeight.Bold)
                Text(
                    "Списки, ваш план и фото бюллетеня хранятся локально. Приложение ничего не отправляет: сеть используется только для подтверждённой загрузки HTTPS-ссылки, а выгрузку файла запускаете вы.",
                )
            }
        }
    }
}

@Composable
private fun LocationFilters(
    region: String,
    city: String,
    district: String,
    precinct: String,
    ballotType: String,
    recommendationSets: List<ImportedRecommendationSet>,
    selectedSetIds: Set<String>,
    interactionMode: RecommendationInteractionMode,
    prioritySetId: String,
    interactionSettingsExpanded: Boolean,
    onRegionChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onDistrictChange: (String) -> Unit,
    onPrecinctChange: (String) -> Unit,
    onBallotTypeChange: (String) -> Unit,
    onSelectedSetIdsChange: (Set<String>) -> Unit,
    onInteractionModeChange: (RecommendationInteractionMode) -> Unit,
    onPrioritySetIdChange: (String) -> Unit,
    onInteractionSettingsExpandedChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    val availableSetIds = recommendationSets.mapTo(mutableSetOf()) { it.id }
    val effectiveSelectedSetIds = selectedSetIds.intersect(availableSetIds)
    val effectivePrioritySetId = prioritySetId
        .takeIf { it in availableSetIds }
        ?: defaultRecommendationPrioritySetId(recommendationSets)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Найти рекомендации для себя", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Заполните нужные поля и нажмите «Найти». Пустые поля не ограничивают поиск.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(region, onRegionChange, label = { Text("Регион") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(city, onCityChange, label = { Text("Город / поселение") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(district, onDistrictChange, label = { Text("Округ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                precinct,
                onPrecinctChange,
                label = { Text("Номер УИК, если нужен") },
                supportingText = { Text("Номер сравнивается целиком: УИК 27 не совпадёт с УИК 127") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("Тип бюллетеня", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(selected = ballotType.isBlank(), onClick = { onBallotTypeChange("") }, label = { Text("Все") })
                knownBallotTypes.forEach { (value, label) ->
                    FilterChip(selected = ballotType == value, onClick = { onBallotTypeChange(value) }, label = { Text(label) })
                }
            }
            if (recommendationSets.isNotEmpty()) {
                Text("Искать в списках", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = effectiveSelectedSetIds.isEmpty(),
                        onClick = { onSelectedSetIdsChange(emptySet()) },
                        label = { Text("Во всех") },
                    )
                    recommendationSets.forEach { set ->
                        FilterChip(
                            selected = set.id in effectiveSelectedSetIds,
                            onClick = {
                                val next = if (set.id in effectiveSelectedSetIds) {
                                    effectiveSelectedSetIds - set.id
                                } else {
                                    effectiveSelectedSetIds + set.id
                                }
                                onSelectedSetIdsChange(next)
                            },
                            label = { Text(set.displayName) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onInteractionSettingsExpandedChange(!interactionSettingsExpanded) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (interactionSettingsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                    Text(
                        if (interactionMode == RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY) {
                            "Объединение: дополнять по приоритету"
                        } else {
                            "Объединение: показывать все варианты"
                        },
                        Modifier.padding(start = 8.dp),
                    )
                }
                if (interactionSettingsExpanded) {
                    Text(
                        "При дополнении рекомендация нижестоящего списка показывается только тогда, когда списки выше не заполнили тот же тип бюллетеня в этом округе или УИК.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Режим взаимодействия", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = interactionMode == RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY,
                            onClick = { onInteractionModeChange(RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY) },
                            label = { Text("Дополнять по приоритету") },
                        )
                        FilterChip(
                            selected = interactionMode == RecommendationInteractionMode.COMPARE_ALL,
                            onClick = { onInteractionModeChange(RecommendationInteractionMode.COMPARE_ALL) },
                            label = { Text("Показывать все варианты") },
                        )
                    }
                    if (interactionMode == RecommendationInteractionMode.COMPLEMENT_BY_PRIORITY) {
                        Text("Приоритетный список", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            recommendationSets.forEach { set ->
                                FilterChip(
                                    selected = set.id == effectivePrioritySetId,
                                    onClick = { onPrioritySetIdChange(set.id) },
                                    label = { Text(set.displayName) },
                                )
                            }
                        }
                    }
                }
            }
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Text("Найти", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun RecommendationSetCard(
    set: ImportedRecommendationSet,
    filtered: List<VoteRecommendation>,
    containerColor: Color,
    interactionNote: String?,
    onDelete: () -> Unit,
    onAddToPlan: (VoteRecommendation) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(set.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    set.pack.publisher?.let { Text("Автор списка: $it", style = MaterialTheme.typography.labelLarge) }
                    set.pack.election?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    set.pack.publishedAt?.let { Text("Дата набора: $it", style = MaterialTheme.typography.bodySmall) }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить набор") }
            }
            interactionNote?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            Text("Источник импорта: ${set.source}", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256 файла: ${set.rawSha256}", style = MaterialTheme.typography.bodySmall)
            if (set.expectedSha256 != null || set.pack.contentSha256 != null) {
                Text("Контрольная сумма проверена", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Внешняя контрольная сумма не была указана", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            if (filtered.isEmpty()) {
                Text("Для выбранных фильтров рекомендаций нет.")
            } else {
                Text("Найдено: ${filtered.size}", style = MaterialTheme.typography.labelLarge)
                filtered.forEach { item -> RecommendationCard(item, onAddToPlan) }
            }
        }
    }
}

@Composable
private fun recommendationSetColor(index: Int): Color = when (index % 3) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun RecommendationCard(item: VoteRecommendation, onAddToPlan: (VoteRecommendation) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ballotTypeLabel(item.ballotType), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(item.choice, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            item.party?.let { Text("Партия / объединение: $it") }
            item.candidateNumber?.let { Text("Номер: $it") }
            Text(scopeLabel(item), style = MaterialTheme.typography.bodySmall)
            item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { onAddToPlan(item) }, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить в мой выбор")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.personalPlanContent(
    state: VoterLocalState,
    defaultRegion: String,
    defaultCity: String,
    defaultDistrict: String,
    defaultBallotType: String,
    onAdd: (PersonalVoteChoice) -> Unit,
    onDelete: (String) -> Unit,
) {
    item {
        PersonalChoiceForm(defaultRegion, defaultCity, defaultDistrict, defaultBallotType, onAdd)
    }
    if (state.personalChoices.isEmpty()) {
        item { InfoCard("Ваш план пуст", "Добавьте свой вариант или перенесите рекомендацию из любого загруженного набора.") }
    } else {
        items(state.personalChoices, key = { it.id }) { choice ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ballotTypeLabel(choice.ballotType), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(choice.choice, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                choice.region,
                                choice.city,
                                choice.district,
                                choice.districtNumber?.let { "№ $it" },
                            ).joinToString(" · ").ifBlank { "Любая территория" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        choice.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    IconButton(onClick = { onDelete(choice.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Удалить из моего выбора")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ballotArchiveContent(
    records: List<SavedBallotRecord>,
    saving: Boolean,
    status: String?,
    defaultRegion: String,
    defaultPrecinct: String,
    defaultBallotType: String,
    onPressureHelp: () -> Unit,
    onAdd: (BallotDraft) -> Unit,
    onExport: (SavedBallotRecord) -> Unit,
    onDelete: (SavedBallotRecord) -> Unit,
) {
    item {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Warning, contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Фото — только добровольно", fontWeight = FontWeight.Bold)
                        Text(
                            "Никто не вправе требовать фото бюллетеня или доказательство вашего выбора. " +
                                "Если этого требует работодатель, учебное заведение, комиссия или другой человек, " +
                                "не делайте фото ради отчёта и откройте раздел помощи при давлении.",
                        )
                        Text(
                            "Публикация фото заполненного бюллетеня в дни голосования может создать правовой риск; " +
                                "самостоятельно выбирайте надёжного получателя выгрузки.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Button(onClick = onPressureHelp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Shield, contentDescription = null)
                    Text("На меня давят — открыть помощь", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    item {
        BallotRecordForm(
            saving = saving,
            defaultRegion = defaultRegion,
            defaultPrecinct = defaultPrecinct,
            defaultBallotType = defaultBallotType,
            onAdd = onAdd,
        )
    }
    status?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium) } }
    item {
        InfoCard(
            "Что попадёт в выгрузку",
            "Только выборы, регион, номер УИК, тип и отметка бюллетеня, необязательная дата, очищенное фото и проверяемая подпись. " +
                "Имя, локальный ID пользователя, имя исходного файла, координаты и точное время не добавляются. " +
                "Подпись защищает целостность пакета, но сама по себе не доказывает факт голосования.",
        )
    }
    if (records.isEmpty()) {
        item { InfoCard("Сохранённых бюллетеней нет", "Добровольно выберите фото после заполнения данных УИК.") }
    } else {
        items(records, key = { it.id }) { record ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.HowToVote, contentDescription = null)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("УИК № ${record.payload.precinctNumber}", fontWeight = FontWeight.Bold)
                            Text(record.payload.region)
                            Text(record.payload.election, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onDelete(record) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить бюллетень")
                        }
                    }
                    Text(ballotTypeLabel(record.payload.ballotType), color = MaterialTheme.colorScheme.primary)
                    Text("Отметка: ${record.payload.choice}")
                    record.payload.votingDate?.let { Text("Дата голосования: $it", style = MaterialTheme.typography.bodySmall) }
                    Text("SHA-256 фото: ${record.payload.photoSha256}", style = MaterialTheme.typography.bodySmall)
                    Text("Подпись: ${record.signature.algorithm}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onExport(record) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Text("Анонимно выгрузить пакет", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BallotRecordForm(
    saving: Boolean,
    defaultRegion: String,
    defaultPrecinct: String,
    defaultBallotType: String,
    onAdd: (BallotDraft) -> Unit,
) {
    var election by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable(defaultRegion) { mutableStateOf(defaultRegion) }
    var precinct by rememberSaveable(defaultPrecinct) { mutableStateOf(defaultPrecinct) }
    var ballotType by rememberSaveable(defaultBallotType) { mutableStateOf(defaultBallotType.ifBlank { "other" }) }
    var choice by rememberSaveable { mutableStateOf("") }
    var votingDate by rememberSaveable { mutableStateOf("") }
    var voluntary by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Фото и данные бюллетеня", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Перед сохранением приложение уменьшит фото, преобразует его в JPEG без EXIF-метаданных и подпишет данные новым одноразовым ключом.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(election, { election = it }, label = { Text("Выборы") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(region, { region = it }, label = { Text("Регион") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(precinct, { precinct = it }, label = { Text("Номер УИК") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Тип бюллетеня", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                knownBallotTypes.forEach { (value, label) ->
                    FilterChip(selected = ballotType == value, onClick = { ballotType = value }, label = { Text(label) })
                }
            }
            OutlinedTextField(
                choice,
                { choice = it },
                label = { Text("Отметка в бюллетене") },
                supportingText = { Text("Например, кандидат, партия, «против всех» или «недействительный»") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                votingDate,
                { votingDate = it },
                label = { Text("Дата голосования, необязательно") },
                supportingText = { Text("ГГГГ-ММ-ДД; точное время не сохраняется") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = voluntary, onCheckedChange = { voluntary = it })
                Text("Я делаю это добровольно, фото у меня никто не требует", Modifier.weight(1f))
            }
            Text(
                "Проверьте, что в кадре нет лица, паспорта, списков избирателей и других данных, способных раскрыть вашу личность.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = {
                    onAdd(
                        BallotDraft(
                            election = election,
                            region = region,
                            precinctNumber = precinct,
                            ballotType = ballotType,
                            choice = choice,
                            votingDate = votingDate.takeIf(String::isNotBlank),
                        ),
                    )
                },
                enabled = voluntary && !saving && election.isNotBlank() && region.isNotBlank() &&
                    precinct.isNotBlank() && ballotType.isNotBlank() && choice.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Text(if (saving) "Очищаем и подписываем…" else "Выбрать фото и сохранить", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PersonalChoiceForm(
    defaultRegion: String,
    defaultCity: String,
    defaultDistrict: String,
    defaultBallotType: String,
    onAdd: (PersonalVoteChoice) -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable(defaultRegion) { mutableStateOf(defaultRegion) }
    var city by rememberSaveable(defaultCity) { mutableStateOf(defaultCity) }
    var district by rememberSaveable(defaultDistrict) { mutableStateOf(defaultDistrict) }
    var ballotType by rememberSaveable(defaultBallotType) {
        mutableStateOf(defaultBallotType.ifBlank { "other" })
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Добавить свой вариант", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(choice, { choice = it }, label = { Text("Кандидат, партия или другой выбор") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Заметка, необязательно") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(region, { region = it }, label = { Text("Регион") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(city, { city = it }, label = { Text("Город") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(district, { district = it }, label = { Text("Округ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Тип бюллетеня", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                knownBallotTypes.forEach { (value, label) ->
                    FilterChip(selected = ballotType == value, onClick = { ballotType = value }, label = { Text(label) })
                }
            }
            Button(
                onClick = {
                    val newChoice = PersonalVoteChoice(
                        id = Sha256.digest("$region|$city|$district|$ballotType|$choice|$note".encodeToByteArray()),
                        region = region.trim().takeIf(String::isNotBlank),
                        city = city.trim().takeIf(String::isNotBlank),
                        district = district.trim().takeIf(String::isNotBlank),
                        ballotType = ballotType,
                        choice = choice.trim(),
                        note = note.trim().takeIf(String::isNotBlank),
                    )
                    onAdd(newChoice)
                    choice = ""
                    note = ""
                },
                enabled = choice.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Добавить", Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.guideContent(openLink: (String) -> Unit) {
    item { OvdInfoSourceCard(openLink) }
    items(voterGuideSections) { section ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                section.points.forEach { Text("• $it") }
            }
        }
    }
}

@Composable
private fun OvdInfoSourceCard(openLink: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Shield, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Источник и предупреждение", fontWeight = FontWeight.Bold)
                    Text(
                        "Краткое изложение инструкции ОВД-Инфо, обновлённой 11 сентября 2026 года. Медиапроект «ОВД-Инфо» включён Минюстом РФ в перечень экстремистских организаций. Учитывайте риск хранения материалов и переписки на устройстве.",
                    )
                }
            }
            Text("Это справка, а не юридическая консультация. Нормы и практика могут измениться.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { openLink(OVD_INFO_ELECTION_GUIDE_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("Открыть оригинал по моему действию", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ImportDialog(
    mode: ImportMode,
    displayName: String,
    url: String,
    sha256: String,
    importing: Boolean,
    onModeChange: (ImportMode) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSha256Change: (String) -> Unit,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить список") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Приложение сначала проверит структуру и контрольную сумму, затем сохранит набор локально.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportMode.entries.forEach { item ->
                        FilterChip(selected = mode == item, onClick = { onModeChange(item) }, label = { Text(item.label) })
                    }
                }
                OutlinedTextField(
                    displayName,
                    onDisplayNameChange,
                    label = { Text("Ваше название, необязательно") },
                    supportingText = { Text("Например: «Предложение ФБК» или «Список Максима Каца»") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importing,
                )
                if (mode == ImportMode.URL) {
                    OutlinedTextField(
                        url,
                        onUrlChange,
                        label = { Text("HTTPS-ссылка на JSON") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !importing,
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    sha256,
                    onSha256Change,
                    label = { Text("SHA-256 файла, рекомендуется") },
                    supportingText = { Text("64 символа. Возьмите хэш из отдельного доверенного канала.") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importing,
                    singleLine = true,
                )
                Text(
                    if (mode == ImportMode.URL) {
                        "Будет выполнен один HTTPS GET-запрос без cookies и учётных данных. В браузере сервер должен разрешать CORS."
                    } else {
                        "Выберите уже скачанный JSON-файл размером не более 2 МБ."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = if (mode == ImportMode.URL) onDownload else onPickFile,
                enabled = !importing && (mode == ImportMode.FILE || url.trim().startsWith("https://")),
            ) {
                Icon(
                    if (mode == ImportMode.URL) Icons.Outlined.Download else Icons.Outlined.UploadFile,
                    contentDescription = null,
                )
                Text(if (importing) "Проверяем…" else if (mode == ImportMode.URL) "Скачать и добавить" else "Выбрать файл")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !importing) { Text("Отмена") } },
    )
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text)
        }
    }
}

private fun scopeLabel(item: VoteRecommendation): String = listOfNotNull(
    item.region?.let { "Регион: $it" },
    item.city?.let { "Город: $it" },
    item.district?.let { "Округ: $it" },
    item.districtNumber?.let { "№ $it" },
    item.precinct?.let { "Участок: $it" },
).joinToString(" · ").ifBlank { "Для любой территории" }

private fun personalChoiceId(item: VoteRecommendation, sourceName: String): String = Sha256.digest(
    "$sourceName|${item.region}|${item.city}|${item.district}|${item.districtNumber}|${item.precinct}|${item.ballotType}|${item.choice}"
        .encodeToByteArray(),
)

private fun String.toIdSet(): Set<String> = split(',').filter(String::isNotBlank).toSet()

private fun Set<String>.toIdText(): String = sorted().joinToString(",")

private const val RECOMMENDATION_RESULTS_ITEM_INDEX = 5

private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf(String::isNotBlank)?.let { "$fallback: $it" } ?: fallback
