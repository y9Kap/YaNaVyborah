package org.yanavybori.feature.observer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yanavybori.core.common.Clock
import org.yanavybori.core.common.ChecklistStateUpdate
import org.yanavybori.core.common.ComplaintRepository
import org.yanavybori.core.common.CounterRepository
import org.yanavybori.core.common.ElectionPackRepository
import org.yanavybori.core.common.IdGenerator
import org.yanavybori.core.common.JournalEventFactory
import org.yanavybori.core.common.JournalRepository
import org.yanavybori.core.common.KnowledgeRepository
import org.yanavybori.core.common.MediaImportRequest
import org.yanavybori.core.common.MediaRepository
import org.yanavybori.core.common.ObservationRepository
import org.yanavybori.core.common.ProtocolRepository
import org.yanavybori.core.common.ReconciliationEngine
import org.yanavybori.core.common.ReconciliationRepository
import org.yanavybori.core.common.SystemClock
import org.yanavybori.core.common.UuidGenerator
import org.yanavybori.core.model.ChecklistDefinition
import org.yanavybori.core.model.ChecklistItem
import org.yanavybori.core.model.ChecklistItemState
import org.yanavybori.core.model.ChecklistStatus
import org.yanavybori.core.model.Complaint
import org.yanavybori.core.model.ComplaintStatus
import org.yanavybori.core.model.ComplaintTemplate
import org.yanavybori.core.model.CounterMark
import org.yanavybori.core.model.CounterSession
import org.yanavybori.core.model.ElectionPackManifest
import org.yanavybori.core.model.EventSeverity
import org.yanavybori.core.model.JournalCategory
import org.yanavybori.core.model.JournalEvent
import org.yanavybori.core.model.LawReference
import org.yanavybori.core.model.MediaAsset
import org.yanavybori.core.model.MediaSource
import org.yanavybori.core.model.ObservationSession
import org.yanavybori.core.model.ProtocolSnapshot
import org.yanavybori.core.model.ReconciliationDefinition
import org.yanavybori.core.model.ReconciliationSession
import org.yanavybori.core.model.ReferenceDocument
import org.yanavybori.core.model.SearchResult
import org.yanavybori.core.model.Situation
import org.yanavybori.core.model.SituationAudience
import org.yanavybori.core.model.VotingDayDefinition

data class ObserverDependencies(
    val electionPackRepository: ElectionPackRepository,
    val knowledgeRepository: KnowledgeRepository,
    val observationRepository: ObservationRepository,
    val journalRepository: JournalRepository,
    val complaintRepository: ComplaintRepository,
    val counterRepository: CounterRepository,
    val reconciliationRepository: ReconciliationRepository,
    val protocolRepository: ProtocolRepository,
    val mediaRepository: MediaRepository,
    val reconciliationEngine: ReconciliationEngine = ReconciliationEngine(),
    val clock: Clock = SystemClock,
    val ids: IdGenerator = UuidGenerator,
)

data class ObserverUiState(
    val manifest: ElectionPackManifest? = null,
    val votingDays: List<VotingDayDefinition> = emptyList(),
    val checklistDefinitions: List<ChecklistDefinition> = emptyList(),
    val checklistItems: List<ChecklistItem> = emptyList(),
    val checklistStates: List<ChecklistItemState> = emptyList(),
    val situations: List<Situation> = emptyList(),
    val laws: List<LawReference> = emptyList(),
    val complaintTemplates: List<ComplaintTemplate> = emptyList(),
    val reconciliationDefinitions: List<ReconciliationDefinition> = emptyList(),
    val referenceDocuments: List<ReferenceDocument> = emptyList(),
    val activeSession: ObservationSession? = null,
    val journalEvents: List<JournalEvent> = emptyList(),
    val complaints: List<Complaint> = emptyList(),
    val counters: List<CounterSession> = emptyList(),
    val counterLastMarks: Map<String, CounterMark> = emptyMap(),
    val reconciliationSessions: List<ReconciliationSession> = emptyList(),
    val protocolSnapshots: List<ProtocolSnapshot> = emptyList(),
    val mediaAssets: List<MediaAsset> = emptyList(),
    val mediaPreviews: Map<String, ByteArray> = emptyMap(),
    val mediaPreviewErrors: Set<String> = emptySet(),
    val searchResults: List<SearchResult> = emptyList(),
    val complaintToRevealId: String? = null,
    val errorMessage: String? = null,
) {
    val currentDay: VotingDayDefinition?
        get() = votingDays.firstOrNull { it.id == activeSession?.currentVotingDay }
}

class ObserverViewModel(
    private val dependencies: ObserverDependencies,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ObserverUiState())
    val state: StateFlow<ObserverUiState> = mutableState.asStateFlow()
    private var packJob: Job? = null
    private var sessionJob: Job? = null
    private val loadingMediaPreviewIds = mutableSetOf<String>()
    private val journalFactory = JournalEventFactory(dependencies.clock, dependencies.ids)

    init {
        viewModelScope.launch {
            dependencies.electionPackRepository.observeActiveManifest().collectLatest { manifest ->
                mutableState.update { it.copy(manifest = manifest) }
                packJob?.cancel()
                if (manifest != null) packJob = collectPack(manifest.id)
            }
        }
        viewModelScope.launch {
            dependencies.observationRepository.observeActiveSession().collectLatest { session ->
                mutableState.update { it.copy(activeSession = session) }
                sessionJob?.cancel()
                if (session != null) sessionJob = collectSession(session)
                else clearSessionData()
            }
        }
        viewModelScope.launch {
            dependencies.mediaRepository.observeMedia().collectLatest { media ->
                mutableState.update { it.copy(mediaAssets = media) }
            }
        }
    }

    private fun collectPack(packId: String) = viewModelScope.launch {
        coroutineScope {
            launch { dependencies.knowledgeRepository.observeVotingDays(packId).collectLatest { value -> mutableState.update { it.copy(votingDays = value) } } }
            launch { dependencies.knowledgeRepository.observeChecklistDefinitions(packId).collectLatest { value -> mutableState.update { it.copy(checklistDefinitions = value) } } }
            launch { dependencies.knowledgeRepository.observeChecklistItems(packId).collectLatest { value -> mutableState.update { it.copy(checklistItems = value) } } }
            launch { dependencies.knowledgeRepository.observeSituations(packId, SituationAudience.OBSERVER).collectLatest { value -> mutableState.update { it.copy(situations = value) } } }
            launch { dependencies.knowledgeRepository.observeLawReferences(packId).collectLatest { value -> mutableState.update { it.copy(laws = value) } } }
            launch { dependencies.knowledgeRepository.observeComplaintTemplates(packId).collectLatest { value -> mutableState.update { it.copy(complaintTemplates = value) } } }
            launch { dependencies.knowledgeRepository.observeReconciliationDefinitions(packId).collectLatest { value -> mutableState.update { it.copy(reconciliationDefinitions = value) } } }
            launch { dependencies.knowledgeRepository.observeReferenceDocuments(packId).collectLatest { value -> mutableState.update { it.copy(referenceDocuments = value) } } }
        }
    }

    private fun collectSession(session: ObservationSession) = viewModelScope.launch {
        mutableState.update { it.copy(counterLastMarks = emptyMap()) }
        coroutineScope {
            launch { dependencies.observationRepository.observeChecklistStates(session.id, session.currentVotingDay).collectLatest { value -> mutableState.update { it.copy(checklistStates = value) } } }
            launch { dependencies.journalRepository.observeEvents(session.id).collectLatest { value -> mutableState.update { it.copy(journalEvents = value) } } }
            launch { dependencies.complaintRepository.observeComplaints(session.id).collectLatest { value -> mutableState.update { it.copy(complaints = value) } } }
            launch { dependencies.counterRepository.observeCounters(session.id, session.currentVotingDay).collectLatest { value -> mutableState.update { it.copy(counters = value) } } }
            launch {
                dependencies.counterRepository
                    .observeLastMarksForDay(session.id, session.currentVotingDay)
                    .collectLatest { marks ->
                        val latest = marks.associateBy(CounterMark::counterSessionId)
                        mutableState.update { it.copy(counterLastMarks = latest) }
                    }
            }
            launch { dependencies.reconciliationRepository.observeSessions(session.id).collectLatest { value -> mutableState.update { it.copy(reconciliationSessions = value) } } }
            launch { dependencies.protocolRepository.observeSnapshots(session.id).collectLatest { value -> mutableState.update { it.copy(protocolSnapshots = value) } } }
        }
    }

    private fun clearSessionData() {
        mutableState.update {
            it.copy(
                checklistStates = emptyList(),
                journalEvents = emptyList(),
                complaints = emptyList(),
                counters = emptyList(),
                counterLastMarks = emptyMap(),
                reconciliationSessions = emptyList(),
                protocolSnapshots = emptyList(),
                mediaPreviews = emptyMap(),
                mediaPreviewErrors = emptySet(),
                complaintToRevealId = null,
            )
        }
    }

    fun createSession(
        observerFullName: String,
        region: String,
        precinctNumber: String,
        precinctName: String?,
        commissionMemberNames: List<String>,
        deletionPassword: String,
        votingDayId: String,
    ) = task {
        val manifest = requireNotNull(state.value.manifest) { "Election Pack ещё не готов" }
        val day = state.value.votingDays.first { it.id == votingDayId }
        dependencies.observationRepository.createSession(
            electionPackId = manifest.id,
            observerFullName = observerFullName,
            region = region,
            precinctNumber = precinctNumber,
            precinctName = precinctName,
            commissionMemberNames = commissionMemberNames,
            deletionPassword = deletionPassword,
            votingDayId = day.id,
            stageId = day.stages.minByOrNull { it.order }?.id.orEmpty(),
        )
    }

    fun setDeletionPassword(password: String, onSet: () -> Unit = {}) = task {
        val session = requireNotNull(state.value.activeSession)
        dependencies.observationRepository.setDeletionPassword(session.id, password)
        onSet()
    }

    fun deleteSession(password: String, onDeleted: () -> Unit = {}) = task {
        val session = requireNotNull(state.value.activeSession)
        val mediaIds = buildSet {
            state.value.journalEvents.flatMapTo(this) { it.mediaIds }
            state.value.complaints.mapNotNullTo(this) { it.acceptedCopyMediaId }
            state.value.reconciliationSessions.mapNotNullTo(this) { it.photoMediaId }
            state.value.protocolSnapshots.mapNotNullTo(this) { it.photoMediaId }
        }
        dependencies.observationRepository.deleteSession(session.id, password)
        mediaIds.forEach { dependencies.mediaRepository.delete(it) }
        onDeleted()
    }

    fun selectDay(day: VotingDayDefinition) = task {
        val session = requireNotNull(state.value.activeSession)
        dependencies.observationRepository.updateDayAndStage(
            session.id,
            day.id,
            day.stages.minByOrNull { it.order }?.id.orEmpty(),
        )
    }

    fun setChecklistState(
        item: ChecklistItem,
        status: ChecklistStatus,
        onUpdated: (ChecklistStateUpdate) -> Unit = {},
    ) = task {
        val session = requireNotNull(state.value.activeSession)
        val update = dependencies.observationRepository.setChecklistState(
            session.id,
            session.currentVotingDay,
            item.id,
            status,
        )
        val event = update.journalEvent?.copy(
            title = item.title,
            description = "Чек-лист: ${status.labelForJournal()}. ${item.shortExplanation}",
        )
        if (event != null) dependencies.journalRepository.update(event)
        onUpdated(update.copy(journalEvent = event))
    }

    fun newEventDraft(): JournalEvent {
        val session = requireNotNull(state.value.activeSession)
        return journalFactory.quick(session.id, session.currentVotingDay)
    }

    fun createEvent(event: JournalEvent) = task {
        val session = requireNotNull(state.value.activeSession)
        require(event.sessionId == session.id) { "Событие относится к другой сессии" }
        dependencies.journalRepository.create(event)
    }

    fun createEventFromSituation(situation: Situation) = task {
        val session = requireNotNull(state.value.activeSession)
        dependencies.journalRepository.create(
            JournalEvent(
                id = dependencies.ids.newId(),
                sessionId = session.id,
                votingDayId = session.currentVotingDay,
                timestamp = dependencies.clock.now(),
                category = when (situation.category) {
                    org.yanavybori.core.model.SituationCategory.POLICE_INTERACTION -> JournalCategory.POLICE
                    else -> JournalCategory.PROBLEM
                },
                severity = EventSeverity.ATTENTION,
                title = situation.title,
                description = situation.summary,
                relatedSituationId = situation.id,
                tags = situation.tags,
            ),
        )
    }

    fun updateEvent(event: JournalEvent) = task { dependencies.journalRepository.update(event) }

    fun attachMediaToEvent(eventId: String, mediaId: String) = task {
        val event = requireNotNull(dependencies.journalRepository.getEvent(eventId)) {
            "Связанная запись журнала не найдена"
        }
        dependencies.journalRepository.update(
            event.copy(mediaIds = (event.mediaIds + mediaId).distinct()),
        )
    }

    fun createComplaint(
        template: ComplaintTemplate,
        relatedEventIds: List<String> = emptyList(),
        contextNotes: String = "",
        onCreated: (Complaint) -> Unit = {},
    ) = task {
        val session = requireNotNull(state.value.activeSession)
        val complaint = dependencies.complaintRepository.create(
            Complaint(
                id = dependencies.ids.newId(),
                sessionId = session.id,
                relatedEventIds = relatedEventIds,
                templateId = template.id,
                recipient = template.defaultRecipient,
                createdAt = dependencies.clock.now(),
                status = ComplaintStatus.DRAFT,
                text = template.body,
                notes = contextNotes,
            ),
        )
        relatedEventIds.forEach { eventId ->
            dependencies.journalRepository.getEvent(eventId)?.let { event ->
                dependencies.journalRepository.update(
                    event.copy(relatedComplaintIds = (event.relatedComplaintIds + complaint.id).distinct()),
                )
            }
        }
        mutableState.update { it.copy(complaintToRevealId = complaint.id) }
        onCreated(complaint)
    }

    fun createComplaintForSituation(situation: Situation) {
        val template = state.value.complaintTemplates.firstOrNull { it.id == situation.complaintTemplateId }
        if (template == null) setError("Для этой ситуации шаблон не задан") else createComplaint(template)
    }

    fun updateComplaint(complaint: Complaint) = task { dependencies.complaintRepository.update(complaint) }

    fun consumeComplaintReveal(complaintId: String) = mutableState.update { current ->
        if (current.complaintToRevealId == complaintId) current.copy(complaintToRevealId = null) else current
    }

    fun updateComplaintStatus(complaint: Complaint, status: ComplaintStatus) = task {
        dependencies.complaintRepository.updateStatus(complaint.id, status)
    }

    fun attachAcceptedCopy(complaint: Complaint, mediaId: String) =
        updateComplaint(complaint.copy(acceptedCopyMediaId = mediaId))

    fun createCounter(label: String) = task {
        val session = requireNotNull(state.value.activeSession)
        dependencies.counterRepository.createCounter(session.id, session.currentVotingDay, label)
    }

    fun incrementCounter(counterId: String) = task { dependencies.counterRepository.increment(counterId) }
    fun decrementCounter(counterId: String) = task { dependencies.counterRepository.decrement(counterId) }
    fun undoCounter(counterId: String) = task { dependencies.counterRepository.undoLast(counterId) }
    fun stopCounter(counterId: String) = task { dependencies.counterRepository.stop(counterId) }

    fun saveReconciliation(
        definition: ReconciliationDefinition,
        values: Map<String, String>,
        photoMediaId: String?,
        onSaved: (ReconciliationSession) -> Unit = {},
    ) = task {
        val session = requireNotNull(state.value.activeSession)
        val existing = state.value.reconciliationSessions.firstOrNull {
            it.definitionId == definition.id && it.votingDayId == session.currentVotingDay
        }
        val now = dependencies.clock.now()
        val previous = state.value.reconciliationSessions
            .firstOrNull { it.definitionId == definition.id && it.id != existing?.id }
            ?.values.orEmpty()
        val reconciliation = ReconciliationSession(
            id = existing?.id ?: dependencies.ids.newId(),
            observationSessionId = session.id,
            votingDayId = session.currentVotingDay,
            definitionId = definition.id,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            values = values,
            results = dependencies.reconciliationEngine.evaluate(definition, values, previous),
            photoMediaId = photoMediaId,
        )
        dependencies.reconciliationRepository.save(reconciliation)
        existing?.photoMediaId
            ?.takeIf { it != photoMediaId }
            ?.let { dependencies.mediaRepository.delete(it) }
        onSaved(reconciliation)
    }

    fun saveProtocol(
        definition: ReconciliationDefinition,
        values: Map<String, String>,
        comments: String,
        photoMediaId: String?,
        onSaved: (ProtocolSnapshot) -> Unit = {},
    ) = task {
        val session = requireNotNull(state.value.activeSession)
        val snapshot = ProtocolSnapshot(
                id = dependencies.ids.newId(),
                observationSessionId = session.id,
                votingDayId = session.currentVotingDay,
                protocolFormId = definition.id,
                capturedAt = dependencies.clock.now(),
                values = values,
                photoMediaId = photoMediaId,
                reconciliationSessionIds = state.value.reconciliationSessions
                    .filter { it.definitionId == definition.id }
                    .map { it.id },
                comments = comments,
            )
        dependencies.protocolRepository.save(snapshot)
        onSaved(snapshot)
    }

    fun importMedia(uri: String, source: MediaSource, onImported: (MediaAsset) -> Unit) = task {
        val asset = dependencies.mediaRepository.import(MediaImportRequest(uri, source))
        onImported(asset)
    }

    fun loadMediaPreview(mediaId: String) {
        val current = state.value
        if (mediaId in current.mediaPreviews || mediaId in current.mediaPreviewErrors ||
            !loadingMediaPreviewIds.add(mediaId)
        ) {
            return
        }
        viewModelScope.launch {
            try {
                val bytes = dependencies.mediaRepository.loadImagePreview(mediaId, maxDimension = 900)
                mutableState.update { state ->
                    if (bytes == null) {
                        state.copy(mediaPreviewErrors = state.mediaPreviewErrors + mediaId)
                    } else {
                        state.copy(mediaPreviews = state.mediaPreviews + (mediaId to bytes))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { state ->
                    state.copy(mediaPreviewErrors = state.mediaPreviewErrors + mediaId)
                }
            } finally {
                loadingMediaPreviewIds.remove(mediaId)
            }
        }
    }

    fun deleteMedia(mediaId: String) = task { dependencies.mediaRepository.delete(mediaId) }

    fun search(query: String) = task {
        val packId = requireNotNull(state.value.manifest).id
        val results = if (query.isBlank()) emptyList() else dependencies.knowledgeRepository.search(packId, query)
        mutableState.update { it.copy(searchResults = results) }
    }

    fun clearError() = mutableState.update { it.copy(errorMessage = null) }
    private fun setError(message: String) = mutableState.update { it.copy(errorMessage = message) }
    private fun task(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { setError(it.message ?: "Неизвестная ошибка") }
    }

    class Factory(private val dependencies: ObserverDependencies) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ObserverViewModel(dependencies) as T
    }
}

private fun ChecklistStatus.labelForJournal(): String = when (this) {
    ChecklistStatus.NOT_CHECKED -> "не выполнено"
    ChecklistStatus.OK -> "всё нормально"
    ChecklistStatus.PROBLEM -> "проблема"
    ChecklistStatus.NOT_APPLICABLE -> "не применимо"
}
